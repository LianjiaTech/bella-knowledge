package com.ke.bella.files.service.storage;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.base.Throwables;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.storage.config.S3StorageConfig;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;

@Slf4j
public class S3StorageService implements StorageService {
    protected final AmazonS3 client;
    protected final AWSStaticCredentialsProvider provider;

    private static final Integer S3_MAX_FILE_SIZE_BYTES = 512 * 1024 * 1024;

    public S3StorageService(S3StorageConfig config) {
        BasicAWSCredentials credentials = new BasicAWSCredentials(config.getAk(), config.getSk());
        this.provider = new AWSStaticCredentialsProvider(credentials);
        this.client = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new EndpointConfiguration(config.getEndpoint(), config.getRegion()))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    @Override
    public String putObject(String bucketName, String fileKey, String mimeType, File file, String filename, String charset) {
        InputStream inputStream = null;
        try {

            String contentType = null;
            if(StringUtils.isNotEmpty(mimeType)) {
                contentType = mimeType + (StringUtils.isNotEmpty(charset) ? "; charset=" + charset : "");
            }

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(Files.size(file.toPath()));

            String filenameEncoded = UriUtils.encodeFragment(filename, StandardCharsets.UTF_8);
            String disposition = getContentDispositionType(mimeType);
            metadata.setContentDisposition(disposition + "; filename=" + filenameEncoded);

            inputStream = Files.newInputStream(file.toPath());
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileKey, inputStream, metadata);
            putObjectRequest.getRequestClientOptions().setReadLimit(S3_MAX_FILE_SIZE_BYTES);

            client.putObject(putObjectRequest);
        } catch (Exception e) {
            String errMsg = String.format("failed to upload file to s3, bucketName: %s, fileKey: %s, mimeType: %s, filename: %s, e: %s", bucketName,
                    fileKey, mimeType, filename, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
        } finally {
            if(inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    LOGGER.error("failed to close inputStream", e);
                }
            }
        }
        return fileKey;
    }

    private String getContentDispositionType(String mimeType) {
        if(StringUtils.isEmpty(mimeType)) {
            return "attachment";
        }

        String lowerMimeType = mimeType.toLowerCase();

        // 可以在浏览器中直接显示的文件类型使用 inline
        if(lowerMimeType.startsWith("image/")           // 图片
                || lowerMimeType.startsWith("text/")            // 文本文件
                || lowerMimeType.equals("application/pdf")      // PDF
                || lowerMimeType.startsWith("video/")           // 视频
                || lowerMimeType.startsWith("audio/")           // 音频
                || lowerMimeType.equals("application/json")     // JSON
                || lowerMimeType.equals("application/xml")) {
            return "inline";
        }

        return "attachment";
    }

    @Override
    public String getPresignedUrl(String bucketName, String fileKey, long expirationSeconds) {
        Date expirationDate = Date.from(LocalDateTime.now().plusSeconds(expirationSeconds).atZone(ZoneId.systemDefault()).toInstant());
        URL singedUrl = client.generatePresignedUrl(bucketName, fileKey, expirationDate);
        return singedUrl.toString();
    }

    @Override
    public String getPublicUrl(String bucketName, String fileKey) {
        URL unsignUrl = client.getUrl(bucketName, fileKey);
        return unsignUrl.toString();
    }

    @Override
    public String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds) {
        throw new NotImplementedException("当前对象存储服务不支持预览功能");
    }

    @Override
    public FileService.InputStreamWithCharset getObjectInputStream(String bucketName, String fileKey) {
        com.amazonaws.services.s3.model.S3Object s3Object = client.getObject(bucketName, fileKey);
        if(s3Object != null) {
            MediaType parse = MediaType.parse(s3Object.getObjectMetadata().getContentType());
            String charset = null;
            if(parse != null) {
                charset = Optional.ofNullable(parse.charset()).map(Charset::name).orElse(null);
            }
            return new FileService.InputStreamWithCharset(s3Object.getObjectContent(), charset);
        }
        throw new IllegalArgumentException("object not found, file_key : " + fileKey);
    }
}
