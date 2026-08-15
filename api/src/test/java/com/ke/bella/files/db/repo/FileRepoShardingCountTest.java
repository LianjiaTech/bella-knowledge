package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE_SHARDING;
import static org.junit.Assert.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileRepoShardingCountTest {
    private Connection connection;
    private DSLContext dsl;
    private FileRepo fileRepo;

    @Before
    public void setUp() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:fileRepoShardingCount;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dsl = DSL.using(connection, SQLDialect.H2);
        dsl.execute("create table file_sharding (type varchar(32) not null, `key` varchar(255) not null, "
                + "count bigint not null, mtime timestamp, primary key (type, `key`))");
        insert("temp", "");
        insert("system", "");
        insert("temp", "250815120000");
        fileRepo = new FileRepo(dsl, new FileEntryRepo(dsl));
    }

    @After
    public void tearDown() throws Exception {
        connection.close();
    }

    @Test
    public void increasesInitialTempAndSystemShardCounts() {
        assertEquals(1, fileRepo.increaseFileShardingCount("temp", 2, "temp"));
        assertEquals(1, fileRepo.increaseFileShardingCount("system", 3, "system"));
        assertEquals(Long.valueOf(2), count("temp", ""));
        assertEquals(Long.valueOf(3), count("system", ""));
    }

    @Test
    public void increasesTimeSuffixedShardCountUsingMetadataKey() {
        assertEquals(1, fileRepo.increaseFileShardingCount("temp_250815120000", 4, "temp"));
        assertEquals(Long.valueOf(4), count("temp", "250815120000"));
    }

    @Test
    public void reportsMissingShardWithoutUpdatingAnyRow() {
        assertEquals(0, fileRepo.increaseFileShardingCount("system_250815120000", 4, "system"));
    }

    private void insert(String type, String key) {
        dsl.insertInto(FILE_SHARDING)
                .set(FILE_SHARDING.TYPE, type)
                .set(FILE_SHARDING.KEY, key)
                .set(FILE_SHARDING.COUNT, 0L)
                .execute();
    }

    private Long count(String type, String key) {
        return dsl.select(FILE_SHARDING.COUNT).from(FILE_SHARDING)
                .where(FILE_SHARDING.TYPE.eq(type)).and(FILE_SHARDING.KEY.eq(key))
                .fetchOne(FILE_SHARDING.COUNT);
    }
}
