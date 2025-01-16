package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILEID_GEN;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.ke.bella.files.BellaContext;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.protocol.BroadcastStatus;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.UpdateProgressRequestData;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FileService {
    public static final Long ONE_DAY = 24 * 60 * 60L;
    public static final String ONE_DAY_STRING = (24 * 60 * 60) + "";
    private static final String VISION = "vision";
    @Autowired
    FileRepo fileRepo;
    @Autowired
    AmazonS3Service amazonS3Service;
    @Autowired
    private BucketConfig bucketConfig;
    @Value("${spring.kafka.producer.topic.broadcast}")
    private String topic;
    @Resource
    private KafkaTemplate<String, Object> kafkaTemplate;

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
                .extension(fileDB.getExtension())
                .mimeType(fileDB.getMimeType())
                .type(fileDB.getType())
                .purpose(fileDB.getPurpose())
                .build();
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

    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String mimeType,
            String type,
            String extension) {
        String spaceCode = BellaContext.getOperator().getSpaceCode();
        String fileId = FILEID_GEN.generate();
        String bucketName = purpose.equals(VISION) ? bucketConfig.getVisionBucket() : bucketConfig.getGeneralBucket();
        String keyName = String.format("%s/%s", purpose, fileId);
        if(StringUtils.isNotEmpty(extension)) {
            keyName += "." + extension;
        }
        amazonS3Service.putObject(bucketName, keyName, file);

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
        fileDB.setAkCode(BellaContext.getApikey().getCode());
        fileRepo.addFile(fileDB);
        FileDB res = fileRepo.queryFile(fileId);
        OpenAIFile openAIFile = res == null ? null : transferToOpenAIFile(res);

        // Kafka发送消息
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(metadata);
        kafkaTemplate.send(topic, message).addCallback(success -> {
            updateBroadcastStatus(fileId, BroadcastStatus.SUCCESS);
        }, failure -> {
            updateBroadcastStatus(fileId, BroadcastStatus.FAILED);
        });
        return openAIFile;
    }

    public List<OpenAIFile> list(
            String purpose,
            Integer limit,
            String order,
            String after) {
        String spaceCode = BellaContext.getOperator().getSpaceCode();
        List<FileDB> files = fileRepo.listFile(purpose, limit, order, after, spaceCode);
        List<OpenAIFile> emptyList = new ArrayList<>();
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }

    public OpenAIFile getFile(String fileId) {
        FileDB fileDB = fileRepo.queryFile(fileId);
        return fileDB == null ? null : transferToOpenAIFile(fileDB);
    }

    public void delete(String fileId) {
        // 只标记status字段，不删除文件，不删除数据库记录
        FileOps op = new FileOps();
        op.setFileId(fileId);
        op.setStatus(FileStatus.DELETED);
        try {
            fileRepo.updateFile(op);
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
        String bucketName = file.getBucket();
        String keyName = file.getPath();
        String purpose = file.getPurpose();
        return purpose.equals(VISION) ? amazonS3Service.getPublicUrl(bucketName, keyName)
                : amazonS3Service.getPresignedUrl(bucketName, keyName, expires);
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
        return files == null ? emptyList : files.stream().map(this::transferToOpenAIFile).collect(Collectors.toList());
    }
}
