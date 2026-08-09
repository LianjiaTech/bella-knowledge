package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = FileRepoUpdateTransactionTest.TestConfig.class)
public class FileRepoUpdateTransactionTest {
    private static final String SPACE_CODE = "sp-a";
    private static final String SOURCE = "file-source-1";
    private static final String CONFLICT = "file-conflict-1";

    @javax.annotation.Resource
    private DSLContext dsl;

    private DSLContext shardDsl;

    @javax.annotation.Resource
    private FileRepo fileRepo;

    @Before
    public void setup() {
        FileRepoTestFixture.recreateUserFileTables(dsl, "1");
        shardDsl = DSLContextHolder.get("1", dsl);
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(SPACE_CODE).build());
        addFile(SOURCE, "source.txt");
        addFile(CONFLICT, "conflict.txt");
    }

    @Test
    public void renameConflictRollsBackFileUpdate() {
        assertThrows(IllegalStateException.class,
                () -> fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("conflict.txt").build()));

        assertEquals("source.txt", queryFile(SOURCE).getFilename());
        assertEquals("source.txt", queryEntryFilename(SOURCE));
    }

    @Test
    public void unchangedFilenameSkipsEntryRename() {
        addCorruptEntry();

        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("source.txt").build());

        assertEquals("source.txt", queryFile(SOURCE).getFilename());
        assertEquals(2, shardDsl.fetchCount(FILE_ENTRY,
                FILE_ENTRY.FILE_ID.eq(SOURCE).and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))));
    }

    @Test
    public void invalidMultipleEntriesRollBackFileDelete() {
        addCorruptEntry();

        assertThrows(RuntimeException.class,
                () -> fileRepo.updateFile(FileOps.builder().fileId(SOURCE).status(FileStatus.DELETED).build()));

        assertEquals(FileStatus.NOT_DELETED.getValue(), queryFile(SOURCE).getStatus());
        assertEquals(2, shardDsl.fetchCount(FILE_ENTRY,
                FILE_ENTRY.FILE_ID.eq(SOURCE).and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))));
    }

    private void addCorruptEntry() {
        shardDsl.insertInto(FILE_ENTRY)
                .set(FILE_ENTRY.ENTRY_ID, "entry-corrupt")
                .set(FILE_ENTRY.SPACE_CODE, SPACE_CODE)
                .set(FILE_ENTRY.PARENT_ENTRY_ID, "")
                .set(FILE_ENTRY.FILE_ID, SOURCE)
                .set(FILE_ENTRY.FILENAME, "corrupt.txt")
                .set(FILE_ENTRY.TYPE, FileEntryRepo.TYPE_FILE)
                .set(FILE_ENTRY.STATUS, FileStatus.NOT_DELETED.getValue())
                .set(FILE_ENTRY.CUID, 1L)
                .set(FILE_ENTRY.CU_NAME, "tester")
                .set(FILE_ENTRY.CTIME, java.time.LocalDateTime.now())
                .set(FILE_ENTRY.MUID, 1L)
                .set(FILE_ENTRY.MU_NAME, "tester")
                .set(FILE_ENTRY.MTIME, java.time.LocalDateTime.now())
                .execute();
    }

    private void addFile(String fileId, String filename) {
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename(filename);
        file.setSpaceCode(SPACE_CODE);
        file.setPurpose("assistants");
        file.setStatus(FileStatus.NOT_DELETED.getValue());
        file.setMetaData("{}");
        fileRepo.addFile(file, null, FileType.USER);
    }

    private FileDB queryFile(String fileId) {
        return shardDsl.selectFrom(FILE).where(FILE.FILE_ID.eq(fileId)).fetchOneInto(FileDB.class);
    }

    private String queryEntryFilename(String fileId) {
        return shardDsl.select(FILE_ENTRY.FILENAME).from(FILE_ENTRY)
                .where(FILE_ENTRY.FILE_ID.eq(fileId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(String.class);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class TestConfig {
        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:fileRepoUpdate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            return dataSource;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public DSLContext dslContext(DataSource dataSource) {
            DefaultConfiguration configuration = new DefaultConfiguration();
            configuration.setSQLDialect(SQLDialect.H2);
            configuration.setConnectionProvider(new org.jooq.impl.DataSourceConnectionProvider(
                    new TransactionAwareDataSourceProxy(dataSource)));
            return new DefaultDSLContext(configuration);
        }

        @Bean
        public FileEntryRepo fileEntryRepo(DSLContext dslContext) {
            return new FileEntryRepo(dslContext);
        }

        @Bean
        public FileRepo fileRepo(DSLContext dslContext, FileEntryRepo fileEntryRepo) {
            return new FileRepo(dslContext, fileEntryRepo);
        }
    }
}
