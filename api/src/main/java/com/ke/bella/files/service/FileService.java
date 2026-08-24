package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILE_ID_GENERATOR;

import java.io.File;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.FileShardingCountUpdator;
import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.enums.FilePurpose;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.BroadcastStatus;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileNodeCount;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.FilePurposeClassifier;
import com.ke.bella.files.utils.JsonUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FileService {
    public static final Long ONE_DAY = 24 * 60 * 60L;
    public static final String ONE_DAY_STRING = (24 * 60 * 60) + "";
    private static final String VISION = "vision";
    @Autowired
    private FileRepo fileRepo;
    @Autowired
    private StorageService storageService;
    @Autowired
    private BucketConfig bucketConfig;
    @Autowired
    private BroadcastService broadcastService;
    @Autowired
    private FileShardingCountUpdator fileShardingCountUpdator;
    @Autowired
    @Lazy
    private FileService self;

    public boolean exists(String spaceCode, String ancestorId, String filename) {
        return fileRepo.exists(spaceCode, ancestorId, filename);
    }

    public String bucketForPurpose(String purpose) {
        return VISION.equals(purpose) ? bucketConfig.getPublicBucket() : bucketConfig.getPrivateBucket();
    }

    /**
     * Internal buckets are excluded even if configured, so an external-bucket
     * import can never bypass the import/ path restriction on our own buckets.
     */
    public boolean isAllowedImportSource(String bucket) {
        return bucketConfig.getImportSourceBuckets().contains(bucket)
                && !StringUtils.equals(bucket, bucketConfig.getPrivateBucket())
                && !StringUtils.equals(bucket, bucketConfig.getPublicBucket());
    }

    public boolean objectExists(String bucket, String path) {
        return storageService.objectExists(bucket, path);
    }

    public long objectSize(String bucket, String path) {
        return storageService.objectSize(bucket, path);
    }

    public OpenAIFile importObject(
            String spaceCode,
            String bucket,
            String path,
            long contentLength,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String ancestorId,
            String description,
            List<String> cities,
            List<String> tags) {
        FileType fileType = FilePurposeClassifier.classify(purpose);
        String fileId = FILE_ID_GENERATOR.generateWithType(fileType, spaceCode);
        FileUploadContext context = self.createFileWithId(spaceCode, fileId, bucket, path, filename, contentLength,
                purpose, metadata, mimeType, type, extension, ancestorId, description, cities, tags);
        return self.finalizeFileUpload(context.getFileDB(), metadata);
    }

    public FileDB queryFile(String spaceCode, String ancestorId, String filename) {
        return fileRepo.queryFile(spaceCode, ancestorId, filename);
    }

    public OpenAIFile getFile(String spaceCode, String ancestorId, String filename) {
        FileDB fileDB = fileRepo.queryFile(spaceCode, ancestorId, filename);
        if(fileDB == null) {
            return null;
        }
        return transferToOpenAIFile(fileDB);
    }

    private void updateBroadcastStatus(String fileId, BroadcastStatus status) {
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setBroadcastStatus(status);
        try {
            fileRepo.updateFile(op);
        } catch (Exception e) {
            String msg = "Failed to update file broadcast status, fileId: "
                    + fileId + ", broadcastStatus: " + status.getValue();
            LOGGER.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    private void broadcast(FileDB fileDB, FileBroadcasting<?> message) {
        FileType fileType = FileType.fromFileId(fileDB.getFileId());
        if((fileType == FileType.SYSTEM && !FilePurpose.DOM_TREE.getValue().equals(fileDB.getPurpose()))
                || (fileType == FileType.TEMP && !FilePurpose.ASSISTANTS_CHAT.getValue().equals(fileDB.getPurpose()))) {
            return;
        }
        broadcastService.broadcast(message,
                () -> updateBroadcastStatus(fileDB.getFileId(), BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(fileDB.getFileId(), BroadcastStatus.FAILED));
    }

    private OpenAIFile transferToOpenAIFile(FileDB fileDB) {
        NodeType nodeType = NodeType.from(fileDB);
        return OpenAIFile.builder()
                .id(fileDB.getFileId())
                .bytes(fileDB.getBytes())
                .createdAt(fileDB.getCtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getCtime()))
                        .toEpochMilli())
                .filename(fileDB.getFilename())
                .isDir(nodeType == NodeType.DIRECTORY)
                .nodeType(nodeType.getValue())
                .resourceId(fileDB.getResourceId())
                .extension(fileDB.getExtension())
                .mimeType(fileDB.getMimeType())
                .type(fileDB.getType())
                .purpose(fileDB.getPurpose())
                .domTreeFileId(fileDB.getDomTreeFileId())
                .pdfFileId(fileDB.getPdfFileId())
                .version(fileDB.getVersion())
                .spaceCode(fileDB.getSpaceCode())
                .metadata(fileDB.getMetaData())
                .cuid(fileDB.getCuid())
                .cuName(fileDB.getCuName())
                .muid(fileDB.getMuid())
                .muName(fileDB.getMuName())
                .mtime(fileDB.getMtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getMtime()))
                        .toEpochMilli())
                .description(fileDB.getDescription())
                .cities(JsonUtils.fromJson(fileDB.getCities(), new TypeReference<List<String>>() {
                }))
                .tags(JsonUtils.fromJson(fileDB.getTags(), new TypeReference<List<String>>() {
                }))
                .build();
    }

    private OpenAIFile buildOpenAIFileWithSource(FileDB fileDB) {
        OpenAIFile openAIFile = transferToOpenAIFile(fileDB);
        String purpose = fileDB.getPurpose();

        FileDB sourceFileDB = null;
        try {
            if("dom_tree".equals(purpose)) {
                sourceFileDB = fileRepo.queryFileByDomTreeFileId(fileDB.getFileId());
            } else if("pdf".equals(purpose)) {
                sourceFileDB = fileRepo.queryFileByPdfFileId(fileDB.getFileId());
            }

            if(sourceFileDB != null) {
                return openAIFile.toBuilder().sourceFile(transferToOpenAIFile(sourceFileDB)).build();
            }
        } catch (Exception e) {
            LOGGER.warn("failed to query source file for {} file: {}, error: {}", purpose, fileDB.getFileId(), e.getMessage());
        }

        return openAIFile;
    }

    private Progress transferToProgress(FileProgressDB fileProgressDB) {
        return Progress.builder()
                .id(fileProgressDB.getId())
                .fileId(fileProgressDB.getFileId())
                .name(fileProgressDB.getName())
                .status(fileProgressDB.getStatus())
                .message(fileProgressDB.getMessage())
                .percent(fileProgressDB.getPercent())
                .cuid(fileProgressDB.getCuid())
                .cuName(fileProgressDB.getCuName())
                .ctime(fileProgressDB.getCtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileProgressDB.getCtime()))
                        .toEpochMilli())
                .muid(fileProgressDB.getMuid())
                .muName(fileProgressDB.getMuName())
                .mtime(fileProgressDB.getMtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileProgressDB.getMtime()))
                        .toEpochMilli())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile uploadWithUrl(File file, String type, String mimeType, String extension, String charset, String purpose, String metadata,
            boolean getUrl, long expires, String ancestorId, String filename, String description, List<String> cities, List<String> tags) {
        OpenAIFile openaiFile = upload(file, filename, purpose, metadata,
                mimeType, type, extension, charset, ancestorId, description, cities, tags);

        if(getUrl) {
            String url = getUrl(openaiFile.getId(), expires);
            openaiFile.setUrl(url);
        }
        return openaiFile;
    }

    @Transactional
    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset) {
        return upload(file, filename, purpose, metadata, mimeType, type, extension, charset, null, null, null, null);
    }

    /**
     * 基于File的上传 - 临时文件方式
     * 优化：将S3上传移出事务，减少数据库连接占用时间
     */
    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset,
            String ancestorId,
            String description,
            List<String> cities,
            List<String> tags) {
        // 步骤1：创建数据库记录（在事务内，快速完成）
        FileUploadContext context = self.createFile(
                filename, file.length(), purpose, metadata,
                mimeType, type, extension, ancestorId, description, cities, tags);

        // 步骤2：上传到S3（在事务外，不占用数据库连接）
        try {
            storageService.putObject(
                    context.getBucketName(),
                    context.getKeyName(),
                    mimeType,
                    file,
                    filename,
                    charset);
        } catch (Exception e) {
            LOGGER.error("S3 upload failed for fileId: {}, error: {}", context.getFileDB().getFileId(), e.getMessage(), e);
            // S3上传失败，删除已创建的数据库记录，保证数据一致性
            try {
                self.delete(context.getFileDB().getFileId());
                LOGGER.info("Cleaned up orphaned DB record after S3 upload failure, fileId: {}", context.getFileDB().getFileId());
            } catch (Exception cleanupError) {
                LOGGER.error("Failed to cleanup DB record after S3 upload failure, fileId: {}, error: {}",
                        context.getFileDB().getFileId(), cleanupError.getMessage(), cleanupError);
            }
            throw new IllegalStateException("Failed to upload file to storage", e);
        }

        // 步骤3：查询并广播
        return self.finalizeFileUpload(context.getFileDB(), metadata);
    }

    public OpenAIFile uploadFromStream(
            InputStream inputStream,
            long contentLength,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset,
            String ancestorId) {
        return uploadFromStream(inputStream, contentLength, filename, purpose, metadata, mimeType, type, extension, charset, ancestorId, null, null,
                null);
    }

    /**
     * 基于InputStream的流式上传 - 避免创建临时文件
     * 优化：将S3上传移出事务，减少数据库连接占用时间
     */
    public OpenAIFile uploadFromStream(
            InputStream inputStream,
            long contentLength,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset,
            String ancestorId,
            String description,
            List<String> cities,
            List<String> tags) {

        // 步骤1：创建数据库记录（在事务内，快速完成）
        FileUploadContext context = self.createFile(
                filename, contentLength, purpose, metadata,
                mimeType, type, extension, ancestorId,
                description, cities, tags);

        // 步骤2：上传到S3（在事务外，不占用数据库连接）
        try {
            storageService.putObjectFromStream(
                    context.getBucketName(),
                    context.getKeyName(),
                    mimeType,
                    inputStream,
                    contentLength,
                    filename,
                    charset);
        } catch (Exception e) {
            LOGGER.error("S3 upload failed for fileId: {}, error: {}", context.getFileDB().getFileId(), e.getMessage(), e);
            // S3上传失败，删除已创建的数据库记录，保证数据一致性
            try {
                self.delete(context.getFileDB().getFileId());
                LOGGER.info("Cleaned up orphaned DB record after S3 upload failure, fileId: {}", context.getFileDB().getFileId());
            } catch (Exception cleanupError) {
                LOGGER.error("Failed to cleanup DB record after S3 upload failure, fileId: {}, error: {}",
                        context.getFileDB().getFileId(), cleanupError.getMessage(), cleanupError);
            }
            throw new IllegalStateException("Failed to upload file to storage", e);
        }

        // 步骤3：查询并广播
        return self.finalizeFileUpload(context.getFileDB(), metadata);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileUploadContext createFile(
            String filename,
            long contentLength,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String ancestorId,
            String description,
            List<String> cities,
            List<String> tags) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        FileType fileType = FilePurposeClassifier.classify(purpose);
        String fileId = FILE_ID_GENERATOR.generateWithType(fileType);
        String bucketName = purpose.equals(VISION) ? bucketConfig.getPublicBucket() : bucketConfig.getPrivateBucket();
        String keyName = String.format("%s/%s", purpose, fileId);
        if(StringUtils.isNotEmpty(extension)) {
            keyName += "." + extension;
        }
        return createFileWithId(spaceCode, fileId, bucketName, keyName, filename, contentLength, purpose, metadata,
                mimeType, type, extension, ancestorId, description, cities, tags);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileUploadContext createFileWithId(
            String spaceCode,
            String fileId,
            String bucketName,
            String keyName,
            String filename,
            long contentLength,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String ancestorId,
            String description,
            List<String> cities,
            List<String> tags) {
        FileType fileType = FilePurposeClassifier.classify(purpose);
        String akCode = BellaContextHelper.getOperatorAkCode();

        String citiesJson = cities == null ? "" : JsonUtils.toJson(cities);
        String tagsJson = tags == null ? "" : JsonUtils.toJson(tags);

        // 保存文件信息到数据库
        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(filename);
        fileDB.setExtension(extension);
        fileDB.setMimeType(mimeType);
        fileDB.setType(type);
        fileDB.setBucket(bucketName);
        fileDB.setPath(keyName);
        fileDB.setBytes(contentLength);
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(purpose);
        fileDB.setMetaData(metadata);
        fileDB.setAkCode(akCode);
        fileDB.setDescription(StringUtils.isNotEmpty(description) ? description : "");
        fileDB.setCities(citiesJson);
        fileDB.setTags(tagsJson);
        fileDB.setNodeType(NodeType.FILE.getValue());
        fileDB.setResourceId("");
        String shardingKey = fileRepo.addFile(fileDB, ancestorId, fileType);
        if(fileType.notUsersType()) {
            fileShardingCountUpdator.increase(shardingKey, fileType.getType());
        }

        // Query complete file record with auto-generated fields (ctime, etc.)
        FileDB completeFileDB = fileRepo.queryFile(fileId, fileType);

        // 返回上传上下文
        return FileUploadContext.builder()
                .fileDB(completeFileDB)
                .bucketName(bucketName)
                .keyName(keyName)
                .fileType(fileType)
                .build();
    }

    /**
     * 完成上传并广播
     */
    @Transactional
    public OpenAIFile finalizeFileUpload(FileDB fileDB, String metadata) {

        OpenAIFile openAIFile = buildOpenAIFileWithSource(fileDB);

        // 文件创建后的广播机制
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(metadata);
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        message.setAkCode(BellaContextHelper.getOperatorAkCode());
        broadcast(fileDB, message);

        return openAIFile;
    }

    /**
     * 上传上下文 - 用于在步骤间传递信息
     */
    @Data
    @Builder
    public static class FileUploadContext {
        private String bucketName;
        private String keyName;
        private FileType fileType;
        private FileDB fileDB;
    }

    public List<OpenAIFile> list(
            String purpose,
            Integer limit,
            String order,
            String after,
            String ancestorId) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        List<FileDB> files = fileRepo.listFile(purpose, limit, order, after, spaceCode, ancestorId);
        List<OpenAIFile> emptyList = new ArrayList<>();
        // don't set source fIle for listing
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }

    public OpenAIFile getFile(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        FileDB fileDB = fileRepo.queryFile(fileId, fileType);
        if(fileDB == null) {
            throw new FileNotFoundException(fileId);
        }

        return transferToOpenAIFile(fileDB);
    }

    public FileDB requireContentFile(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        FileDB file = fileRepo.queryFile(fileId, fileType);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        NodeType nodeType = NodeType.from(file);
        if(nodeType != NodeType.FILE) {
            throw new IllegalArgumentException(String.format("node has no file content. file_id = %s, node_type = %s",
                    fileId, nodeType.getValue()));
        }
        return file;
    }

    public FileDB getFile0(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        return fileRepo.queryFile(fileId, fileType);
    }

    public String updateRealFile(String fileId, String filename, File file, String mimeType, String charset) {
        return updateRealFile(requireContentFile(fileId), filename, file, mimeType, charset);
    }

    public String updateRealFile(FileDB fileDB, String filename, File file, String mimeType, String charset) {
        return storageService.putObject(fileDB.getBucket(), fileDB.getPath(), mimeType, file, filename, charset);
    }

    public String updateRealFileFromStream(String fileId, String filename, java.io.InputStream inputStream, long contentLength, String mimeType,
            String charset) {
        return updateRealFileFromStream(requireContentFile(fileId), filename, inputStream, contentLength, mimeType, charset);
    }

    public String updateRealFileFromStream(FileDB fileDB, String filename, java.io.InputStream inputStream, long contentLength, String mimeType,
            String charset) {
        return storageService.putObjectFromStream(fileDB.getBucket(), fileDB.getPath(), mimeType, inputStream, contentLength, filename, charset);
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile updateFile(FileOps ops, boolean increaseVersion, Scope actionType) {
        FileType fileType = FileType.fromFileId(ops.getFileId());
        fileRepo.updateFile(ops, increaseVersion);
        FileDB fileDB = fileRepo.queryFile(ops.getFileId(), fileType);
        OpenAIFile finalOpenAIFile = buildOpenAIFileWithSource(fileDB);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_UPDATED);
        message.setScope(actionType.getValue());
        message.setData(finalOpenAIFile);
        message.setMetadata(fileDB.getMetaData());
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        message.setAkCode(BellaContextHelper.getOperatorAkCode());
        broadcast(fileDB, message);

        return finalOpenAIFile;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        // 删除前获取文件信息用于广播
        FileDB fileDB = fileRepo.queryFile(fileId, fileType);
        delete(fileDB);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(FileDB fileDB) {
        String fileId = fileDB.getFileId();
        FileType fileType = FileType.fromFileId(fileId);

        OpenAIFile fileToDelete = buildOpenAIFileWithSource(fileDB);

        // 只标记status字段，不删除文件，不删除数据库记录
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setStatus(FileStatus.DELETED);
        try {
            fileRepo.updateFile(op, false);
            if(fileType.needsDirectorySupport()) {
                fileRepo.deleteFileClosure(fileId, fileType);
            }

            // 文件删除后的广播机制
            FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
            message.setEvent(EventType.FILE_DELETED);
            message.setData(fileToDelete);
            message.setMetadata(fileDB.getMetaData());
            message.setUserId(BellaContextHelper.getOperatorUserId());
            message.setUserName(BellaContextHelper.getOperatorUserName());
            message.setAkCode(BellaContextHelper.getOperatorAkCode());
            broadcast(fileDB, message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update file status (indicating deletion), fileId: "
                    + fileId + ", status: " + FileStatus.DELETED, e);
        }
    }

    public String getUrl(String fileId) {
        return getUrl(fileId, ONE_DAY);
    }

    public String getUrl(
            String fileId,
            long expires) {
        return getUrl(requireContentFile(fileId), expires);
    }

    public String getUrl(FileDB file, long expires) {
        return getUrl(file.getBucket(), file.getPath(), file.getPurpose(), expires);
    }

    public String getUrl(String bucketName, String keyName, String purpose, long expires) {
        // Public URLs only work for the public bucket; vision files imported
        // from an external source bucket fall back to a presigned URL.
        return purpose.equals(VISION) && StringUtils.equals(bucketName, bucketConfig.getPublicBucket())
                ? storageService.getPublicUrl(bucketName, keyName)
                : storageService.getPresignedUrl(bucketName, keyName, expires);
    }

    public void updateProgress(
            UpdateProgressRequestData data,
            String fileId,
            String progressName) {
        updateProgress(data, requireContentFile(fileId), progressName);
    }

    public void updateProgress(
            UpdateProgressRequestData data,
            FileDB file,
            String progressName) {
        String fileId = file.getFileId();
        String status = data.getStatus();
        String message = data.getMessage();
        Integer percent = data.getPercent();
        if(fileRepo.queryProgress(fileId, progressName) == null) {
            fileRepo.insertProgress(fileId, progressName, status, message, percent);
        } else {
            fileRepo.updateProgress(fileId, progressName, status, message, percent);
        }
    }

    public Progress getProgress(
            String fileId,
            String progressName) {
        return getProgress(requireContentFile(fileId), progressName);
    }

    public Progress getProgress(
            FileDB file,
            String progressName) {
        FileProgressDB fileProgressDB = fileRepo.queryProgress(file.getFileId(), progressName);
        return fileProgressDB == null ? null : transferToProgress(fileProgressDB);
    }

    /**
     * 支持通过fileIds批量查询，默认所有file在同一个space下
     *
     * @param ops
     *
     * @return
     */
    public List<OpenAIFile> getFiles(ListFileOps ops) {
        List<FileDB> files = fileRepo.getFiles(ops);
        List<OpenAIFile> emptyList = new ArrayList<>();
        // don't set source file for listing
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }

    public String getPreviewUrl(String fileId, Long expires) {
        return getPreviewUrl(requireContentFile(fileId), expires);
    }

    public String getPreviewUrl(FileDB file, Long expires) {
        String bucketName = file.getBucket();
        String keyName = file.getPath();
        return storageService.getPreviewUrl(bucketName, keyName, expires);
    }

    public InputStreamWithCharset getFileInputStream(String fileId) {
        return getFileInputStream(requireContentFile(fileId));
    }

    public InputStreamWithCharset getFileInputStream(FileDB file) {
        String fileId = file.getFileId();
        try {
            String bucketName = file.getBucket();
            String keyName = file.getPath();

            return storageService.getObjectInputStream(bucketName, keyName);
        } catch (Exception e) {
            String errMsg = String.format("failed to get input stream for file: %s, error: %s", fileId, e.getMessage());
            LOGGER.error(errMsg, e);
            throw new IllegalStateException(errMsg, e);
        }
    }

    public OpenAIFile mkdir(String name, String ancestorId, String description, String purpose, String metadata,
            List<String> cities, List<String> tags) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        FileType fileType = FileType.DIRECTORY;
        String fileId = FILE_ID_GENERATOR.generateDirId();
        String akCode = BellaContextHelper.getOperatorAkCode();

        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(name);
        fileDB.setExtension("");
        fileDB.setMimeType("");
        fileDB.setType("");
        fileDB.setBucket("");
        fileDB.setPath("");
        fileDB.setBytes(0L);
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(purpose);
        fileDB.setMetaData(metadata == null ? "{}" : metadata);
        fileDB.setAkCode(akCode);
        fileDB.setIsDir(1);
        fileDB.setNodeType(NodeType.DIRECTORY.getValue());
        fileDB.setResourceId("");
        fileDB.setDescription(description == null ? "" : description);
        fileDB.setCities(cities == null ? "" : JsonUtils.toJson(cities));
        fileDB.setTags(tags == null ? "" : JsonUtils.toJson(tags));

        fileRepo.addFile(fileDB, ancestorId, fileType);

        FileDB res = fileRepo.queryFile(fileId, fileType);
        if(res == null) {
            throw new FileNotFoundException(fileId);
        }

        OpenAIFile openAIFile = transferToOpenAIFile(res);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(res.getMetaData());
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        message.setAkCode(BellaContextHelper.getOperatorAkCode());
        broadcast(res, message);
        return openAIFile;
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile createResource(String name, String resourceId, String ancestorId, String purpose) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        String fileId = FILE_ID_GENERATOR.generateWithType(FileType.USER);

        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(name);
        fileDB.setExtension("");
        fileDB.setMimeType("");
        fileDB.setType("");
        fileDB.setBucket("");
        fileDB.setPath("");
        fileDB.setBytes(0L);
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(StringUtils.defaultString(purpose));
        fileDB.setMetaData("{}");
        fileDB.setAkCode(BellaContextHelper.getOperatorAkCode());
        fileDB.setIsDir(0);
        fileDB.setDescription("");
        fileDB.setCities("");
        fileDB.setTags("");
        fileDB.setNodeType(NodeType.RESOURCE.getValue());
        fileDB.setResourceId(resourceId);

        fileRepo.addFile(fileDB, ancestorId, FileType.USER);
        FileDB created = fileRepo.queryFile(fileId, FileType.USER);
        if(created == null) {
            throw new FileNotFoundException(fileId);
        }
        OpenAIFile resource = transferToOpenAIFile(created);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(resource);
        message.setMetadata("{}");
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        message.setAkCode(BellaContextHelper.getOperatorAkCode());
        broadcast(created, message);
        return resource;
    }

    public List<OpenAIFile> findFiles(FileDB ancestor) {
        List<FileDB> fileDbs = fileRepo.findFiles(ancestor.getSpaceCode(), ancestor.getFileId());
        return fileDbs.stream()
                .map(this::transferToOpenAIFile)
                .collect(Collectors.toList());
    }

    public List<OpenAIFile> findFiles(String spaceCode) {
        List<FileDB> fileDbs = fileRepo.findFiles(spaceCode, null);
        return fileDbs.stream()
                .map(this::transferToOpenAIFile)
                .collect(Collectors.toList());
    }

    public FileNodeCount countNodes(String spaceCode, String ancestorId) {
        return fileRepo.countNodes(spaceCode, ancestorId);
    }

    public OpenAIFile info(String fileId) {
        List<FileDB> pathFiles = fileRepo.getPathFiles(fileId);
        StringBuilder pathBuilder = new StringBuilder();
        for (FileDB file : pathFiles) {
            pathBuilder.append("/");
            pathBuilder.append(file.getFilename());
        }

        OpenAIFile result = transferToOpenAIFile(pathFiles.get(0));

        return result.toBuilder().path(pathBuilder.toString()).build();
    }

    public String getDirectAncestorId(String fileId) {
        return fileRepo.getDirectAncestorId(fileId);
    }

    /**
     * 初始化文件分片管理定时任务
     */
    @PostConstruct
    public void init() {
        // 每5秒刷新文件分片计数到数据库
        TaskExecutor.scheduleAtFixedRate(() -> fileShardingCountUpdator.flush(), 5);
        // 每60秒检查是否需要创建新分片
        TaskExecutor.scheduleAtFixedRate(() -> fileShardingCountUpdator.trySharding(), 60);
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class InputStreamWithCharset {
        private java.io.InputStream inputStream;
        private String charset;
    }

    /**
     * 移动文件/目录，支持同空间与跨空间。目标空间解析：targetSpaceCode 显式传入优先；
     * 缺省时取 targetAncestor 所在空间（移入哪个目录就去哪个空间）；均缺省则为文件当前空间。
     * targetAncestor 为 null 表示移动到目标空间根目录——跨空间移动到根只能通过 targetSpaceCode 表达。
     * 传入对象是调用方锁外读取的快照，最新状态由仓储层事务内加锁重读兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile moveFile(FileDB file, @Nullable String targetSpaceCode, @Nullable FileDB targetAncestor) {
        String fileId = file.getFileId();
        String targetSpace = StringUtils.defaultString(StringUtils.trimToNull(targetSpaceCode),
                targetAncestor != null ? targetAncestor.getSpaceCode() : file.getSpaceCode());
        if(targetAncestor != null && !StringUtils.equals(targetSpace, targetAncestor.getSpaceCode())) {
            throw new IllegalArgumentException("space_code mismatch between target space and ancestor_id");
        }
        if(StringUtils.equals(targetSpace, file.getSpaceCode())) {
            fileRepo.moveFile(file, targetAncestor);
        } else {
            LOGGER.info("cross-space move, fileId: {}, source space: {}, target space: {}",
                    fileId, file.getSpaceCode(), targetSpace);
            fileRepo.moveFileAcrossSpace(file, targetSpace, targetAncestor);
        }

        FileOps ops = FileOps.builder()
                .fileId(fileId)
                .build();

        updateFile(ops, false, Scope.LOCATION);

        return getFile(fileId);
    }

    public Page<OpenAIFile> pageFiles(PageFileOps ops) {
        Page<FileDB> pageResult = fileRepo.pageFiles(ops);
        List<OpenAIFile> openAIFiles = pageResult.getData().stream()
                .map(this::transferToOpenAIFile)
                .collect(Collectors.toList());

        return Page.<OpenAIFile>from(ops.getPage(), ops.getPageSize())
                .total(pageResult.getTotal())
                .list(openAIFiles);
    }

    /**
     * 批量获取文件的祖先ID列表
     *
     * @param spaceCode 空间编码，必传，用于分表计算
     * @param fileIds   文件ID列表
     *
     * @return Map<String, List<String>>
     *         key为fileId，value为从根路径开始的祖先ID数组（根路径文件返回空数组）
     */
    public Map<String, List<String>> getFileAncestorIds(String spaceCode, List<String> fileIds) {
        return fileRepo.getFileAncestorIds(spaceCode, fileIds);
    }
}
