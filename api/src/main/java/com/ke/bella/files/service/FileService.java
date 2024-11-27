package com.ke.bella.files.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;

import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.UpdateProgressRequestData;

@Component
public class FileService {
    public OpenAIFile upload(File file, String fileName, String purpose, String metaData, String extension) throws IOException {
        return null;
    }

    public OpenapiListResponse<OpenAIFile> list(String purpose, Integer limit, String order, String after) {
        return null;
    }

    public OpenAIFile get(String fileId) {
        return null;
    }

    public OpenAIFile delete(String fileId) {
        return null;
    }

    public InputStream retrieveContent(String fileId) {
        return null;
    }

    public FileUrl getUrl(String fileId, Long expires) {
        return null;
    }

    public Progress updateProgress(UpdateProgressRequestData data, String fileId, String progressName) {
        return null;
    }

    public Progress getProgress(String fileId, String progressName) {
        return null;
    }
}
