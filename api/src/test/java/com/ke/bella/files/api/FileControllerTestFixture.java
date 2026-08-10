package com.ke.bella.files.api;

import static org.mockito.Mockito.when;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.service.FileService;

final class FileControllerTestFixture {

    private FileControllerTestFixture() {
    }

    static void stubDirectory(FileService fileService, String fileId) {
        stubDirectory(fileService, fileId, null);
    }

    static void stubDirectory(FileService fileService, String fileId, String spaceCode) {
        FileDB directory = new FileDB();
        directory.setFileId(fileId);
        directory.setSpaceCode(spaceCode);
        directory.setIsDir(1);
        directory.setNodeType(NodeType.DIRECTORY.getValue());
        when(fileService.getFile0(fileId)).thenReturn(directory);
    }
}
