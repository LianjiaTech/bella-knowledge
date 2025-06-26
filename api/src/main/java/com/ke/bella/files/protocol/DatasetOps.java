package com.ke.bella.files.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.Operator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

public class DatasetOps {

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class DatasetOp extends Operator {
        private String datasetId;
        private String name;
        private String type;
        private String remark;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class DatasetPage extends DatasetOp {
        @Builder.Default
        private int page = 1;
        @Builder.Default
        private int pageSize = 30;
        @Builder.Default
        private String order = "desc";
        @Builder.Default
        private String orderBy = "ctime";
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class QAOp {
        private String datasetId;
        private String itemId;
        private String question;
        private String similarQ1;
        private String similarQ2;
        private String similarQ3;
        private String answer;
        private List<QAReferenceOp> references;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class QaPage extends QAOp {
        @Builder.Default
        private int page = 1;
        @Builder.Default
        private int pageSize = 30;
        @Builder.Default
        private String order = "desc";
        @Builder.Default
        private String orderBy = "ctime";
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class QAReferenceOp {
        @JsonProperty("reference_id")
        private String referenceId;
        @JsonProperty("item_id")
        private String itemId;
        @JsonProperty("file_id")
        private String fileId;
        @JsonProperty("path")
        private String path;
        @JsonProperty("dataset_id")
        private String datasetId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder
    public static class QaReferencePage extends QAReferenceOp {
        @Builder.Default
        private int page = 1;
        @Builder.Default
        private int pageSize = 30;
        @Builder.Default
        private String order = "desc";
        @Builder.Default
        private String orderBy = "ctime";
    }

    @Getter
    @NoArgsConstructor
    public enum DatasetType {
        qa
    }

    @Getter
    @AllArgsConstructor
    public enum DatasetImportingProgress {
        preprocessing("preprocessing"),

        processing("processing"),

        completed("completed"),

        failed("failed");

        /**
         * Status description
         */
        private final String description;
    }
}
