package com.ke.bella.files.protocol;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BellaResponse<T> {
    private int code;
    private String message;
    private long timestamp;
    private T data;
    private String stacktrace;
}
