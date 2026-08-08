package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public final class FileRepoTestFixture {
    private FileRepoTestFixture() {
    }

    public static void recreateUserFileTables(DSLContext dsl, String shardKey) {
        DSLContext shardDsl = DSL.using(dsl.configuration().derive(DSLContextHolder.newSettings(shardKey)));
        shardDsl.dropTableIfExists(FILE_CLOSURE).execute();
        shardDsl.dropTableIfExists(FILE_ENTRY).execute();
        shardDsl.dropTableIfExists(FILE).execute();
        shardDsl.ddl(FILE, FILE_CLOSURE, FILE_ENTRY).executeBatch();
    }
}
