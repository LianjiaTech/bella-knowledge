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
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class S3StorageService implements StorageService {
    protected final S3Client s3Client;
    protected final S3AsyncClient s3AsyncClient;
    protected final S3Presigner presigner;
    protected final StaticCredentialsProvider credentialsProvider;
    protected final ExecutorService streamExecutor;

    public S3StorageService(S3StorageConfig config) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(config.getAk(), config.getSk());
        this.credentialsProvider = StaticCredentialsProvider.create(credentials);

        this.streamExecutor = Executors.newFixedThreadPool(
                10,
                r -> {
                    Thread t = new Thread(r);
                    t.setName("s3-stream-upload-" + t.getId());
                    t.setDaemon(true);
                    return t;
                }
        );

        LOGGER.info("Initializing S3StorageService with maxConcurrency={}, tcpKeepAlive={}, connectionMaxIdle={}s, checksumDisabled=true",
                config.getMaxConcurrency(), config.isTcpKeepAlive(), config.getConnectionMaxIdleSeconds());

        // 同步客户端 - 用于下载等操作
        this.s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(config.getRegion()))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .endpointOverride(URI.create(config.getEndpoint()))
                .forcePathStyle(true)
                .build();

        // 使用 CRT 异步 HTTP 客户端实现零拷贝
        // CRT (Common Runtime) 使用C++实现，支持真正的零拷贝和堆外内存
        AwsCrtAsyncHttpClient.Builder httpClientBuilder = AwsCrtAsyncHttpClient.builder()
                .maxConcurrency(config.getMaxConcurrency())
                .connectionMaxIdleTime(Duration.ofSeconds(config.getConnectionMaxIdleSeconds()))
                .connectionTimeout(Duration.ofSeconds(config.getConnectionTimeoutSeconds()));

        // 启用 TCP KeepAlive，保持长连接减少握手开销
        if (config.isTcpKeepAlive()) {
            httpClientBuilder.tcpKeepAliveConfiguration(tcp -> tcp
                    .keepAliveInterval(Duration.ofSeconds(30))  // KeepAlive 探测间隔
                    .keepAliveTimeout(Duration.ofSeconds(5))    // 探测超时时间
            );
        }

        this.s3AsyncClient = S3AsyncClient.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(config.getRegion()))
                .endpointOverride(URI.create(config.getEndpoint()))
                .forcePathStyle(true)
                .httpClient(httpClientBuilder.build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
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

    /**
     * 使用流式上传 + CRT零拷贝上传文件到S3
     *
     * 优势：
     * 1. AsyncRequestBody.fromFile() 使用零拷贝技术，文件数据直接从磁盘到网卡，不经过JVM堆
     * 2. CRT客户端使用堆外内存和native代码，内存占用极低
     * 3. 异步上传，不阻塞线程
     */
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

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType)
                    .contentDisposition(contentDisposition)
                    .contentLength(size)
                    .build();

            // 使用 AsyncRequestBody.fromFile() 实现零拷贝流式上传
            // 这会使用操作系统的 sendfile/splice 系统调用，数据直接从磁盘到网卡
            AsyncRequestBody requestBody = AsyncRequestBody.fromFile(file.toPath());

            // 异步上传并等待完成
            CompletableFuture<PutObjectResponse> uploadFuture = s3AsyncClient.putObject(putObjectRequest, requestBody);
            PutObjectResponse response = uploadFuture.join();

            LOGGER.info("Successfully uploaded file to S3, bucket: {}, key: {}, eTag: {}, size: {}KB",
                    bucketName, fileKey, response.eTag(), size / 1024);

        } catch (Exception e) {
            String errMsg = String.format("failed to upload file to s3, bucketName: %s, fileKey: %s, mimeType: %s, filename: %s, e: %s", bucketName,
                    fileKey, mimeType, filename, Throwables.getStackTraceAsString(e));
            LOGGER.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
        return fileKey;
    }

    /**
     * 基于InputStream的流式上传 - 直接从流上传到S3
     *
     * 优化点：
     * 1. 配合Servlet Part API使用，绕过Spring MVC的完整解析
     * 2. 使用AWS SDK内置的流式上传，内部已优化
     * 3. 无需临时文件，减少磁盘IO
     */
    @Override
    public String putObjectFromStream(String bucketName, String fileKey, String mimeType, InputStream inputStream,
                                       long contentLength, String filename, String charset) {
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

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType)
                    .contentDisposition(contentDisposition)
                    .contentLength(contentLength)
                    .build();

            // 直接使用AWS SDK的流式上传
            AsyncRequestBody requestBody = AsyncRequestBody.fromInputStream(config -> config
                    .inputStream(inputStream)
                    .contentLength(contentLength)
                    .executor(streamExecutor)
            );

            // 异步上传并等待完成
            CompletableFuture<PutObjectResponse> uploadFuture = s3AsyncClient.putObject(putObjectRequest, requestBody);
            PutObjectResponse response = uploadFuture.join();

            LOGGER.info("Successfully uploaded file from stream to S3, bucket: {}, key: {}, eTag: {}, size: {}KB",
                    bucketName, fileKey, response.eTag(), contentLength / 1024);

        } catch (Exception e) {
            String errMsg = String.format("failed to upload file from stream to s3, bucketName: %s, fileKey: %s, mimeType: %s, filename: %s, contentLength: %d, e: %s",
                    bucketName, fileKey, mimeType, filename, contentLength, Throwables.getStackTraceAsString(e));
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

    /**
     * 清理资源：关闭线程池和S3客户端
     */
    @PreDestroy
    public void destroy() {
        LOGGER.info("Shutting down S3StorageService...");

        // 关闭线程池
        if (streamExecutor != null && !streamExecutor.isShutdown()) {
            streamExecutor.shutdown();
            try {
                if (!streamExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    streamExecutor.shutdownNow();
                    LOGGER.warn("Stream executor did not terminate gracefully, forced shutdown");
                }
            } catch (InterruptedException e) {
                streamExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 关闭S3客户端
        try {
            if (s3AsyncClient != null) {
                s3AsyncClient.close();
            }
            if (s3Client != null) {
                s3Client.close();
            }
            if (presigner != null) {
                presigner.close();
            }
            LOGGER.info("S3StorageService shutdown completed");
        } catch (Exception e) {
            LOGGER.error("Error closing S3 clients", e);
        }
    }
}
