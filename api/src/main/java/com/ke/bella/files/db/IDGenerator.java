package com.ke.bella.files.db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.ke.bella.files.BellaContext;
import com.ke.bella.files.utils.CustomStringUtils;

public class IDGenerator {
    public static final IDGenerator FILEID_GEN = new IDGenerator("file-");
    private static final String yyMMddHHmmss = "yyMMddHHmmss";
    private static final int MAX_COUNT = (int) 1e7;
    private static String instanceId;
    private final int serialLength;
    private final int serialMask;
    private final String prefix;
    private final String serialFormat;
    private final AtomicInteger serialCounter = new AtomicInteger(0);

    public IDGenerator(String prefix) {
        this(prefix, 6);
    }

    public IDGenerator(String prefix, int serialLength) {
        this.prefix = prefix;
        this.serialLength = serialLength;
        this.serialFormat = "%0" + this.serialLength + "d";
        this.serialMask = Integer.parseInt("1" + String.format(serialFormat, 0));
    }

    public static void setInstanceId(Long id) {
        int idx = id.intValue();
        if(idx > 9999) {
            throw new IllegalStateException("超出当前所能够支持的最大实例数");
        }
        instanceId = String.format("%04d", idx);
    }

    public String generate() {
        String now = new SimpleDateFormat(yyMMddHHmmss).format(new Date());
        String spaceCode = BellaContext.getOperator().getSpaceCode();
        String spaceCodeHash = String.valueOf(Math.abs(CustomStringUtils.hashCode(spaceCode)));
        return String.format("%s%s%s%s-%s", this.prefix, now, instanceId, nextTick(), spaceCodeHash);
    }

    private String nextTick() {
        int val = serialCounter.incrementAndGet();
        if(val >= MAX_COUNT) {
            synchronized(serialCounter) {
                val = serialCounter.get();
                if(val >= MAX_COUNT) {
                    serialCounter.set(0);
                }
            }
            val = serialCounter.incrementAndGet();
        }
        return String.format(this.serialFormat, val % this.serialMask);
    }
}
