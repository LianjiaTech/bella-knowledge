package com.ke.bella.files.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ke.bella.files.enums.FilePurpose;
import com.ke.bella.files.enums.FileType;

public class FilePurposeClassifierTest {

    @Test
    public void visionPurposeIsClassifiedAsTemp() {
        assertEquals(FileType.TEMP, FilePurposeClassifier.classify(FilePurpose.VISION.getValue()));
    }

    @Test
    public void visionPurposeIsAllowedButNotAUserPurpose() {
        assertTrue(FilePurposeClassifier.allowedPurposes().contains(FilePurpose.VISION.getValue()));
        assertFalse(FilePurposeClassifier.isUserFile("file-vision-t"));
    }

    @Test
    public void visionPurposeRemainsProgressTrackable() {
        assertTrue(FilePurposeClassifier.allowedProgressTrackablePurposes()
                .contains(FilePurpose.VISION.getValue()));
    }
}
