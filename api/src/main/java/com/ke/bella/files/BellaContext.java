package com.ke.bella.files;

import com.ke.bella.files.api.Operator;
import com.ke.bella.openapi.apikey.ApikeyInfo;

public class BellaContext {
    private static final ThreadLocal<ApikeyInfo> akThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<Operator> operatorLocal = new ThreadLocal<>();

    public static ApikeyInfo getApikey() {
        return akThreadLocal.get();
    }

    public static void setApikey(ApikeyInfo ak) {
        akThreadLocal.set(ak);
    }

    public static Operator getOperator() {
        return operatorLocal.get();
    }

    public static void setOperator(Operator operator) {
        operatorLocal.set(operator);
    }

    public static void clearAll() {
        akThreadLocal.remove();
        operatorLocal.remove();
    }
}
