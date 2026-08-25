package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateCreatorOps {

    private Long cuid;
    @JsonProperty("cu_name")
    private String cuName;
    @JsonProperty("created_at")
    private Long createdAt;
}
