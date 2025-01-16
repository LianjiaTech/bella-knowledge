package com.ke.bella.files.protocol;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListFileOps {

    @JsonProperty("get_url")
    private boolean getUrl = false;

    private long expires;

    @JsonProperty("file_ids")
    private List<String> fileIds = Collections.emptyList();
}
