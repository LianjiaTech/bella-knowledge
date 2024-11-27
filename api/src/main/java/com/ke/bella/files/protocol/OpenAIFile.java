package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class OpenAIFile {
    private String id;
    private Long bytes;
    private Long create_at;
    private String fileName;
    private String object = "file";
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
    @Deprecated
    private String status;
    @Deprecated
    private String status_details;
}
