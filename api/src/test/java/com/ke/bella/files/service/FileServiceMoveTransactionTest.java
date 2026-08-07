package com.ke.bella.files.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.ke.bella.files.FileShardingCountUpdator;
import com.ke.bella.files.configuration.BucketConfig;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.repo.FileRepoTestFixture;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.service.broadcast.BroadcastService;
import com.ke.bella.files.service.storage.StorageService;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = FileServiceMoveTransactionTest.TestConfig.class)
public class FileServiceMoveTransactionTest {
    private static final String OLD_ROOT = "file-old-1-d";
    private static final String SOURCE = "file-source-1-d";
    private static final String CHILD = "file-child-1-d";
    private static final String TARGET = "file-target-1-d";
    private static final String CREATED_FILE = "file-created-1-d";

    @javax.annotation.Resource
    private DSLContext dsl;
    @javax.annotation.Resource
    private FileService fileService;
    @javax.annotation.Resource
    private BroadcastService broadcastService;
    @javax.annotation.Resource
    private FileRepo fileRepo;
    @javax.annotation.Resource
    private PlatformTransactionManager transactionManager;

    @Before
    public void setup() {
        FileRepoTestFixture.recreateUserFileTables(dsl, "1");
        insertTree();
        insertFile();
        setOperator();
    }

    @Test
    public void updateFailureRollsBackClosuresAndSkipsLocationBroadcast() {
        Map<String, String> closuresBefore = snapshotClosures();
        String fileBefore = snapshotFile();

        RuntimeException error = assertThrows(RuntimeException.class, () -> fileService.moveFile(SOURCE, TARGET));

        assertTrue(error.getMessage().contains("simulated location update failure"));
        assertEquals(closuresBefore, snapshotClosures());
        assertEquals(fileBefore, snapshotFile());
        verifyNoInteractions(broadcastService);
    }

    @Test
    public void creatingInMovingSubtreeWaitsForNewAncestorChain() throws Exception {
        CountDownLatch moveCompleted = new CountDownLatch(1);
        CountDownLatch allowMoveCommit = new CountDownLatch(1);
        CountDownLatch createStarted = new CountDownLatch(1);
        CountDownLatch createCompleted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            Future<?> move = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                setOperator();
                fileRepo.moveFileClosures(SOURCE, TARGET);
                moveCompleted.countDown();
                await(allowMoveCommit);
            }));
            assertTrue(moveCompleted.await(5, TimeUnit.SECONDS));

            Future<?> create = executor.submit(() -> {
                setOperator();
                createStarted.countDown();
                fileRepo.addFileClosures(CREATED_FILE, CHILD);
                createCompleted.countDown();
            });
            assertTrue(createStarted.await(5, TimeUnit.SECONDS));
            assertFalse(createCompleted.await(200, TimeUnit.MILLISECONDS));

            allowMoveCommit.countDown();
            move.get(5, TimeUnit.SECONDS);
            create.get(5, TimeUnit.SECONDS);

            assertFalse(hasClosure(OLD_ROOT, CREATED_FILE));
            assertClosure(TARGET, CREATED_FILE, 3L);
            assertClosure(SOURCE, CREATED_FILE, 2L);
            assertClosure(CHILD, CREATED_FILE, 1L);
            assertClosure(CREATED_FILE, CREATED_FILE, 0L);
        } finally {
            allowMoveCommit.countDown();
            executor.shutdownNow();
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

    private static void setOperator() {
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
    }

    private void insertTree() {
        insertClosure(OLD_ROOT, OLD_ROOT, 0L, 1L);
        insertClosure(SOURCE, SOURCE, 0L, 2L);
        insertClosure(OLD_ROOT, SOURCE, 1L, -1L);
        insertClosure(CHILD, CHILD, 0L, 3L);
        insertClosure(SOURCE, CHILD, 1L, -1L);
        insertClosure(OLD_ROOT, CHILD, 2L, -1L);
        insertClosure(TARGET, TARGET, 0L, 1L);
    }

    private void insertFile() {
        dsl.execute("insert into file_1 (file_id, filename, is_dir, space_code, meta_data, mtime) "
                        + "values (?, ?, ?, ?, ?, timestamp '2026-01-01 00:00:00')",
                SOURCE, "source", 1, "sp-a", "{}");
    }

    private void insertClosure(String ancestorId, String descendantId, long depth, long rootDepth) {
        dsl.execute("insert into file_closure_1 "
                        + "(ancestor_id, descendant_id, space_code, depth, root_depth) values (?, ?, ?, ?, ?)",
                ancestorId, descendantId, "sp-a", depth, rootDepth);
    }

    private String snapshotFile() {
        Record record = dsl.fetchOne("select muid, mu_name, mtime from file_1 where file_id = ?", SOURCE);
        return record.get(0, Long.class) + ":" + record.get(1, String.class) + ":" + record.get(2).toString();
    }

    private Map<String, String> snapshotClosures() {
        Result<Record> records = dsl.fetch("select ancestor_id, descendant_id, depth, root_depth "
                + "from file_closure_1 order by ancestor_id, descendant_id");
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Record record : records) {
            String key = record.get(0, String.class) + "->" + record.get(1, String.class);
            snapshot.put(key, record.get(2, Long.class) + ":" + record.get(3, Long.class));
        }
        return snapshot;
    }

    private boolean hasClosure(String ancestorId, String descendantId) {
        Record record = dsl.fetchOne("select count(*) from file_closure_1 where ancestor_id = ? and descendant_id = ?",
                ancestorId, descendantId);
        return record.get(0, Number.class).intValue() > 0;
    }

    private void assertClosure(String ancestorId, String descendantId, long depth) {
        Record record = dsl.fetchOne("select depth from file_closure_1 where ancestor_id = ? and descendant_id = ?",
                ancestorId, descendantId);
        assertEquals(depth, record.get(0, Long.class).longValue());
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class TestConfig {
        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:fileServiceMove;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
        public FileRepo fileRepo(DSLContext dslContext) {
            return new FailingFileRepo(dslContext);
        }

        @Bean
        public BroadcastService broadcastService() {
            return mock(BroadcastService.class);
        }

        @Bean
        public StorageService storageService() {
            return mock(StorageService.class);
        }

        @Bean
        public BucketConfig bucketConfig() {
            return mock(BucketConfig.class);
        }

        @Bean
        public FileShardingCountUpdator fileShardingCountUpdator() {
            return mock(FileShardingCountUpdator.class);
        }

        @Bean
        public FileService fileService() {
            return new TestFileService();
        }
    }

    private static class FailingFileRepo extends FileRepo {
        FailingFileRepo(DSLContext dslContext) {
            super(dslContext);
        }

        @Override
        public void updateFile(FileOps op, boolean increaseVersion) {
            super.updateFile(op, increaseVersion);
            throw new IllegalStateException("simulated location update failure");
        }
    }

    private static class TestFileService extends FileService {
        @Override
        public void init() {
        }
    }
}
