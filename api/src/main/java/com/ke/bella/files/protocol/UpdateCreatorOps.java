package com.ke.bella.files.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateCreatorOps {

    private Long cuid;
    private String cuName;
    private Long createdAt;

    @JsonIgnore
    private boolean cuidSet;
    @JsonIgnore
    private boolean cuNameSet;
    @JsonIgnore
    private boolean createdAtSet;

    @JsonSetter("cuid")
    public void setCuid(Long cuid) {
        this.cuid = cuid;
        this.cuidSet = true;
    }

    @JsonSetter("cu_name")
    public void setCuName(String cuName) {
        this.cuName = cuName;
        this.cuNameSet = true;
    }

    @JsonSetter("created_at")
    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
        this.createdAtSet = true;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return cuidSet || cuNameSet || createdAtSet;
    }
}
