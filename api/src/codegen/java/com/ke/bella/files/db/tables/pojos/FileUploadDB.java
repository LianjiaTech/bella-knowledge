package com.ke.bella.files.db.tables.pojos;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadDB {
    private Long id;
    private String uploadId;
    private String spaceCode;
    private String akCode;
    private String fileId;
    private String filename;
    private String extension;
    private String purpose;
    private String mimeType;
    private String type;
    private String charset;
    private String bucket;
    private String path;
    private Long declaredBytes;
    private String storageUploadId;
    private String ancestorId;
    private String metadata;
    private String description;
    private String cities;
    private String tags;
    private String status;
    private LocalDateTime expiresAt;
    private Long cuid;
    private String cuName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;
}
