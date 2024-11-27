package com.ke.bella.files.api;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Operator {
    protected Long userId;
    protected String userName;
    protected String spaceCode;

    public String getSpaceCode() {
        return StringUtils.isEmpty(spaceCode) ? String.valueOf(userId) : spaceCode;
    }
}
