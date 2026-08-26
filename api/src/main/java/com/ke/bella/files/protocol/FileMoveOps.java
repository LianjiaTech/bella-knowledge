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

    /**
     * 目标空间，可选。缺省时取 ancestor_id 所在空间，均缺省则为文件当前空间（同空间移动）；
     * 与 ancestor_id 的空间不一致时拒绝。跨空间移动到目标空间根目录只能通过本字段表达。
     */
    @JsonProperty("target_space_code")
    private String targetSpaceCode;
}
