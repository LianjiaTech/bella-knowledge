package com.ke.bella.files.protocol;

import java.util.List;

import lombok.Data;

public class UploadOps {
    @Data
    public static class CreateUploadOp {
        private String filename;
        private String purpose;
        private Long bytes;
        private String mimeType;
        private String spaceCode;
        private String ancestorId;
        private String metadata;
        private String description;
        private List<String> cities;
        private List<String> tags;
    }

    @Data
    public static class CompleteUploadOp {
        private List<String> partIds;
    }
}
