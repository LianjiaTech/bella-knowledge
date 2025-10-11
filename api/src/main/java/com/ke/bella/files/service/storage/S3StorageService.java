package com.ke.bella.files.service.storage;

import com.google.common.base.Throwables;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.storage.config.S3StorageConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;

@Slf4j
public class S3StorageService implements StorageService {
    protected final S3Client s3Client;
    protected final S3TransferManager transferManager;
    protected final S3Presigner presigner;
    protected final StaticCredentialsProvider credentialsProvider;

    public S3StorageService(S3StorageConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAk(), config.getSk());
        this.credentialsProvider = StaticCredentialsProvider.create(credentials);

        this.s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(config.getRegion()))
                .endpointOverride(URI.create(config.getEndpoint()))
                .forcePathStyle(true)
                .build();

        S3AsyncClient s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(config.getRegion()))
                .endpointOverride(URI.create(config.getEndpoint()))
                .forcePathStyle(true)
                .build();

        this.transferManager = S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();

        this.presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(config.getRegion()))
                .endpointOverride(URI.create(config.getEndpoint()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Override
    public String putObject(String bucketName, String fileKey, String mimeType, File file, String filename, String charset) {
        try {
            String contentType;
            if(StringUtils.isNotEmpty(mimeType)) {
                contentType = mimeType + (StringUtils.isNotEmpty(charset) ? "; charset=" + charset : "");
            } else {
                contentType = null;
            }

            String filenameEncoded = UriUtils.encodeFragment(filename, StandardCharsets.UTF_8);
            String disposition = getContentDispositionType(mimeType);
            String contentDisposition = disposition + "; filename=" + filenameEncoded;
            long size = Files.size(file.toPath());

            UploadFileRequest uploadFileRequest = UploadFileRequest.builder()
                    .putObjectRequest(req -> req
                            .bucket(bucketName)
                            .key(fileKey)
                            .contentType(contentType)
                            .contentDisposition(contentDisposition)
                            .contentLength(size))
                    .source(file.toPath())
                    .build();

            FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);
            CompletedFileUpload completedUpload = fileUpload.completionFuture().join();

            LOGGER.info("Successfully uploaded file to S3, bucket: {}, key: {}, eTag: {}",
                    bucketName, fileKey, completedUpload.response().eTag());

        } catch (Exception e) {
            String errMsg = String.format("failed to upload file to s3, bucketName: %s, fileKey: %s, mimeType: %s, filename: %s, e: %s", bucketName,
                    fileKey, mimeType, filename, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
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
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (Exception e) {
            String errMsg = String.format("failed to generate presigned url, bucketName: %s, fileKey: %s, e: %s",
                    bucketName, fileKey, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
    }

    @Override
    public String getPublicUrl(String bucketName, String fileKey) {
        try {
            GetUrlRequest request = GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            URL url = s3Client.utilities().getUrl(request);
            return url.toString();
        } catch (Exception e) {
            String errMsg = String.format("failed to get public url, bucketName: %s, fileKey: %s, e: %s",
                    bucketName, fileKey, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
    }

    @Override
    public String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds) {
        throw new NotImplementedException("当前对象存储服务不支持预览功能");
    }

    @Override
    public FileService.InputStreamWithCharset getObjectInputStream(String bucketName, String fileKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();

            software.amazon.awssdk.core.ResponseInputStream<GetObjectResponse> responseInputStream =
                    s3Client.getObject(getObjectRequest);

            GetObjectResponse response = responseInputStream.response();
            String contentType = response.contentType();

            MediaType parse = MediaType.parse(contentType);
            String charset = null;
            if(parse != null) {
                charset = Optional.ofNullable(parse.charset()).map(Charset::name).orElse(null);
            }

            return new FileService.InputStreamWithCharset(responseInputStream, charset);
        } catch (Exception e) {
            String errMsg = String.format("failed to get object input stream, bucketName: %s, fileKey: %s, e: %s",
                    bucketName, fileKey, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalArgumentException("object not found, file_key : " + fileKey, e);
        }
    }
}
