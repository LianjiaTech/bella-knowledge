package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class FileNodeCount {
    @JsonProperty("file_count")
    private long fileCount;

    @JsonProperty("directory_count")
    private long directoryCount;

    @JsonProperty("resource_count")
    private long resourceCount;
}
