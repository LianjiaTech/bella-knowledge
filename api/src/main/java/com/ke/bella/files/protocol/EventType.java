package com.ke.bella.files.protocol;

import lombok.Getter;

@Getter
public enum EventType {
    FILE_CREATED("file.created");

    private final String value;

    EventType(String value) {
        this.value = value;
    }
}
