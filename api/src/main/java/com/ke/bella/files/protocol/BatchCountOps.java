package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class BatchCountOps {
    @JsonProperty("ancestor_ids")
    private List<String> ancestorIds;

    private String type;

    @JsonProperty("space_code")
    private String spaceCode;
}
