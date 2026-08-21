package com.ke.bella.files.protocol;

import java.util.List;

import lombok.Data;

public class ImportOps {
    @Data
    public static class ImportObjectOp {
        private String path;
        private String filename;
        private String bucket;
        private String purpose;
        private Long bytes;
        private String mimeType;
        private String metadata;
        private String spaceCode;
        private String ancestorId;
        private String description;
        private List<String> cities;
        private List<String> tags;
    }
}
