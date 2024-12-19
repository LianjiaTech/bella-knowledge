package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
}
