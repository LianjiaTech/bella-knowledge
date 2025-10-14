package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileMoveOps {

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("ancestor_id")
    private String ancestorId;
}
