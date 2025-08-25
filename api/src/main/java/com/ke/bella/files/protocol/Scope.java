package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Scope {
    CONTENT("content"),
    FILENAME("filename"),
    DOM_TREE("dom_tree"),
    PDF("pdf"),;

    private final String value;
}
