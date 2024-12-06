package com.ke.bella.files.protocol;

public enum BroadcastStatus {
    SUCCESS(1L),
    FAILED(0L);

    private final Long value;

    BroadcastStatus(Long value) {
        this.value = value;
    }

    public Long getValue() {
        return value;
    }
}
