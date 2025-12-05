package com.ke.bella.files.protocol;

import lombok.Getter;

@Getter
public enum EventType {
    FILE_CREATED("file.created"),
    FILE_UPDATED("file.updated"),
    FILE_DELETED("file.deleted");

    private final String value;

    EventType(String value) {
        this.value = value;
    }
}
