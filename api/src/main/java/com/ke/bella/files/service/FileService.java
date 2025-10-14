package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILE_ID_GENERATOR;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.FileShardingCountUpdator;
import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.BroadcastStatus;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
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

    public boolean exists(String spaceCode, String ancestorId, String filename) {
        return fileRepo.exists(spaceCode, ancestorId, filename);
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

    private OpenAIFile transferToOpenAIFile(FileDB fileDB) {
        return OpenAIFile.builder()
                .id(fileDB.getFileId())
                .bytes(fileDB.getBytes())
                .createdAt(fileDB.getCtime()
                        .toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getCtime()))
                        .toEpochMilli())
                .filename(fileDB.getFilename())
                .isDir(fileDB.getIsDir() == 1)
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

    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset) {
        return upload(file, filename, purpose, metadata, mimeType, type, extension, charset, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension,
            String charset,
            String ancestorId) {
        return upload(file, filename, purpose, metadata, mimeType, type, extension, charset, ancestorId, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
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
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        FileType fileType = FilePurposeClassifier.classify(purpose);
        String fileId = FILE_ID_GENERATOR.generateWithType(fileType);
        String bucketName = purpose.equals(VISION) ? bucketConfig.getPublicBucket() : bucketConfig.getPrivateBucket();
        String keyName = String.format("%s/%s", purpose, fileId);
        if(StringUtils.isNotEmpty(extension)) {
            keyName += "." + extension;
        }
        storageService.putObject(bucketName, keyName, mimeType, file, filename, charset);

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
        fileDB.setBytes(file.length());
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(purpose);
        fileDB.setMetaData(metadata);
        fileDB.setAkCode(akCode);
        fileDB.setDescription(StringUtils.isNotEmpty(description) ? description : "");
        fileDB.setCities(citiesJson);
        fileDB.setTags(tagsJson);
        String shardingKey = fileRepo.addFile(fileDB, ancestorId, fileType);
        if(fileType.notUsersType()) {
            fileShardingCountUpdator.increase(shardingKey, fileType.getType());
        }

        FileDB res = fileRepo.queryFile(fileId, fileType);
        if(res == null) {
            throw new FileNotFoundException(fileId);
        }

        OpenAIFile openAIFile = buildOpenAIFileWithSource(res);

        // 文件创建后的广播机制
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(metadata);
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        broadcastService.broadcast(message, () -> updateBroadcastStatus(fileId, BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(fileId, BroadcastStatus.FAILED));
        return openAIFile;
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

    public FileDB getFile0(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        return fileRepo.queryFile(fileId, fileType);
    }

    public String updateRealFile(String fileId, String filename, File file, String mimeType, String charset) {
        FileType fileType = FileType.fromFileId(fileId);
        FileDB fileDB = fileRepo.queryFile(fileId, fileType);
        return storageService.putObject(fileDB.getBucket(), fileDB.getPath(), mimeType, file, filename, charset);
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
        broadcastService.broadcast(message, () -> updateBroadcastStatus(finalOpenAIFile.getId(), BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(finalOpenAIFile.getId(), BroadcastStatus.FAILED));

        return finalOpenAIFile;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        // 只标记status字段，不删除文件，不删除数据库记录
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setStatus(FileStatus.DELETED);
        try {
            fileRepo.updateFile(op, false);
            if(fileType.needsDirectorySupport()) {
                fileRepo.deleteFileClosure(fileId, fileType);
            }
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
        FileDB file = fileRepo.queryFile(fileId);
        return getUrl(file.getBucket(), file.getPath(), file.getPurpose(), expires);
    }

    public String getUrl(String bucketName, String keyName, String purpose, long expires) {
        return purpose.equals(VISION) ? storageService.getPublicUrl(bucketName, keyName)
                : storageService.getPresignedUrl(bucketName, keyName, expires);
    }

    public void updateProgress(
            UpdateProgressRequestData data,
            String fileId,
            String progressName) {
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
        FileProgressDB fileProgressDB = fileRepo.queryProgress(fileId, progressName);
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
        FileDB file = fileRepo.queryFile(fileId);
        String bucketName = file.getBucket();
        String keyName = file.getPath();
        return storageService.getPreviewUrl(bucketName, keyName, expires);
    }

    public InputStreamWithCharset getFileInputStream(String fileId) {
        try {
            // 获取文件信息
            FileDB file = fileRepo.queryFile(fileId);
            if(file == null) {
                LOGGER.warn("file not found, file_id = {}", fileId);
                return null;
            }

            String bucketName = file.getBucket();
            String keyName = file.getPath();

            return storageService.getObjectInputStream(bucketName, keyName);
        } catch (Exception e) {
            String errMsg = String.format("failed to get input stream for file: %s, error: %s", fileId, e.getMessage());
            LOGGER.error(errMsg, e);
            throw new IllegalStateException(errMsg, e);
        }
    }

    public OpenAIFile mkdir(String name, String ancestorId, String description) {
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
        fileDB.setPurpose(null);
        fileDB.setMetaData("{}");
        fileDB.setAkCode(akCode);
        fileDB.setIsDir(1);
        fileDB.setDescription(description == null ? "" : description);
        fileDB.setCities("");
        fileDB.setTags("");

        fileRepo.addFile(fileDB, ancestorId, fileType);

        FileDB res = fileRepo.queryFile(fileId, fileType);
        if(res == null) {
            throw new FileNotFoundException(fileId);
        }

        OpenAIFile openAIFile = transferToOpenAIFile(res);

        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata("{}");
        message.setUserId(BellaContextHelper.getOperatorUserId());
        message.setUserName(BellaContextHelper.getOperatorUserName());
        broadcastService.broadcast(message, () -> updateBroadcastStatus(fileId, BroadcastStatus.SUCCESS),
                () -> updateBroadcastStatus(fileId, BroadcastStatus.FAILED));
        return openAIFile;
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

    @Transactional(rollbackFor = Exception.class)
    public OpenAIFile moveFile(String fileId, String targetAncestorId) {

        // 执行移动操作
        FileType fileType = FileType.fromFileId(fileId);
        fileRepo.deleteFileClosure(fileId, fileType);
        fileRepo.addFileClosures(fileId, targetAncestorId);

        FileOps ops = FileOps.builder()
                .fileId(fileId)
                .build();

        updateFile(ops, false, Scope.LOCATION);

        return getFile(fileId);
    }
}
