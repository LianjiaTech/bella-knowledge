package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadPart {
    private String id;
    @Builder.Default
    private String object = "upload.part";
    private String uploadId;
    private Long createdAt;
    private Integer partNumber;
    private Long size;
    private String etag;
}
