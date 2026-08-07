package com.ke.bella.files.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.protocol.PageFileOps;
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
        dsl.execute("drop table if exists file_0");
        createFileClosureTable();
        createFileTable();
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-0").build());
        insertTree();
        insertFiles();
    }

    private void createFileClosureTable() {
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
    }

    private void createFileTable() {
        dsl.execute("create table file_0 ("
                + "id bigint auto_increment primary key,"
                + "file_id varchar(256) not null,"
                + "version bigint not null default 0,"
                + "filename varchar(512) not null default '',"
                + "is_dir int not null default 0,"
                + "extension varchar(512) not null default '',"
                + "mime_type varchar(512) not null default '',"
                + "type varchar(512) not null default '',"
                + "bucket varchar(256) not null default '',"
                + "path varchar(512) not null default '',"
                + "bytes bigint not null default 0,"
                + "space_code varchar(128) not null default '',"
                + "purpose varchar(64) not null default '',"
                + "cuid bigint not null default 0,"
                + "cu_name varchar(32) not null default '',"
                + "ctime timestamp not null default current_timestamp,"
                + "muid bigint not null default 0,"
                + "mu_name varchar(32) not null default '',"
                + "mtime timestamp not null default current_timestamp,"
                + "meta_data clob,"
                + "status int not null default 0,"
                + "ak_code varchar(128) not null default '',"
                + "broadcast_status bigint not null default 0,"
                + "dom_tree_file_id varchar(256) not null default '',"
                + "pdf_file_id varchar(256) not null default '',"
                + "description varchar(256) not null default '',"
                + "cities varchar(512) not null default '',"
                + "tags varchar(512) not null default '',"
                + "unique (file_id, space_code))");
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
    public void movedSubtreeIsVisibleThroughHierarchyQueries() {
        fileRepo.moveFileClosures(SOURCE, TARGET);

        List<String> pathIds = fileRepo.getPathFiles(LEAF).stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(NEW_ROOT, TARGET, SOURCE, CHILD, LEAF), pathIds);

        Map<String, List<String>> ancestorIds = fileRepo.getFileAncestorIds("sp-0", Collections.singletonList(LEAF));
        assertEquals(Arrays.asList(NEW_ROOT, TARGET, SOURCE, CHILD), ancestorIds.get(LEAF));

        List<String> targetChildren = fileRepo.findFiles("sp-0", TARGET).stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
        assertEquals(Collections.singletonList(SOURCE), targetChildren);

        Page<FileDB> sourcePage = fileRepo.pageFiles(PageFileOps.builder()
                .ancestorId(SOURCE)
                .page(1)
                .pageSize(10)
                .order("asc")
                .build());
        assertEquals(1, sourcePage.getTotal());
        assertEquals(CHILD, sourcePage.getData().get(0).getFileId());
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

    private void insertFiles() {
        insertFile(OLD_ROOT, "old-root", true);
        insertFile(SOURCE, "source", true);
        insertFile(CHILD, "child", true);
        insertFile(LEAF, "leaf.txt", false);
        insertFile(NEW_ROOT, "new-root", true);
        insertFile(TARGET, "target", true);
    }

    private void insertFile(String fileId, String filename, boolean directory) {
        dsl.execute("insert into file_0 (file_id, filename, is_dir, space_code, meta_data) values (?, ?, ?, ?, ?)",
                fileId, filename, directory ? 1 : 0, "sp-0", "{}");
    }

    private void insertClosure(String ancestorId, String descendantId, long depth, long rootDepth) {
        dsl.execute("insert into file_closure_0 "
                        + "(ancestor_id, descendant_id, space_code, depth, root_depth) values (?, ?, ?, ?, ?)",
                ancestorId, descendantId, "sp-0", depth, rootDepth);
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
