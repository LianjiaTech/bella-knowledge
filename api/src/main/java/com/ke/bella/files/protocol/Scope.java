package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Scope {
    CONTENT("content"),
    FILENAME("filename"),
    DOM_TREE("dom_tree"),
    PDF("pdf"),
    DESCRIPTION("description"),
    CITIES("cities"),
    TAGS("tags");

    private final String value;
}
