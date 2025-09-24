package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FileSystemOps {

    @Getter
    @AllArgsConstructor
    public enum ItemType {
        file,
        folder;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MkdirOp {
        private String ancestorId;
        private String name;
        private String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FindOp {
        private String name;
        private List<String> types;
        private String extension;
        @Builder.Default
        private boolean recursive = false;
        @JsonProperty("ancestor_id")
        @JsonAlias("ancestor_id")
        private String ancestorId;
    }
}
