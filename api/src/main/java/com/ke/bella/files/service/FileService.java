package com.ke.bella.files.service;

import static com.ke.bella.files.db.IDGenerator.FILEID_GEN;

import java.io.File;

import javax.annotation.Resource;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.ke.bella.files.BellaContext;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.protocol.EventType;
import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.UpdateProgressRequestData;

@Component
public class FileService {
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

    public OpenAIFile upload(
            File file,
            String filename,
            String purpose,
            String metadata,
            String extension) {
        String spaceCode = BellaContext.getOperator().getSpaceCode();
        String fileId = FILEID_GEN.generate();
        String bucketName = purpose == "version" ? bucketConfig.getVisionBucket() : bucketConfig.getGeneralBucket();
        String keyName = String.format("%s/%s/%s", spaceCode, purpose, fileId);
        if(StringUtils.isNotEmpty(extension)) {
            keyName += "." + extension;
        }
        amazonS3Service.putObject(bucketName, keyName, file);

        // 保存文件信息到数据库
        FileDB fileDB = new FileDB();
        fileDB.setFileId(fileId);
        fileDB.setFilename(filename);
        fileDB.setBucket(bucketName);
        fileDB.setPath(keyName);
        fileDB.setBytes(file.length());
        fileDB.setSpaceCode(spaceCode);
        fileDB.setPurpose(purpose);
        fileDB.setMetaData(metadata);
        fileDB.setAkCode(BellaContext.getApikey().getCode());
        fileRepo.addFile(fileDB);
        OpenAIFile openAIFile = fileRepo.queryOpenAIFile(fileId);

        // Kafka发送消息
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setEvent(EventType.FILE_CREATED);
        message.setData(openAIFile);
        message.setMetadata(metadata);
        kafkaTemplate.send(topic, message).addCallback(success -> {
            fileRepo.updateFileStatus(fileId, 1L);
        }, failure -> {
            fileRepo.updateFileStatus(fileId, 0L);
        });
        return openAIFile;
    }

    public OpenapiListResponse<OpenAIFile> list(String purpose, Integer limit, String order, String after) {
        return null;
    }

    public OpenAIFile get(String fileId) {
        return null;
    }

    public OpenAIFile delete(String fileId) {
        return null;
    }

    public FileUrl getUrl(
            String fileId,
            Long expires) {
        FileDB file = fileRepo.queryFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        String bucketName = file.getBucket();
        String keyName = file.getPath();
        String url = amazonS3Service.signUrl(bucketName, keyName, expires);
        return FileUrl.builder()
                .s3Url(url)
                .build();
    }

    public FileUrl getUrl(String fileId) {
        return getUrl(fileId, 86400L);
    }

    public Progress updateProgress(UpdateProgressRequestData data, String fileId, String progressName) {
        return null;
    }

    public Progress getProgress(String fileId, String progressName) {
        return null;
    }
}
