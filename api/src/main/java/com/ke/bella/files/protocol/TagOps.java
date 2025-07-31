package com.ke.bella.files.protocol;

import com.ke.bella.openapi.Operator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

public class TagOps {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class TagOp extends Operator {
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class TagPage {
        @Builder.Default
        private int page = 1;
        @Builder.Default
        private int pageSize = 30;
        @Builder.Default
        private String order = "desc";
        @Builder.Default
        private String orderBy = "ctime";
        private String name;
    }
}
