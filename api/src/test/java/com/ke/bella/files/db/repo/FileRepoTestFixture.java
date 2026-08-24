package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;

public final class FileRepoTestFixture {
    private FileRepoTestFixture() {
    }

    /**
     * 按生产 DDL（含主键、唯一键、二级索引）重建一个分片的用户文件表。
     * jOOQ ddl() 输出的约束名和索引名不含分片号，同一 H2 库建多个分片会在全局命名空间撞名，
     * 因此给引号内的约束/索引名统一追加分片前缀；表结构本身不受影响。
     */
    public static void recreateUserFileTables(DSLContext dsl, String shardKey) {
        DSLContext shardDsl = DSL.using(dsl.configuration().derive(DSLContextHolder.newSettings(shardKey)));
        shardDsl.dropTableIfExists(FILE_CLOSURE).execute();
        shardDsl.dropTableIfExists(FILE_ENTRY).execute();
        shardDsl.dropTableIfExists(FILE).execute();
        for (Query query : shardDsl.ddl(FILE, FILE_CLOSURE, FILE_ENTRY).queries()) {
            String sql = query.getSQL()
                    .replace("constraint \"", "constraint \"shard" + shardKey + "_")
                    .replaceFirst("^create index \"", "create index \"shard" + shardKey + "_")
                    .replaceFirst("^create unique index \"", "create unique index \"shard" + shardKey + "_");
            dsl.execute(sql);
        }
    }
}
