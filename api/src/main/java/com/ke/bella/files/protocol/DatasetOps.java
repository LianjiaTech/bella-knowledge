package com.ke.bella.files.protocol;

import java.util.List;

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
        private String order;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class QAOp {
        private String datasetId;
        private String itemId;
        private String question;
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
        private String order;
    }

    @Data
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class QAReferenceOp {
        private String referenceId;
        private String itemId;
        private String fileId;
        private String path;
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
        private String order;
    }

    @Getter
    @NoArgsConstructor
    public enum DatasetType {
        qa
    }
}
