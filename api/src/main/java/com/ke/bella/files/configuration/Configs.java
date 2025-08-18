package com.ke.bella.files.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Configs {

    public static String SERVICE_NAME = "file-api";
    public static String MAX_FILE_SIZE;
    public static String OPEN_API_BASE;

    public static Integer TASK_THREAD_NUMS;
    public static Integer DATASETS_IMPORT_BATCH_SIZE;
    public static Boolean FILE_EXISTS_BLOCK;

    @Value("${spring.servlet.multipart.max-file-size}")
    public void setMaxSizeInMB(String maxFileSize) {
        MAX_FILE_SIZE = maxFileSize;
    }

    @Value("${bella.open-api-base}")
    public void setOpenApiBase(String openApiBase) {
        OPEN_API_BASE = openApiBase;
    }

    @Value("${bella.task.thread-nums}")
    public void setTaskThreadNums(Integer taskThreadNums) {
        TASK_THREAD_NUMS = taskThreadNums;
    }

    @Value("${bella.datasets.import.batch-size}")
    public void setDatasetsImportBatchSize(Integer batchSize) {
        DATASETS_IMPORT_BATCH_SIZE = batchSize;
    }

    @Value("${bella.file-api.file.exists.block}")
    public void setFileExistsCheckEnabled(Boolean enabled) {
        FILE_EXISTS_BLOCK = enabled;
    }
}
