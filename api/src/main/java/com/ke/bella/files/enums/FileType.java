package com.ke.bella.files.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * File type enumeration for different file categories
 */
@Getter
@AllArgsConstructor
public enum FileType {

    SYSTEM("system", "-s"),
    TEMP("temp", "-t"),
    /**
     * 普通用户文件/目录
     */
    USER("user", ""),
    DIRECTORY("directory", "-d");

    private final String type;
    private final String suffix;

    /**
     * Get FileType from file ID by checking suffix
     */
    public static FileType fromFileId(String fileId) {
        if(fileId == null) {
            return null;
        }

        if(fileId.endsWith(SYSTEM.suffix)) {
            return SYSTEM;
        }
        if(fileId.endsWith(TEMP.suffix)) {
            return TEMP;
        }
        if(fileId.endsWith(DIRECTORY.suffix)) {
            return DIRECTORY;
        }
        return USER;
    }

    /**
     * Get FileType from type string
     */
    public static FileType fromType(String type) {
        if(type == null) {
            return null;
        }

        for (FileType fileType : values()) {
            if(fileType.type.equals(type)) {
                return fileType;
            }
        }
        return null;
    }

    /**
     * 判断是否为用户空间文件（包括普通用户文件和目录）
     */
    public boolean isUsersType() {
        return this == USER || this == DIRECTORY;
    }

    /**
     * 判断是否为基于时间的分片
     */
    public boolean notUsersType() {
        return this == SYSTEM || this == TEMP;
    }

    /**
     * 判断是否需要目录结构支持
     */
    public boolean needsDirectorySupport() {
        return isUsersType();
    }

    /**
     * 判断是否为目录类型
     */
    public boolean isDirectory() {
        return this == DIRECTORY;
    }

    /**
     * 判断是否为普通用户文件
     */
    public boolean isUserFile() {
        return this == USER;
    }
}
