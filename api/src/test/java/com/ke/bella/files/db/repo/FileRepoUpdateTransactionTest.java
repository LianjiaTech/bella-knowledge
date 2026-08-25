package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
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

    @javax.annotation.Resource
    private FileEntryRepo fileEntryRepo;

    @Before
    public void setup() {
        fileEntryRepo.setFileEntryWriteMode("dual");
        FileRepoTestFixture.recreateUserFileTables(dsl, "1");
        shardDsl = DSLContextHolder.get("1", dsl);
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(SPACE_CODE).build());
        addFile(SOURCE, "source.txt");
        addFile(CONFLICT, "conflict.txt");
    }

    @Test
    public void renameConflictRollsBackFileUpdateInDualMode() {
        assertThrows(IllegalStateException.class,
                () -> fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("conflict.txt").build()));

        assertEquals("source.txt", queryFile(SOURCE).getFilename());
        assertEquals("source.txt", queryEntryFilename(SOURCE));
    }

    @Test
    public void renameConflictRollsBackFileUpdateWhenClosureWriteStopped() {
        fileEntryRepo.setFileEntryWriteMode("entry");

        assertThrows(IllegalStateException.class,
                () -> fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("conflict.txt").build()));

        assertEquals("source.txt", queryFile(SOURCE).getFilename());
        assertEquals("source.txt", queryEntryFilename(SOURCE));
    }

    @Test
    public void closureModeSkipsEntryWritesAndKeepsFileUpdate() {
        fileEntryRepo.setFileEntryWriteMode("closure");

        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("conflict.txt").build());

        assertEquals("conflict.txt", queryFile(SOURCE).getFilename());
        assertEquals("source.txt", queryEntryFilename(SOURCE));
    }

    @Test
    public void unchangedFilenameLeavesEntryUntouched() {
        String entryId = queryEntryId(SOURCE);

        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).filename("source.txt").build());

        assertEquals("source.txt", queryFile(SOURCE).getFilename());
        assertEquals(entryId, queryEntryId(SOURCE));
        assertEquals(1, shardDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.FILE_ID.eq(SOURCE)));
    }

    @Test
    public void metadataUpdateLeavesOtherFileFieldsUntouched() {
        FileDB before = queryFile(SOURCE);

        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).metadata("{\"team\":\"search\"}").build(), false);

        FileDB after = queryFile(SOURCE);
        assertEquals("{\"team\":\"search\"}", after.getMetaData());
        assertEquals(before.getFilename(), after.getFilename());
        assertEquals(before.getPurpose(), after.getPurpose());
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(queryEntryFilename(SOURCE), after.getFilename());
    }

    @Test
    public void deleteHardDeletesEntry() {
        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).status(FileStatus.DELETED).build());

        assertEquals(FileStatus.DELETED.getValue(), queryFile(SOURCE).getStatus());
        assertEquals(0, shardDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.FILE_ID.eq(SOURCE)));
        assertNull(queryEntryFilename(SOURCE));
    }

    @Test
    public void missingLegacyEntryDoesNotBlockFileDelete() {
        shardDsl.deleteFrom(FILE_ENTRY).where(FILE_ENTRY.FILE_ID.eq(SOURCE)).execute();

        fileRepo.updateFile(FileOps.builder().fileId(SOURCE).status(FileStatus.DELETED).build());

        assertEquals(FileStatus.DELETED.getValue(), queryFile(SOURCE).getStatus());
        assertEquals(0, shardDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.FILE_ID.eq(SOURCE)));
    }

    @Test
    public void duplicateFileEntryIsRejectedBySchema() {
        assertThrows(DataAccessException.class, () -> shardDsl.insertInto(FILE_ENTRY)
                .set(FILE_ENTRY.ENTRY_ID, "entry-duplicate-file")
                .set(FILE_ENTRY.SPACE_CODE, SPACE_CODE)
                .set(FILE_ENTRY.PARENT_ENTRY_ID, "")
                .set(FILE_ENTRY.FILE_ID, SOURCE)
                .set(FILE_ENTRY.FILENAME, "duplicate-file.txt")
                .set(FILE_ENTRY.TYPE, FileEntryRepo.TYPE_FILE)
                .execute());
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
                .fetchOneInto(String.class);
    }

    private String queryEntryId(String fileId) {
        return shardDsl.select(FILE_ENTRY.ENTRY_ID).from(FILE_ENTRY)
                .where(FILE_ENTRY.FILE_ID.eq(fileId))
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
