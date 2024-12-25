package com.ke.bella.files.service;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Component
public class AmazonS3Service {
    private final String endpointWrite;
    private final String endpointReadPrivate;
    private final String endpointReadPublic;
    private final AmazonS3 amazonS3Write;
    private final AmazonS3 amazonS3ReadPrivate;
    private final AmazonS3 amazonS3ReadPublic;

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
            File file) {
        amazonS3Write.putObject(bucketName, fileKey, file);
        return fileKey;
    }

    public String getPresignedUrl(
            String bucketName,
            String fileKey,
            Long expirationSeconds) {
        Date expirationDate = Date.from(LocalDateTime.now().plusSeconds(expirationSeconds).atZone(ZoneId.systemDefault()).toInstant());
        URL singedUrl = amazonS3ReadPrivate.generatePresignedUrl(bucketName, fileKey, expirationDate);
        return singedUrl.toString();
    }

    public String getPublicUrl(String bucketName, String fileKey) {
        URL unsignUrl = amazonS3ReadPublic.getUrl(bucketName, fileKey);
        return unsignUrl.toString();
    }
}
