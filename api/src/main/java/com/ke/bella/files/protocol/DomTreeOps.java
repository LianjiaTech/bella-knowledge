package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class DomTreeOps {
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DomTreeUploadOp {
        private String fileId;
        private Object domTree;
    }
}
