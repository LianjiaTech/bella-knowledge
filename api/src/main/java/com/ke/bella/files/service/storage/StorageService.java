package com.ke.bella.files.service.storage;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import com.ke.bella.files.service.FileService;

public interface StorageService {
    // 上传文件（基于File对象）
    String putObject(String bucketName, String fileKey, String mimeType, File file, String filename, String charset);

    // 流式上传（基于InputStream，避免创建临时文件）
    String putObjectFromStream(String bucketName, String fileKey, String mimeType, InputStream inputStream, long contentLength, String filename, String charset);

    // 获取预签名URL
    String getPresignedUrl(String bucketName, String fileKey, long expirationSeconds);

    // 获取公共URL
    String getPublicUrl(String bucketName, String fileKey);

    // 获取预览URL
    String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds);

    // 获取文件输入流
    FileService.InputStreamWithCharset getObjectInputStream(String bucketName, String fileKey);

    String createMultipartUpload(String bucketName, String fileKey, String mimeType, String filename, String charset);

    String uploadPart(String bucketName, String fileKey, String uploadId, int partNumber, InputStream inputStream, long contentLength);

    List<StoragePart> listParts(String bucketName, String fileKey, String uploadId);

    void completeMultipartUpload(String bucketName, String fileKey, String uploadId, List<StoragePart> parts);

    void abortMultipartUpload(String bucketName, String fileKey, String uploadId);

    boolean objectExists(String bucketName, String fileKey);

    long objectSize(String bucketName, String fileKey);
}
