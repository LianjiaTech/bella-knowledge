package com.ke.bella.files.protocol;

import lombok.Getter;

@Getter
public class UploadException extends IllegalArgumentException {
    private final int httpStatus;
    private final String errorCode;

    public UploadException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
