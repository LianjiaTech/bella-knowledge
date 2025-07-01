package com.ke.bella.files.service.storage;

import java.io.File;

import com.ke.bella.files.service.FileService;

public interface StorageService {
    // 上传文件
    String putObject(String bucketName, String fileKey, String mimeType, File file, String filename, String charset);

    // 获取预签名URL
    String getPresignedUrl(String bucketName, String fileKey, long expirationSeconds);

    // 获取公共URL
    String getPublicUrl(String bucketName, String fileKey);

    // 获取预览URL
    String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds);

    // 获取文件输入流
    FileService.InputStreamWithCharset getObjectInputStream(String bucketName, String fileKey);
}
