package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Upload {
    private String id;
    @Builder.Default
    private String object = "upload";
    private String filename;
    private String purpose;
    private Long bytes;
    private String status;
    private Long createdAt;
    private Long expiresAt;
    private OpenAIFile file;
    private Long partSizeMin;
    private Long partSizeMax;
    private Integer maxParts;
}
