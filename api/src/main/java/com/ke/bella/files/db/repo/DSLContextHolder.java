package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.DATASET_QA;
import static com.ke.bella.files.db.Tables.DATASET_QA_REFERENCE;
import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;

import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.MappedTable;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class DSLContextHolder {
    private static final Cache<String, DSLContext> configurations = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    public static synchronized DSLContext get(String key, final DSLContext db) {
        if(StringUtils.isEmpty(key)) {
            return db;
        }

        DSLContext ret = configurations.getIfPresent(key);
        if(ret == null) {
            ret = DSL.using(db.configuration().derive(newSettings(key)));
            configurations.put(key, ret);
        }

        return ret;
    }

    public static Settings newSettings(String key) {
        return new Settings().withRenderMapping(new RenderMapping()
                .withSchemata( // 为对象设置表的映射
                        new MappedSchema()
                                .withInputExpression(Pattern.compile(".*"))
                                .withTables(
                                        new MappedTable()
                                                .withInput(FILE.getName())
                                                .withOutput(targetTableName(FILE.getName(), key)),
                                        new MappedTable().withInput(FILE_PROGRESS.getName())
                                                .withOutput(targetTableName(FILE_PROGRESS.getName(), key)),
                                        new MappedTable()
                                                .withInput(DATASET_QA.getName())
                                                .withOutput(targetTableName(DATASET_QA.getName(), key)),
                                        new MappedTable()
                                                .withInput(DATASET_QA_REFERENCE.getName())
                                                .withOutput(targetTableName(DATASET_QA_REFERENCE.getName(), key)))));
    }

    public static String targetTableName(String orignalName, String key) {
        if(StringUtils.isEmpty(key)) {
            return orignalName;
        }
        return String.format("%s_%s", orignalName, key); // todo 修改为多段
    }
}
