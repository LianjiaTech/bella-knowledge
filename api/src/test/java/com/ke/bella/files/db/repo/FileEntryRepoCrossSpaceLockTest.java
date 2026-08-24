package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE_ENTRY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

/**
 * 跨空间迁移的目标父目录锁：解析 targetParentEntryId 后必须 FOR UPDATE 复核，
 * 否则并发事务把目标父目录迁走后，本事务插入的子树会指向目标空间不存在的 entry。
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = FileEntryRepoCrossSpaceLockTest.TestConfig.class)
public class FileEntryRepoCrossSpaceLockTest {
    private static final String SOURCE_SPACE = "sp-a";
    private static final String TARGET_SPACE = "sp-b";

    @javax.annotation.Resource
    private DSLContext dsl;
    @javax.annotation.Resource
    private FileEntryRepo entryRepo;
    @javax.annotation.Resource
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @Before
    public void setup() {
        String sourceShard = FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE);
        String targetShard = FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE);
        FileRepoTestFixture.recreateUserFileTables(dsl, sourceShard);
        // jOOQ ddl() 的命名约束在同库不能重复创建，目标分片表复制源分片结构
        dsl.execute("drop table if exists file_" + targetShard);
        dsl.execute("drop table if exists file_closure_" + targetShard);
        dsl.execute("drop table if exists file_entry_" + targetShard);
        dsl.execute("create table file_" + targetShard + " as select * from file_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_closure_" + targetShard + " as select * from file_closure_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_entry_" + targetShard + " as select * from file_entry_" + sourceShard + " where 1 = 0");
        IDGenerator.setInstanceId(1L);
        // @Value 注入在 @Bean 方法之后执行，开关必须在 bean 初始化完成后设置到代理背后的目标对象
        FileEntryRepo target = AopTestUtils.getTargetObject(entryRepo);
        target.setFileEntryWriteMode("entry");
        target.setCrossSpaceMoveEnabled(true);
        executor = Executors.newFixedThreadPool(2);
        setOperator(SOURCE_SPACE);
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void crossSpaceMoveWaitsForTargetParentLockAndFailsWhenParentMovedAway() throws Exception {
        FileDB movedDir = addDirectory(SOURCE_SPACE, "locked-src", null, "locked-src");
        FileDB child = addFile(SOURCE_SPACE, "locked-child.txt", movedDir.getFileId(), "locked-child");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "locked-target", null, "locked-target");
        String targetParentEntryId = entryRepo.queryActiveByFileId(TARGET_SPACE, targetParent.getFileId()).getEntryId();

        CountDownLatch parentLocked = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch moveFinished = new CountDownLatch(1);
        AtomicReference<Throwable> moveError = new AtomicReference<>();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            // 事务一：模拟并发操作把目标父目录迁走——锁定其 entry 行并删除，提交前挂起
            Future<?> parentMove = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                DSLContext targetDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE), dsl);
                targetDsl.selectFrom(FILE_ENTRY)
                        .where(FILE_ENTRY.SPACE_CODE.eq(TARGET_SPACE))
                        .and(FILE_ENTRY.ENTRY_ID.eq(targetParentEntryId))
                        .forUpdate()
                        .fetch();
                targetDsl.deleteFrom(FILE_ENTRY)
                        .where(FILE_ENTRY.SPACE_CODE.eq(TARGET_SPACE))
                        .and(FILE_ENTRY.ENTRY_ID.eq(targetParentEntryId))
                        .execute();
                parentLocked.countDown();
                await(allowCommit);
            }));
            assertTrue(parentLocked.await(5, TimeUnit.SECONDS));

            // 事务二：跨空间迁移非空目录到该父目录，必须阻塞在目标父 entry 锁上
            Future<?> move = executor.submit(() -> {
                setOperator(SOURCE_SPACE);
                try {
                    entryRepo.moveAcrossSpace(movedDir.getFileId(), TARGET_SPACE, targetParent.getFileId());
                } catch (Throwable t) {
                    moveError.set(t);
                }
                moveFinished.countDown();
            });
            if(moveFinished.await(200, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("moveAcrossSpace did not block on target parent lock", moveError.get());
            }

            allowCommit.countDown();
            parentMove.get(5, TimeUnit.SECONDS);
            assertTrue(moveFinished.await(5, TimeUnit.SECONDS));
            move.get(5, TimeUnit.SECONDS);

            // 父目录已被迁走：迁移必须失败回滚，源子树保持完好，目标空间无残留
            Throwable error = moveError.get();
            assertNotNull(error);
            assertTrue(error instanceof IllegalStateException);
            assertTrue(error.getMessage().contains("target parent file_entry not found"));
            FileEntryDB sourceRoot = entryRepo.queryActiveByFileId(SOURCE_SPACE, movedDir.getFileId());
            assertNotNull(sourceRoot);
            FileEntryDB sourceChild = entryRepo.queryActiveByFileId(SOURCE_SPACE, child.getFileId());
            assertNotNull(sourceChild);
            assertTrue(sourceRoot.getEntryId().equals(sourceChild.getParentEntryId()));
            DSLContext targetDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE), dsl);
            assertTrue(targetDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.SPACE_CODE.eq(TARGET_SPACE)) == 0);
        } finally {
            allowCommit.countDown();
        }
    }

    @Test
    public void crossSpaceMoveWaitsForRootLockAndUsesRenamedFilename() throws Exception {
        FileDB movedDir = addDirectory(SOURCE_SPACE, "root-old-name", null, "root-lock-src");
        FileDB child = addFile(SOURCE_SPACE, "root-lock-child.txt", movedDir.getFileId(), "root-lock-child");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "root-lock-target", null, "root-lock-target");
        String rootEntryId = entryRepo.queryActiveByFileId(SOURCE_SPACE, movedDir.getFileId()).getEntryId();

        CountDownLatch rootLocked = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch moveFinished = new CountDownLatch(1);
        AtomicReference<Throwable> moveError = new AtomicReference<>();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            // 事务一：并发 rename 根节点，提交前挂起
            Future<?> rename = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                DSLContext sourceDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);
                sourceDsl.update(FILE_ENTRY)
                        .set(FILE_ENTRY.FILENAME, "root-new-name")
                        .where(FILE_ENTRY.SPACE_CODE.eq(SOURCE_SPACE))
                        .and(FILE_ENTRY.ENTRY_ID.eq(rootEntryId))
                        .execute();
                rootLocked.countDown();
                await(allowCommit);
            }));
            assertTrue(rootLocked.await(5, TimeUnit.SECONDS));

            // 事务二：跨空间迁移必须阻塞在根 entry 锁上，锁定后以最新 filename 为准
            Future<?> move = executor.submit(() -> {
                setOperator(SOURCE_SPACE);
                try {
                    entryRepo.moveAcrossSpace(movedDir.getFileId(), TARGET_SPACE, targetParent.getFileId());
                } catch (Throwable t) {
                    moveError.set(t);
                }
                moveFinished.countDown();
            });
            if(moveFinished.await(200, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("moveAcrossSpace did not block on source root lock", moveError.get());
            }

            allowCommit.countDown();
            rename.get(5, TimeUnit.SECONDS);
            assertTrue(moveFinished.await(5, TimeUnit.SECONDS));
            move.get(5, TimeUnit.SECONDS);

            assertNull(moveError.get());
            FileEntryDB movedRoot = entryRepo.queryActiveByFileId(TARGET_SPACE, movedDir.getFileId());
            assertNotNull(movedRoot);
            assertEquals("root-new-name", movedRoot.getFilename());
            FileEntryDB movedChild = entryRepo.queryActiveByFileId(TARGET_SPACE, child.getFileId());
            assertNotNull(movedChild);
            assertEquals(movedRoot.getEntryId(), movedChild.getParentEntryId());
        } finally {
            allowCommit.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if(!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void setOperator(String spaceCode) {
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
    }

    private FileDB addDirectory(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, 1, NodeType.DIRECTORY);
    }

    private FileDB addFile(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, 0, NodeType.FILE);
    }

    private FileDB add(String spaceCode, String filename, String ancestorId, String seed, int isDir, NodeType nodeType) {
        setOperator(spaceCode);
        String hash = String.valueOf(Math.abs(CustomStringUtils.hashCode(spaceCode)));
        String fileId = "file-260824000000" + String.format("%06d", Math.abs(seed.hashCode()) % 1000000) + "-" + hash
                + (isDir == 1 ? "-d" : "-u");
        dsl.execute("insert into file_" + FileRepo.getShardingKeyByFileIdStatic(fileId)
                        + " (file_id, filename, is_dir, node_type, space_code, meta_data, mtime) "
                        + "values (?, ?, ?, ?, ?, ?, timestamp '2026-08-24 00:00:00')",
                fileId, filename, isDir, nodeType.getValue(), spaceCode, "{}");
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename(filename);
        file.setIsDir(isDir);
        file.setNodeType(nodeType.getValue());
        entryRepo.addEntry(spaceCode, file, ancestorId);
        return file;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class TestConfig {
        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:fileEntryCrossSpaceLock;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
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
            configuration.setConnectionProvider(new DataSourceConnectionProvider(
                    new TransactionAwareDataSourceProxy(dataSource)));
            return new DefaultDSLContext(configuration);
        }

        @Bean
        public FileEntryRepo fileEntryRepo(DSLContext dslContext) {
            return new FileEntryRepo(dslContext);
        }
    }
}
