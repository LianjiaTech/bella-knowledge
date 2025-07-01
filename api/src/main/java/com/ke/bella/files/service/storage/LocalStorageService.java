package com.ke.bella.files.service.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;

import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.storage.config.LocalStorageConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalStorageService implements StorageService {
    private final LocalStorageConfig config;

    public LocalStorageService(LocalStorageConfig config) {
        this.config = config;
        // 确保存储目录存在
        try {
            Files.createDirectories(Paths.get(config.getRootPath()));
        } catch (IOException e) {
            LOGGER.error("Failed to create storage directories", e);
            throw new RuntimeException("Failed to create storage directories", e);
        }
    }

    @Override
    public String putObject(String bucketName, String fileKey, String mimeType, File file, String filename, String charset) {
        try {
            // 构建目标路径：rootPath/bucketName/fileKey
            Path targetDir = Paths.get(config.getRootPath(), bucketName);
            Files.createDirectories(targetDir);

            Path targetPath = targetDir.resolve(fileKey);

            // 复制文件到目标路径
            FileUtils.copyFile(file, targetPath.toFile());

            // 保存文件元数据
            Path metadataPath = targetDir.resolve(fileKey + ".meta");
            String metadata = String.format("filename=%s\nmimeType=%s\ncharset=%s", filename, mimeType, charset);
            Files.write(metadataPath, metadata.getBytes(StandardCharsets.UTF_8));

            return fileKey;
        } catch (IOException e) {
            LOGGER.error("Failed to store file locally: {}", fileKey, e);
            throw new RuntimeException("Failed to store file locally", e);
        }
    }

    // 本地存储不支持url访问
    @Override
    public String getPresignedUrl(String bucketName, String fileKey, long expirationSeconds) {
        return "http://example.com/" + bucketName + "/" + fileKey;
    }

    @Override
    public String getPublicUrl(String bucketName, String fileKey) {
        return "http://example.com/" + bucketName + "/" + fileKey;
    }

    @Override
    public String getPreviewUrl(String bucketName, String fileKey, long expirationSeconds) {
        return "http://example.com/" + bucketName + "/" + fileKey;
    }

    @Override
    public FileService.InputStreamWithCharset getObjectInputStream(String bucketName, String fileKey) {
        try {
            Path filePath = Paths.get(config.getRootPath(), bucketName, fileKey);

            if(!Files.exists(filePath)) {
                LOGGER.warn("file not found: {}/{}", bucketName, fileKey);
                throw new IllegalArgumentException("object not found, file_key : " + fileKey);
            }

            Path metadataPath = filePath.resolveSibling(fileKey + ".meta");

            String charset = null;
            if(Files.exists(metadataPath)) {
                String metadata = new String(Files.readAllBytes(metadataPath), StandardCharsets.UTF_8);
                for (String line : metadata.split("\n")) {
                    if(line.startsWith("charset=")) {
                        charset = line.substring("charset=".length()).trim();
                    }
                }
            }

            return new FileService.InputStreamWithCharset(Files.newInputStream(filePath), charset);
        } catch (IOException e) {
            LOGGER.error("failed to get input stream for file: {}/{}", bucketName, fileKey, e);
            throw new RuntimeException("failed to get input stream for file: " + fileKey, e);
        }
    }

}
