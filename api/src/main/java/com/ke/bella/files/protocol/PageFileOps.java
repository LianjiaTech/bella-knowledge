package com.ke.bella.files.protocol;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageFileOps {

    /**
     * 页码（从1开始），默认 1
     */
    @Builder.Default
    private int page = 1;

    /**
     * 每页条数，默认 10
     */
    @Builder.Default
    @JsonProperty("page_size")
    private int pageSize = 10;

    /**
     * 空间编码
     */
    @JsonProperty("space_code")
    private String spaceCode;

    /**
     * 父级目录ID
     */
    @JsonProperty("ancestor_id")
    private String ancestorId;

    /**
     * 用途标识
     */
    private String purpose;
    /**
     * 标签过滤
     */
    private List<String> tags;
    /**
     * 城市过滤
     */
    private List<String> cities;
    /**
     * 创建人ID过滤
     */
    private Long cuid;
    /**
     * 修改人ID过滤
     */
    private Long muid;
    /**
     * 文件名过滤
     */
    private String filename;
    /**
     * 类型过滤：file（仅文件）、dir（仅目录）
     */
    private String type;
    /**
     * 排序：desc/asc，默认 desc
     */
    @Builder.Default
    private String order = "desc";

    /**
     * 精确文件ID过滤
     */
    @JsonProperty("file_id")
    private String fileId;

    /**
     * extension
     */
    private String extension;
}
