package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionTemplate;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

/**
 * 非空目录跨空间迁移的事务性验证：事务中途失败必须整体回滚，源目录保持完好、无中间状态。
 */
public class FileEntryCrossSpaceMoveTransactionTest {
    private static final String SOURCE_SPACE = "sp-a";
    private static final String TARGET_SPACE = "sp-b";

    private DSLContext dsl;
    private TransactionTemplate transactionTemplate;
    private FileRepo fileRepo;
    private FileEntryRepo entryRepo;

    @Before
    public void setup() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:fileEntryCrossSpaceMove;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        h2.setUser("sa");
        DataSource dataSource = h2;
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.H2);
        configuration.setConnectionProvider(new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource)));
        dsl = new DefaultDSLContext(configuration);

        String sourceShard = FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE);
        String targetShard = FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE);
        assertNotEquals(sourceShard, targetShard);
        FileRepoTestFixture.recreateUserFileTables(dsl, sourceShard);
        dsl.execute("drop table if exists file_" + targetShard);
        dsl.execute("drop table if exists file_closure_" + targetShard);
        dsl.execute("drop table if exists file_entry_" + targetShard);
        dsl.execute("create table file_" + targetShard + " as select * from file_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_closure_" + targetShard + " as select * from file_closure_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_entry_" + targetShard + " as select * from file_entry_" + sourceShard + " where 1 = 0");
        IDGenerator.setInstanceId(1L);
        entryRepo = new FileEntryRepo(dsl);
        fileRepo = new FileRepo(dsl, entryRepo);
        entryRepo.setFileEntryWriteMode("entry");
        entryRepo.setCrossSpaceMoveEnabled(true);
        setOperator(SOURCE_SPACE);
    }

    @Test
    public void midTransactionFailureRollsBackWholeSubtreeMigration() {
        FileDB root = addDirectory(SOURCE_SPACE, "tx-root", null, "tx-root");
        FileDB sub = addDirectory(SOURCE_SPACE, "tx-sub", root.getFileId(), "tx-sub");
        FileDB leaf = addFile(SOURCE_SPACE, "tx-leaf.txt", sub.getFileId(), "tx-leaf");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "tx-target", null, "tx-target");

        // 直接置删一个子孙的 file 行制造数据异常：space_code 缓存刷新计数不符，在 entry 已搬迁后触发失败
        DSLContextHolder.get(FileRepo.getShardingKeyByFileIdStatic(leaf.getFileId()), dsl)
                .update(FILE)
                .set(FILE.STATUS, FileStatus.DELETED.getValue())
                .where(FILE.FILE_ID.eq(leaf.getFileId()))
                .execute();
        List<String> sourceEntriesBefore = snapshotEntries(SOURCE_SPACE);
        List<String> targetEntriesBefore = snapshotEntries(TARGET_SPACE);

        setOperator(SOURCE_SPACE);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> transactionTemplate.execute(status ->
                        entryRepo.moveAcrossSpace(root.getFileId(), TARGET_SPACE, targetParent.getFileId())));

        assertTrue(error.getMessage().contains("update file space cache failed"));
        // 整体回滚：源子树 entry 完好，目标空间无残留，file.space_code 缓存未变
        assertEquals(sourceEntriesBefore, snapshotEntries(SOURCE_SPACE));
        assertEquals(targetEntriesBefore, snapshotEntries(TARGET_SPACE));
        assertEquals(SOURCE_SPACE, fileRepo.queryFile(root.getFileId()).getSpaceCode());
        assertEquals(SOURCE_SPACE, fileRepo.queryFile(sub.getFileId()).getSpaceCode());
    }

    private List<String> snapshotEntries(String spaceCode) {
        return DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(spaceCode), dsl)
                .selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .orderBy(FILE_ENTRY.ENTRY_ID.asc())
                .fetch()
                .stream()
                .map(record -> record.getEntryId() + ":" + record.getParentEntryId() + ":" + record.getFilename())
                .collect(Collectors.toList());
    }

    private FileDB addDirectory(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.DIRECTORY, 1, NodeType.DIRECTORY);
    }

    private FileDB addFile(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.USER, 0, NodeType.FILE);
    }

    private FileDB add(String spaceCode, String filename, String ancestorId, String seed, FileType type, int isDir,
            NodeType nodeType) {
        setOperator(spaceCode);
        String hash = String.valueOf(Math.abs(CustomStringUtils.hashCode(spaceCode)));
        String fileId = "file-260808000000" + String.format("%06d", Math.abs(seed.hashCode()) % 1000000) + "-" + hash + type.getSuffix();
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename(filename);
        file.setIsDir(isDir);
        file.setNodeType(nodeType.getValue());
        file.setResourceId("");
        file.setSpaceCode(spaceCode);
        file.setPurpose("assistants");
        file.setStatus(FileStatus.NOT_DELETED.getValue());
        file.setBucket("bucket");
        file.setPath("path/" + seed);
        file.setMetaData("{}");
        fileRepo.addFile(file, ancestorId, type);
        return fileRepo.queryFile(fileId, type);
    }

    private void setOperator(String spaceCode) {
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
    }
}
