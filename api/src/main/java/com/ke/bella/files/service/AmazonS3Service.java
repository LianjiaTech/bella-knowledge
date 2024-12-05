package com.ke.bella.files.service;

import java.io.File;

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
    private final String endPoint;
    private final AmazonS3 amazonS3;

    public AmazonS3Service(
            @Value("${s3.ak}") String ak,
            @Value("${s3.sk}") String sk,
            @Value("${s3.end_point}") String endPoint) {
        this.endPoint = endPoint;
        BasicAWSCredentials credentials = new BasicAWSCredentials(ak, sk);
        AWSStaticCredentialsProvider provider = new AWSStaticCredentialsProvider(credentials);
        this.amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new EndpointConfiguration(this.endPoint, Regions.CN_NORTH_1.getName()))
                .withPathStyleAccessEnabled(true)
                .build();
    }

    public String putObject(
            String bucketName,
            String fileKey,
            File file) {
        amazonS3.putObject(bucketName, fileKey, file);
        return fileKey;
    }
}
