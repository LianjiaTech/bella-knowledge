package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageFileOps {

    /**
     * 页码（从1开始），默认 1
     */
    @JsonProperty("page_no")
    private long pageNo = 1L;

    /**
     * 每页条数，默认 10
     */
    @JsonProperty("page_size")
    private long pageSize = 10L;

    /**
     * 空间编码（与 ancestor_id 至少提供一个）
     */
    @JsonProperty("space_code")
    private String spaceCode;

    /**
     * 父级目录ID（与 space_code 至少提供一个）
     */
    @JsonProperty("ancestor_id")
    private String ancestorId;

    /**
     * 是否返回临时下载 URL
     */
    @JsonProperty("get_url")
    private boolean getUrl;

    /**
     * URL 过期时间（单位：秒），默认 24*60*60
     */
    private long expires = 24 * 60 * 60L;

    /**
     * 用途标识（可选）
     */
    private String purpose;
    /**
     * 标签过滤（可选）
     */
    private List<String> tags;
    /**
     * 城市过滤（可选）
     */
    private List<String> cities;
    /**
     * 创建人ID过滤（可选）
     */
    private Long cuid;
    /**
     * 修改人ID过滤（可选）
     */
    private Long muid;
    /**
     * 文件名过滤（可选）
     */
    private String filename;
    /**
     * 类型过滤：file（仅文件）、dir（仅目录），为空表示全部
     */
    private String type;
    /**
     * 排序：desc/asc，默认 desc
     */
    private String order = "desc";
}
