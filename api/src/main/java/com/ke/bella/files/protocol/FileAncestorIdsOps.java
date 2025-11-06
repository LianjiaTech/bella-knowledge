package com.ke.bella.files.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量获取文件祖先ID列表请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileAncestorIdsOps {

    /**
     * 空间编码，必传，用于分表计算
     */
    @JsonProperty("space_code")
    private String spaceCode;

    /**
     * 文件ID列表
     */
    @JsonProperty("file_ids")
    private List<String> fileIds;
}
