package com.ke.bella.files.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileRepoMoveTest {
    private static final String OLD_ROOT = "file-old-0-d";
    private static final String SOURCE = "file-source-0-d";
    private static final String CHILD = "file-child-0-d";
    private static final String LEAF = "file-leaf-0";
    private static final String NEW_ROOT = "file-new-root-0-d";
    private static final String TARGET = "file-target-0-d";

    private static Connection connection;
    private static DSLContext dsl;
    private static FileRepo fileRepo;

    @BeforeClass
    public static void setupConnection() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:fileRepoMove;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dsl = DSL.using(connection, SQLDialect.MYSQL);
        fileRepo = new FileRepo(dsl);
    }

    @Before
    public void setup() {
        dsl.execute("drop table if exists file_closure_0");
        dsl.execute("create table file_closure_0 ("
                + "id bigint auto_increment primary key,"
                + "ancestor_id varchar(255) not null,"
                + "descendant_id varchar(255) not null,"
                + "space_code varchar(128) not null default '',"
                + "depth bigint not null default 0,"
                + "root_depth bigint not null default -1,"
                + "cuid bigint not null default 0,"
                + "cu_name varchar(32) not null default '',"
                + "ctime timestamp not null default current_timestamp,"
                + "muid bigint not null default 0,"
                + "mu_name varchar(32) not null default '',"
                + "mtime timestamp not null default current_timestamp,"
                + "unique (ancestor_id, descendant_id))");
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-a").build());
        insertTree();
    }

    @AfterClass
    public static void tearDownConnection() throws Exception {
        connection.close();
    }

    @Test
    public void moveDirectoryPreservesSubtreeAndReplacesExternalAncestors() {
        fileRepo.moveFileClosures(SOURCE, TARGET);

        assertFalse(hasClosure(OLD_ROOT, SOURCE));
        assertFalse(hasClosure(OLD_ROOT, CHILD));
        assertFalse(hasClosure(OLD_ROOT, LEAF));

        assertClosure(SOURCE, CHILD, 1L, -1L);
        assertClosure(SOURCE, LEAF, 2L, -1L);
        assertClosure(CHILD, LEAF, 1L, -1L);

        assertClosure(TARGET, SOURCE, 1L, -1L);
        assertClosure(TARGET, CHILD, 2L, -1L);
        assertClosure(TARGET, LEAF, 3L, -1L);
        assertClosure(NEW_ROOT, SOURCE, 2L, -1L);
        assertClosure(NEW_ROOT, CHILD, 3L, -1L);
        assertClosure(NEW_ROOT, LEAF, 4L, -1L);

        assertClosure(SOURCE, SOURCE, 0L, 3L);
        assertClosure(CHILD, CHILD, 0L, 4L);
        assertClosure(LEAF, LEAF, 0L, 5L);
    }

    @Test
    public void moveLeafUsesSameSubtreeAlgorithm() {
        fileRepo.moveFileClosures(LEAF, TARGET);

        assertFalse(hasClosure(OLD_ROOT, LEAF));
        assertFalse(hasClosure(SOURCE, LEAF));
        assertFalse(hasClosure(CHILD, LEAF));
        assertClosure(TARGET, LEAF, 1L, -1L);
        assertClosure(NEW_ROOT, LEAF, 2L, -1L);
        assertClosure(LEAF, LEAF, 0L, 3L);
    }

    @Test
    public void failedBatchCanRollBackAllClosureChanges() throws Exception {
        insertClosure(NEW_ROOT, CHILD, 99L, -1L);
        Map<String, String> before = snapshot();

        connection.setAutoCommit(false);
        try {
            assertThrows(RuntimeException.class, () -> fileRepo.moveFileClosures(SOURCE, TARGET));
            connection.rollback();
            assertEquals(before, snapshot());
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Test
    public void movingToSelfOrDescendantDoesNotChangeClosures() {
        Map<String, String> before = snapshot();

        IllegalArgumentException selfError = assertThrows(IllegalArgumentException.class,
                () -> fileRepo.moveFileClosures(SOURCE, SOURCE));
        assertTrue(selfError.getMessage().contains("cannot move a directory"));
        assertEquals(before, snapshot());

        assertThrows(IllegalArgumentException.class, () -> fileRepo.moveFileClosures(SOURCE, CHILD));
        assertEquals(before, snapshot());

        assertThrows(IllegalArgumentException.class, () -> fileRepo.moveFileClosures(SOURCE, LEAF));
        assertEquals(before, snapshot());
    }

    private void insertTree() {
        insertClosure(OLD_ROOT, OLD_ROOT, 0L, 1L);
        insertClosure(SOURCE, SOURCE, 0L, 2L);
        insertClosure(OLD_ROOT, SOURCE, 1L, -1L);
        insertClosure(CHILD, CHILD, 0L, 3L);
        insertClosure(SOURCE, CHILD, 1L, -1L);
        insertClosure(OLD_ROOT, CHILD, 2L, -1L);
        insertClosure(LEAF, LEAF, 0L, 4L);
        insertClosure(CHILD, LEAF, 1L, -1L);
        insertClosure(SOURCE, LEAF, 2L, -1L);
        insertClosure(OLD_ROOT, LEAF, 3L, -1L);
        insertClosure(NEW_ROOT, NEW_ROOT, 0L, 1L);
        insertClosure(TARGET, TARGET, 0L, 2L);
        insertClosure(NEW_ROOT, TARGET, 1L, -1L);
    }

    private void insertClosure(String ancestorId, String descendantId, long depth, long rootDepth) {
        dsl.execute("insert into file_closure_0 "
                        + "(ancestor_id, descendant_id, space_code, depth, root_depth) values (?, ?, ?, ?, ?)",
                ancestorId, descendantId, "sp-a", depth, rootDepth);
    }

    private boolean hasClosure(String ancestorId, String descendantId) {
        return dsl.fetchExists(DSL.selectOne()
                .from(DSL.table("file_closure_0"))
                .where(DSL.field("ancestor_id").eq(ancestorId))
                .and(DSL.field("descendant_id").eq(descendantId)));
    }

    private void assertClosure(String ancestorId, String descendantId, long depth, long rootDepth) {
        Record record = dsl.fetchOne("select depth, root_depth from file_closure_0 where ancestor_id = ? and descendant_id = ?",
                ancestorId, descendantId);
        assertEquals(depth, record.get(0, Long.class).longValue());
        assertEquals(rootDepth, record.get(1, Long.class).longValue());
    }

    private Map<String, String> snapshot() {
        Result<Record> records = dsl.fetch("select ancestor_id, descendant_id, depth, root_depth "
                + "from file_closure_0 order by ancestor_id, descendant_id");
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (Record record : records) {
            String key = record.get(0, String.class) + "->" + record.get(1, String.class);
            snapshot.put(key, record.get(2, Long.class) + ":" + record.get(3, Long.class));
        }
        return snapshot;
    }
}
