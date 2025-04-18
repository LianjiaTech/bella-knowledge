package com.ke.bella.files.service.storage.ke;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.service.storage.S3StorageService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Primary
public class KeStorageService extends S3StorageService {
    private static final String PREVIEW_INSTRUCTION = "!m_convert,o_pdf_view";
    private final AmazonS3 amazonS3ReadPrivate;
    private final AmazonS3 amazonS3ReadPublic;
    private final String endpointPreview;
    private final String akPreview;
    private final String skPreview;
    private final String publicBucketName;

    public KeStorageService(KeStorageConfig config, BucketConfig bucketConfig) {
        super(config);
        this.amazonS3ReadPrivate = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpointReadPrivate(), config.getRegion()))
                .withPathStyleAccessEnabled(true)
                .build();
        this.amazonS3ReadPublic = AmazonS3ClientBuilder.standard()
                .withCredentials(provider)
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(config.getEndpointReadPublic(), config.getRegion()))
                .withPathStyleAccessEnabled(true)
                .build();
        this.endpointPreview = config.getEndpointPreview();
        this.akPreview = config.getAkPreview();
        this.skPreview = config.getSkPreview();
        this.publicBucketName = bucketConfig.getPublicBucket();
    }

    @Override
    public String getPresignedUrl(String bucketName, String fileKey, long expirationSeconds) {
        Date expirationDate = Date.from(LocalDateTime.now().plusSeconds(expirationSeconds).atZone(ZoneId.systemDefault()).toInstant());
        URL singedUrl = amazonS3ReadPrivate.generatePresignedUrl(bucketName, fileKey, expirationDate);
        return singedUrl.toString();
    }

    @Override
    public String getPublicUrl(String bucketName, String fileKey) {
        URL unsignUrl = amazonS3ReadPublic.getUrl(bucketName, fileKey);
        return unsignUrl.toString();
    }

    @Override
    public String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds) {
        return publicBucketName.equals(bucketName) ? getPreviewUrlPublic(bucketName, fileKey)
                : getPreviewUrlPrivate(bucketName, fileKey, expirationSeconds);
    }



    private String getPreviewUrlPublic(String bucketName, String keyName) {
        return String.format("%s/%s/%s%s", endpointPreview, bucketName, keyName, PREVIEW_INSTRUCTION);
    }

    private String getPreviewUrlPrivate(String bucketName, String keyName, Long expires) {
        String path = String.format("/%s/%s%s", bucketName, keyName, PREVIEW_INSTRUCTION);
        long timestamp = System.currentTimeMillis() / 1000;

        Map<String, String> parameters = new HashMap<>();

        parameters.put("ak", akPreview);
        parameters.put("exp", String.valueOf(expires));
        parameters.put("path", path);
        parameters.put("ts", String.valueOf(timestamp));

        List<String> paramsKey = new ArrayList<>(parameters.keySet());
        Collections.sort(paramsKey);
        StringBuilder verifyStr = new StringBuilder();
        for (String s : paramsKey) {
            verifyStr.append(s.trim()).append("=").append(parameters.get(s).trim()).append("&");
        }
        verifyStr.append("sk=").append(skPreview);

        String sign = DigestUtils.md5DigestAsHex(verifyStr.toString().getBytes());
        String params = "ak=" + parameters.get("ak") + "&exp=" + parameters.get("exp") + "&ts=" + parameters.get("ts") + "&sign=" + sign;

        return String.format("%s%s?%s", endpointPreview, path, params);
    }
}
