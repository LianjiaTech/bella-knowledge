package com.ke.bella.files.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * File purpose enumeration
 */
@Getter
@AllArgsConstructor
public enum FilePurpose {

    ASSISTANTS("assistants"),
    VISION("vision"),
    FINE_TUNE("fine-tune"),
    EVALS("evals"),
    USER_DATA("user_data"),
    PDF("pdf"),
    DOM_TREE("dom_tree"),
    DATASETS_EXPORT("datasets_export"),
    BATCH("batch"),
    TEMP("temp");

    private final String value;
}
