package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OpenAIFile {
    private String id;
    @Builder.Default
    private String object = "file";
    private Long bytes;
    private Long createdAt;
    private String filename;
    private Boolean isDir;
    /**
     * assistants
     * assistants_output
     * batch
     * batch_output
     * fine-tune
     * fine-tune-results
     * vision
     */
    private String purpose;
    private Boolean deleted;
    @Deprecated
    private String status;
    @Deprecated
    private String statusDetails;
    private String url;
    /**
     * subtype of mime_type. eg: image/jpeg
     */
    @JsonProperty("mime_type")
    private String mimeType;
    /**
     * type of mime_type. eg: image
     */
    private String type;
    private String extension;
    private String domTreeFileId;
    private OpenAIFile sourceFile;
    private String pdfFileId;
    private Long version;
    private String metadata;
    private String spaceCode;
    private Long cuid;
    private String cuName;
    private String path;
    private Long muid;
    private String muName;
    private Long mtime;
    private String description;
    private List<String> cities;
    private List<String> tags;
}
