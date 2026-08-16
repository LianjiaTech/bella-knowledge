package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.INSTANCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.jooq.impl.DefaultExecuteListener;
import org.jooq.impl.DefaultExecuteListenerProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ke.bella.files.db.tables.records.InstanceRecord;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = InstanceRepoTest.TestConfig.class)
public class InstanceRepoTest {
    @javax.annotation.Resource
    private DSLContext db;

    @javax.annotation.Resource
    private InstanceRepo instanceRepo;

    @javax.annotation.Resource
    private BlockingIdleSelectListener blockingIdleSelectListener;

    @Before
    public void setup() {
        blockingIdleSelectListener.reset();
        db.execute("drop table if exists instance");
        db.execute("create table instance ("
                + "id bigint auto_increment primary key, "
                + "ip varchar(64) not null default '', "
                + "port int not null default 0, "
                + "status int not null default 0, "
                + "ctime timestamp not null default current_timestamp, "
                + "mtime timestamp not null default current_timestamp)");
    }

    @Test
    public void registerCreatesInstanceWhenNoIdleRecordExists() {
        Long id = instanceRepo.register("10.0.0.1", 8080);

        InstanceRecord record = db.selectFrom(INSTANCE).where(INSTANCE.ID.eq(id)).fetchOne();
        assertEquals("10.0.0.1", record.getIp());
        assertEquals(Integer.valueOf(8080), record.getPort());
        assertEquals(Integer.valueOf(1), record.getStatus());
        assertEquals(1, db.fetchCount(INSTANCE));
    }

    @Test
    public void registerReusesExistingEndpoint() {
        insertInstance(1L, "10.0.0.2", 8081, 0);

        Long id = instanceRepo.register("10.0.0.2", 8081);

        assertEquals(Long.valueOf(1L), id);
        assertEquals(Integer.valueOf(1), db.selectFrom(INSTANCE)
                .where(INSTANCE.ID.eq(id)).fetchOne().getStatus());
        assertEquals(1, db.fetchCount(INSTANCE));
    }

    @Test
    public void concurrentRegistrationsClaimDifferentIdleRecords() throws Exception {
        insertInstance(1L, "", 0, 0);
        insertInstance(2L, "", 0, 0);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(() -> {
                Thread.currentThread().setName(BlockingIdleSelectListener.FIRST_REGISTRATION_THREAD);
                return instanceRepo.register("10.0.0.3", 8082);
            });
            assertTrue(blockingIdleSelectListener.awaitFirstSelection());

            Future<Long> second = executor.submit(() -> instanceRepo.register("10.0.0.4", 8083));
            try {
                second.get(200, TimeUnit.MILLISECONDS);
                fail("the second registration should wait for the locked idle record");
            } catch(TimeoutException expected) {
            }

            blockingIdleSelectListener.releaseFirstRegistration();
            Long firstId = first.get(2, TimeUnit.SECONDS);
            Long secondId = second.get(2, TimeUnit.SECONDS);

            assertNotEquals(firstId, secondId);
            assertEquals(Integer.valueOf(1), db.selectFrom(INSTANCE)
                    .where(INSTANCE.ID.eq(firstId)).fetchOne().getStatus());
            assertEquals(Integer.valueOf(1), db.selectFrom(INSTANCE)
                    .where(INSTANCE.ID.eq(secondId)).fetchOne().getStatus());
        } finally {
            blockingIdleSelectListener.releaseFirstRegistration();
            executor.shutdownNow();
        }
    }

    private void insertInstance(Long id, String ip, int port, int status) {
        db.insertInto(INSTANCE)
                .set(INSTANCE.ID, id)
                .set(INSTANCE.IP, ip)
                .set(INSTANCE.PORT, port)
                .set(INSTANCE.STATUS, status)
                .execute();
    }

    public static class BlockingIdleSelectListener extends DefaultExecuteListener {
        private static final String FIRST_REGISTRATION_THREAD = "first-instance-registration";
        private CountDownLatch firstSelection;
        private CountDownLatch releaseFirst;

        public void reset() {
            firstSelection = new CountDownLatch(1);
            releaseFirst = new CountDownLatch(1);
        }

        public boolean awaitFirstSelection() throws InterruptedException {
            return firstSelection.await(2, TimeUnit.SECONDS);
        }

        public void releaseFirstRegistration() {
            releaseFirst.countDown();
        }

        @Override
        public void fetchEnd(ExecuteContext context) {
            if(FIRST_REGISTRATION_THREAD.equals(Thread.currentThread().getName())
                    && context.sql() != null
                    && context.sql().toLowerCase().contains("for update")) {
                firstSelection.countDown();
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    public static class TestConfig {
        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:instanceRepo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            return dataSource;
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public BlockingIdleSelectListener blockingIdleSelectListener() {
            return new BlockingIdleSelectListener();
        }

        @Bean
        public DSLContext dslContext(DataSource dataSource, BlockingIdleSelectListener listener) {
            DefaultConfiguration configuration = new DefaultConfiguration();
            configuration.setSQLDialect(SQLDialect.H2);
            configuration.setConnectionProvider(new org.jooq.impl.DataSourceConnectionProvider(
                    new TransactionAwareDataSourceProxy(dataSource)));
            configuration.set(new DefaultExecuteListenerProvider(listener));
            return new DefaultDSLContext(configuration);
        }

        @Bean
        public InstanceRepo instanceRepo(DSLContext db) {
            InstanceRepo repo = new InstanceRepo();
            ReflectionTestUtils.setField(repo, "db", db);
            return repo;
        }
    }
}
