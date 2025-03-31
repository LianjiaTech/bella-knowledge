package com.ke.bella.files.service;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.base.Throwables;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AmazonS3Service {
    private final String endpointWrite;
    private final String endpointReadPrivate;
    private final String endpointReadPublic;
    private final AmazonS3 amazonS3Write;
    private final AmazonS3 amazonS3ReadPrivate;
    private final AmazonS3 amazonS3ReadPublic;

    private static final Integer S3_MAX_FILE_SIZE_BYTES = 512 * 1024 * 1024;

    public AmazonS3Service(
            @Value("${s3.ak}") String ak,
            @Value("${s3.sk}") String sk,
            @Value("${s3.endpoint_write}") String endpointWrite,
            @Value("${s3.endpoint_read_private}") String endpointReadPrivate,
            @Value("${s3.endpoint_read_public}") String endpointReadPublic) {
        this.endpointWrite = endpointWrite;
        this.endpointReadPrivate = endpointReadPrivate;
        this.endpointReadPublic = endpointReadPublic;
        BasicAWSCredentials credentials = new BasicAWSCredentials(ak, sk);
        AWSStaticCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
        this.amazonS3Write = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new EndpointConfiguration(this.endpointWrite, Regions.CN_NORTH_1.getName()))
                .withPathStyleAccessEnabled(true)
                .build();
        this.amazonS3ReadPrivate = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new EndpointConfiguration(this.endpointReadPrivate, Regions.CN_NORTH_1.getName()))
                .withPathStyleAccessEnabled(true)
                .build();
        this.amazonS3ReadPublic = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new EndpointConfiguration(this.endpointReadPublic, Regions.CN_NORTH_1.getName()))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    public String putObject(
            String bucketName,
            String fileKey,
            String mimeType,
            File file,
            String filename) {
        InputStream inputStream = null;
        try {

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(mimeType);
            metadata.setContentLength(Files.size(file.toPath()));

            String filenameEncoded = UriUtils.encodeFragment(filename, StandardCharsets.UTF_8);
            metadata.setContentDisposition("attachment; filename=" + filenameEncoded);

            inputStream = Files.newInputStream(file.toPath());
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, fileKey, inputStream, metadata);
            putObjectRequest.getRequestClientOptions().setReadLimit(S3_MAX_FILE_SIZE_BYTES);

            amazonS3Write.putObject(putObjectRequest);
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

    public String getPresignedUrl(
            String bucketName,
            String fileKey,
            long expirationSeconds) {
        Date expirationDate = Date.from(LocalDateTime.now().plusSeconds(expirationSeconds).atZone(ZoneId.systemDefault()).toInstant());
        URL singedUrl = amazonS3ReadPrivate.generatePresignedUrl(bucketName, fileKey, expirationDate);
        return singedUrl.toString();
    }

    public String getPublicUrl(String bucketName, String fileKey) {
        URL unsignUrl = amazonS3ReadPublic.getUrl(bucketName, fileKey);
        return unsignUrl.toString();
    }
}
