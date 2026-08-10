package com.ke.bella.files.enums;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.files.db.tables.pojos.FileDB;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NodeType {
    FILE("file"),
    DIRECTORY("directory"),
    RESOURCE("resource");

    private final String value;

    public static NodeType from(FileDB file) {
        if(file == null) {
            return null;
        }
        if(Integer.valueOf(1).equals(file.getIsDir())) {
            return DIRECTORY;
        }
        if(StringUtils.isNotEmpty(file.getNodeType())) {
            for (NodeType nodeType : values()) {
                if(nodeType.value.equals(file.getNodeType())) {
                    return nodeType;
                }
            }
            throw new IllegalStateException("Unsupported node_type: " + file.getNodeType());
        }
        return FILE;
    }
}
