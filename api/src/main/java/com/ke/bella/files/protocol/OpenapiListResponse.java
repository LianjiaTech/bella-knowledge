package com.ke.bella.files.protocol;

import java.util.List;

import com.ke.bella.openapi.protocol.OpenapiResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenapiListResponse<T> extends OpenapiResponse {
    private List<T> data;
    private String object = "list";
    private String lastId;
    private Boolean hasMore = false;
}
