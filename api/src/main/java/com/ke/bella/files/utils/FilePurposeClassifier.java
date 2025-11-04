package com.ke.bella.files.utils;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.files.enums.FilePurpose;
import com.ke.bella.files.enums.FileType;

/**
 * File purpose classifier
 */
public class FilePurposeClassifier {

    private static final Set<String> USER_PURPOSES = new HashSet<>();
    private static final Set<String> SYSTEM_PURPOSES = new HashSet<>();
    private static final Set<String> TEMP_PURPOSES = new HashSet<>();
    private static final Set<String> PROGRESS_TRACKABLE_PURPOSES = new HashSet<>();

    static {
        // User files
        USER_PURPOSES.add(FilePurpose.ASSISTANTS.getValue());
        USER_PURPOSES.add(FilePurpose.VISION.getValue());
        USER_PURPOSES.add(FilePurpose.USER_DATA.getValue());

        // System files
        SYSTEM_PURPOSES.add(FilePurpose.PDF.getValue());
        SYSTEM_PURPOSES.add(FilePurpose.DOM_TREE.getValue());
        SYSTEM_PURPOSES.add(FilePurpose.DATASETS_EXPORT.getValue());

        // Temp files
        TEMP_PURPOSES.add(FilePurpose.BATCH.getValue());
        TEMP_PURPOSES.add(FilePurpose.TEMP.getValue());
        TEMP_PURPOSES.add(FilePurpose.FINE_TUNE.getValue());
        TEMP_PURPOSES.add(FilePurpose.EVALS.getValue());
        TEMP_PURPOSES.add(FilePurpose.ASSISTANTS_CHAT.getValue());

        // Progress trackable purposes
        PROGRESS_TRACKABLE_PURPOSES.add(FilePurpose.ASSISTANTS.getValue());
        PROGRESS_TRACKABLE_PURPOSES.add(FilePurpose.ASSISTANTS_CHAT.getValue());
        PROGRESS_TRACKABLE_PURPOSES.add(FilePurpose.VISION.getValue());
    }

    public static FileType classify(String purpose) {
        if(StringUtils.isEmpty(purpose)) {
            return FileType.TEMP; // 默认为temp
        }

        if(USER_PURPOSES.contains(purpose)) {
            return FileType.USER;
        }

        if(SYSTEM_PURPOSES.contains(purpose)) {
            return FileType.SYSTEM;
        }

        if(TEMP_PURPOSES.contains(purpose)) {
            return FileType.TEMP;
        }

        // Default to temp for unknown purposes
        return FileType.TEMP;
    }

    /**
     * 根据fileId判断是否为用户空间文件（包括目录）
     */
    public static boolean isUserFile(String fileId) {
        if(StringUtils.isEmpty(fileId)) {
            return false;
        }
        FileType fileType = FileType.fromFileId(fileId);
        return fileType.isUsersType();
    }

    public static Set<String> allowedPurposes() {
        Set<String> all = new HashSet<>();
        all.addAll(USER_PURPOSES);
        all.addAll(SYSTEM_PURPOSES);
        all.addAll(TEMP_PURPOSES);
        return all;
    }

    public static Set<String> allowedProgressTrackablePurposes() {
        return new HashSet<>(PROGRESS_TRACKABLE_PURPOSES);
    }
}
