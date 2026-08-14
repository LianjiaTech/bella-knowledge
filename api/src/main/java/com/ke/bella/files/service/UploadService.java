package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILE_ID_GENERATOR;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.repo.FileUploadRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileUploadDB;
import com.ke.bella.files.enums.FilePurpose;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.Upload;
import com.ke.bella.files.protocol.UploadException;
import com.ke.bella.files.protocol.UploadOps.CompleteUploadOp;
import com.ke.bella.files.protocol.UploadOps.CreateUploadOp;
import com.ke.bella.files.protocol.UploadPart;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.files.service.storage.StoragePart;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.FilePurposeClassifier;
import com.ke.bella.files.utils.JsonUtils;
import com.ke.bella.openapi.utils.FileUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UploadService {
    public static final long PART_SIZE_MIN = 5L * 1024 * 1024;
    public static final long PART_SIZE_MAX = 5L * 1024 * 1024 * 1024;
    public static final int MAX_PARTS = 10000;
    private static final long FILE_LOCK_TIMEOUT_MS = 10000L;
    private static final IDGenerator UPLOAD_ID_GENERATOR = new IDGenerator("upload-");

    @Autowired
    private FileUploadRepo fileUploadRepo;
    @Autowired
    private FileService fileService;
    @Autowired
    private StorageService storageService;
    @Autowired
    private BucketConfig bucketConfig;
    @Autowired
    private FileUniquenessLock fileUniquenessLock;
    @Value("${bella.file-api.upload.max-bytes:5368709120}")
    private long maxBytes;
    @Value("${bella.file-api.upload.session-ttl-hours:720}")
    private long sessionTtlHours;

    public Upload create(CreateUploadOp op) {
        Assert.notNull(op, "request body is required");
        Assert.hasText(op.getFilename(), "filename is required");
        Assert.notNull(op.getBytes(), "bytes is required");
        Assert.isTrue(op.getBytes() > 0, "bytes must be greater than 0");
        Assert.isTrue(op.getBytes() <= maxBytes, "bytes exceeds the maximum of " + maxBytes);
        Assert.isTrue(StringUtils.length(op.getDescription()) <= 256, "description cannot exceed 256 characters");
        validateJsonLength(op.getCities(), 512, "cities");
        validateJsonLength(op.getTags(), 512, "tags");

        String spaceCode = StringUtils.defaultIfBlank(op.getSpaceCode(), BellaContextHelper.getOperateSpaceCode());
        String purpose = op.getPurpose();
        if(!FilePurposeClassifier.allowedPurposes().contains(purpose)) {
            LOGGER.info("Invalid purpose '{}', force to '{}'", purpose, FilePurpose.TEMP.getValue());
            purpose = FilePurpose.TEMP.getValue();
        }
        validateAncestorDirectory(spaceCode, op.getAncestorId());
        if(fileService.exists(spaceCode, op.getAncestorId(), op.getFilename())) {
            throw new UploadException(409, "file_already_exists", "file already exists: " + op.getFilename());
        }

        FileType fileType = FilePurposeClassifier.classify(purpose);
        String fileId = FILE_ID_GENERATOR.generateWithType(fileType, spaceCode);
        String extension = StringUtils.defaultString(FileUtils.getFileExtension(op.getFilename()));
        String bucket = FilePurpose.VISION.getValue().equals(purpose) ? bucketConfig.getPublicBucket() : bucketConfig.getPrivateBucket();
        String path = purpose + "/" + fileId + (StringUtils.isEmpty(extension) ? "" : "." + extension);
        String mimeType = StringUtils.defaultString(op.getMimeType());
        String type = StringUtils.isEmpty(mimeType) ? "" : StringUtils.substringBefore(mimeType, "/");
        String storageUploadId = storageService.createMultipartUpload(bucket, path, mimeType, op.getFilename(), "");

        FileUploadDB row = new FileUploadDB();
        row.setUploadId(UPLOAD_ID_GENERATOR.generateWithSpaceCode(spaceCode));
        row.setSpaceCode(spaceCode);
        row.setAkCode(StringUtils.defaultString(BellaContextHelper.getOperatorAkCode()));
        row.setFileId(fileId);
        row.setFilename(op.getFilename());
        row.setExtension(extension);
        row.setPurpose(purpose);
        row.setMimeType(mimeType);
        row.setType(type);
        row.setCharset("");
        row.setBucket(bucket);
        row.setPath(path);
        row.setDeclaredBytes(op.getBytes());
        row.setStorageUploadId(storageUploadId);
        row.setAncestorId(StringUtils.defaultString(op.getAncestorId()));
        row.setMetadata(op.getMetadata());
        row.setDescription(StringUtils.defaultString(op.getDescription()));
        row.setCities(op.getCities() == null ? "" : JsonUtils.toJson(op.getCities()));
        row.setTags(op.getTags() == null ? "" : JsonUtils.toJson(op.getTags()));
        row.setStatus("PENDING");
        row.setExpiresAt(LocalDateTime.now().plusHours(sessionTtlHours));
        try {
            return toUpload(fileUploadRepo.insert(row), null);
        } catch (Exception e) {
            try {
                storageService.abortMultipartUpload(bucket, path, storageUploadId);
            } catch (Exception abortError) {
                LOGGER.warn("Failed to abort multipart upload after session insert failure", abortError);
            }
            throw e;
        }
    }

    public UploadPart uploadPart(String uploadId, int partNumber, InputStream inputStream, long contentLength) {
        FileUploadDB session = loadSession(uploadId);
        requirePending(session);
        if(partNumber < 1 || partNumber > MAX_PARTS) {
            throw badRequest("invalid_part_number", "part_number must be between 1 and " + MAX_PARTS);
        }
        if(contentLength <= 0 || contentLength > PART_SIZE_MAX) {
            throw badRequest("invalid_part_size", "part size must be between 1 and " + PART_SIZE_MAX);
        }
        String etag = normalizeEtag(storageService.uploadPart(session.getBucket(), session.getPath(), session.getStorageUploadId(), partNumber,
                inputStream, contentLength));
        return UploadPart.builder().id(partId(partNumber, etag)).uploadId(uploadId).createdAt(epochSeconds(LocalDateTime.now()))
                .partNumber(partNumber).size(contentLength).etag(etag).build();
    }

    public Upload complete(String uploadId, CompleteUploadOp op) {
        FileUploadDB session = loadSession(uploadId);
        if("COMPLETED".equals(session.getStatus())) {
            return toUpload(session, fileService.getFile(session.getFileId()));
        }
        return fileUniquenessLock.executeWithLock(session.getSpaceCode(), session.getAncestorId(), session.getFilename(), FILE_LOCK_TIMEOUT_MS, () -> {
            FileUploadDB current = loadSession(uploadId);
            if("COMPLETED".equals(current.getStatus())) {
                return toUpload(current, fileService.getFile(current.getFileId()));
            }
            FileDB existingById = getFileIfPresent(current.getFileId());
            if(existingById != null) {
                OpenAIFile file = fileService.finalizeFileUpload(existingById, current.getMetadata());
                fileUploadRepo.bindFileId(uploadId, current.getFileId());
                current.setStatus("COMPLETED");
                return toUpload(current, file);
            }
            if(!"PENDING".equals(current.getStatus()) && !"COMPLETING".equals(current.getStatus())) {
                throw conflict("upload_state_conflict", "upload cannot be completed in state " + current.getStatus());
            }
            if(fileService.exists(current.getSpaceCode(), current.getAncestorId(), current.getFilename())) {
                throw conflict("file_already_exists", "file already exists: " + current.getFilename());
            }
            if(!storageService.objectExists(current.getBucket(), current.getPath())) {
                List<StoragePart> parts = validateParts(current,
                        storageService.listParts(current.getBucket(), current.getPath(), current.getStorageUploadId()), op);
                if("PENDING".equals(current.getStatus()) && !fileUploadRepo.casStatus(uploadId, "PENDING", "COMPLETING")) {
                    throw conflict("upload_completing", "upload state changed while completing");
                }
                try {
                    storageService.completeMultipartUpload(current.getBucket(), current.getPath(), current.getStorageUploadId(), parts);
                } catch (Exception e) {
                    if(!storageService.objectExists(current.getBucket(), current.getPath())) {
                        throw e;
                    }
                    LOGGER.warn("Multipart completion returned an error but target object exists, continuing upload recovery: {}", uploadId, e);
                }
            } else if("PENDING".equals(current.getStatus()) && !fileUploadRepo.casStatus(uploadId, "PENDING", "COMPLETING")) {
                throw conflict("upload_completing", "upload state changed while completing");
            }
            FileService.FileUploadContext fileContext = fileService.createFileWithId(current.getSpaceCode(), current.getFileId(), current.getBucket(),
                    current.getPath(), current.getFilename(), current.getDeclaredBytes(), current.getPurpose(), current.getMetadata(), current.getMimeType(),
                    current.getType(), current.getExtension(), emptyToNull(current.getAncestorId()), current.getDescription(),
                    jsonList(current.getCities()), jsonList(current.getTags()));
            OpenAIFile file = fileService.finalizeFileUpload(fileContext.getFileDB(), current.getMetadata());
            fileUploadRepo.bindFileId(uploadId, current.getFileId());
            current.setStatus("COMPLETED");
            return toUpload(current, file);
        });
    }

    public Upload cancel(String uploadId) {
        FileUploadDB session = loadSession(uploadId);
        if("CANCELLED".equals(session.getStatus())) {
            return toUpload(session, null);
        }
        if("COMPLETED".equals(session.getStatus())) {
            throw badRequest("upload_completed", "completed upload cannot be cancelled");
        }
        if(!fileUploadRepo.casStatus(uploadId, "PENDING", "CANCELLED")) {
            throw conflict("upload_state_conflict", "upload cannot be cancelled in state " + session.getStatus());
        }
        try {
            storageService.abortMultipartUpload(session.getBucket(), session.getPath(), session.getStorageUploadId());
        } catch (Exception e) {
            LOGGER.warn("Failed to abort multipart upload {}, lifecycle rule will clean it up", uploadId, e);
        }
        session.setStatus("CANCELLED");
        return toUpload(session, null);
    }

    public List<UploadPart> listParts(String uploadId) {
        FileUploadDB session = loadSession(uploadId);
        requirePending(session);
        return storageService.listParts(session.getBucket(), session.getPath(), session.getStorageUploadId()).stream()
                .map(part -> {
                    String etag = normalizeEtag(part.getEtag());
                    return UploadPart.builder().id(partId(part.getPartNumber(), etag)).uploadId(uploadId)
                            .partNumber(part.getPartNumber()).size(part.getSize()).etag(etag).build();
                })
                .collect(Collectors.toList());
    }

    private List<StoragePart> validateParts(FileUploadDB session, List<StoragePart> parts, CompleteUploadOp op) {
        List<StoragePart> sorted = new ArrayList<>(parts == null ? Collections.emptyList() : parts);
        sorted.sort(Comparator.comparingInt(StoragePart::getPartNumber));
        long total = 0;
        for (int index = 0; index < sorted.size(); index++) {
            StoragePart part = sorted.get(index);
            int expected = index + 1;
            if(part.getPartNumber() != expected) {
                throw badRequest("invalid_parts", "missing or unexpected part_number " + expected);
            }
            total += part.getSize();
        }
        if(total != session.getDeclaredBytes()) {
            throw badRequest("invalid_parts", "uploaded bytes " + total + " does not match declared bytes " + session.getDeclaredBytes());
        }
        if(op != null && op.getPartIds() != null) {
            Map<Integer, String> expected = new HashMap<>();
            op.getPartIds().forEach(id -> {
                ParsedPartId parsed = parsePartId(id);
                expected.put(parsed.partNumber, parsed.etag);
            });
            for (StoragePart part : sorted) {
                String etag = expected.get(part.getPartNumber());
                if(etag == null || !normalizeEtag(part.getEtag()).equals(etag)) {
                    throw badRequest("invalid_parts", "part_id mismatch for part_number " + part.getPartNumber());
                }
            }
            if(expected.size() != sorted.size()) {
                throw badRequest("invalid_parts", "part_ids count does not match uploaded parts");
            }
        }
        return sorted;
    }

    private FileUploadDB loadSession(String uploadId) {
        FileUploadDB session = fileUploadRepo.queryByUploadId(uploadId, BellaContextHelper.getOperateSpaceCode());
        if(session == null) {
            throw new UploadException(404, "upload_not_found", "upload not found: " + uploadId);
        }
        // 过期仅拦截 PENDING；COMPLETING 必须允许 complete 重试，这是崩溃后唯一的恢复路径（没有清理任务）
        if("PENDING".equals(session.getStatus()) && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw badRequest("upload_expired", "upload has expired: " + uploadId);
        }
        return session;
    }

    private void requirePending(FileUploadDB session) {
        if(!"PENDING".equals(session.getStatus())) {
            throw conflict("upload_state_conflict", "upload is in state " + session.getStatus());
        }
    }

    private void validateAncestorDirectory(String spaceCode, String ancestorId) {
        if(StringUtils.isEmpty(ancestorId)) {
            return;
        }
        FileDB ancestor = fileService.getFile0(ancestorId);
        if(ancestor == null) {
            throw new UploadException(404, "ancestor_not_found", "ancestor not found: " + ancestorId);
        }
        Assert.isTrue(NodeType.from(ancestor) == NodeType.DIRECTORY, "ancestor_id must refer to a directory");
        Assert.isTrue(StringUtils.equals(spaceCode, ancestor.getSpaceCode()), "space_code mismatch between context and ancestor_id");
    }

    private Upload toUpload(FileUploadDB session, OpenAIFile file) {
        return Upload.builder().id(session.getUploadId()).filename(session.getFilename()).purpose(session.getPurpose())
                .bytes(session.getDeclaredBytes()).status(session.getStatus().toLowerCase())
                .createdAt(epochSeconds(session.getCtime())).expiresAt(epochSeconds(session.getExpiresAt())).file(file)
                .partSizeMin(PART_SIZE_MIN).partSizeMax(PART_SIZE_MAX).maxParts(MAX_PARTS).build();
    }

    private long epochSeconds(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private void validateJsonLength(List<String> values, int maxLength, String field) {
        if(values != null) {
            Assert.isTrue(JsonUtils.toJson(values).length() <= maxLength, field + " JSON cannot exceed " + maxLength + " characters");
        }
    }

    private String partId(int partNumber, String etag) {
        return "part_" + partNumber + "_" + etag;
    }

    private ParsedPartId parsePartId(String id) {
        if(StringUtils.isEmpty(id) || !id.startsWith("part_")) {
            throw badRequest("invalid_parts", "invalid part_id: " + id);
        }
        int separator = id.indexOf('_', 5);
        try {
            return new ParsedPartId(Integer.parseInt(id.substring(5, separator)), id.substring(separator + 1));
        } catch (Exception e) {
            throw badRequest("invalid_parts", "invalid part_id: " + id);
        }
    }

    private String normalizeEtag(String etag) {
        return StringUtils.remove(StringUtils.defaultString(etag), '"');
    }

    private String emptyToNull(String value) {
        return StringUtils.isEmpty(value) ? null : value;
    }

    private FileDB getFileIfPresent(String fileId) {
        try {
            return fileService.getFile0(fileId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> jsonList(String value) {
        return StringUtils.isEmpty(value) ? null : JsonUtils.fromJson(value, List.class);
    }

    private UploadException badRequest(String code, String message) {
        return new UploadException(400, code, message);
    }

    private UploadException conflict(String code, String message) {
        return new UploadException(409, code, message);
    }

    private static class ParsedPartId {
        private final int partNumber;
        private final String etag;

        private ParsedPartId(int partNumber, String etag) {
            this.partNumber = partNumber;
            this.etag = etag;
        }
    }
}
