package com.ke.bella.files.protocol;

public enum FileStatus {
    NOT_DELETED(0),
    DELETED(-1);

    private final Integer value;

    FileStatus(Integer value) {
        this.value = value;
    }

    public Integer getValue() {
        return value;
    }
}
