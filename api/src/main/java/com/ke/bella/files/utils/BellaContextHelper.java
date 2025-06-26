package com.ke.bella.files.utils;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;

public class BellaContextHelper {

    private static final String OPERATOR_ID_HEADER = "X-BELLA-OPERATOR-ID";
    private static final String OPERATOR_NAME_HEADER = "X-BELLA-OPERATOR-NAME";
    private static final String SPACE_CODE_HEADER = "X-BELLA-SPACE-CODE";

    public static String getOperateSpaceCode() {
        String spaceCode = BellaContext.getHeader(SPACE_CODE_HEADER);
        if(StringUtils.isNotEmpty(spaceCode)) {
            return spaceCode;
        }
        return BellaContext.getOperator().getSpaceCode();
    }

    public static Long getOperatorUserId() {
        String operatorId = BellaContext.getHeader(OPERATOR_ID_HEADER);
        if(StringUtils.isNotEmpty(operatorId)) {
            return Long.valueOf(operatorId);
        }
        return BellaContext.getOperator().getUserId();
    }

    public static String getOperatorUserName() {
        String operatorName = BellaContext.getHeader(OPERATOR_NAME_HEADER);
        if(StringUtils.isNotEmpty(operatorName)) {
            return operatorName;
        }
        return BellaContext.getOperator().getUserName();
    }

    public static String getOperatorAkCode() {
        return Optional.ofNullable(BellaContext.getApikeyIgnoreNull()).map(ApikeyInfo::getCode)
                .orElse(BellaContext.getOperator().getManagerAk());
    }

}
