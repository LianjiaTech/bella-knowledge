package com.ke.bella.files.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileRepoMoveTest {
    private static final String OLD_ROOT = "file-260824000000000001-0-d";
    private static final String SOURCE = "file-260824000000000002-0-d";
    private static final String CHILD = "file-260824000000000003-0-d";
    private static final String LEAF = "file-260824000000000004-0";
    private static final String NEW_ROOT = "file-260824000000000005-0-d";
    private static final String TARGET = "file-260824000000000006-0-d";
    private static final String PAGE_DIRECTORY = "file-260824000000000007-0-d";
    private static final String PAGE_FILE = "file-260824000000000008-0";
    private static final String PAGE_RESOURCE = "file-260824000000000009-0";

    private static Connection connection;
    private static DSLContext dsl;
    private static FileRepo fileRepo;
    private static FileEntryRepo entryRepo;

    @BeforeClass
    public static void setupConnection() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:fileRepoMove;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        dsl = DSL.using(connection, SQLDialect.H2);
        entryRepo = new FileEntryRepo(dsl);
        fileRepo = new FileRepo(dsl, entryRepo);
    }

    @Before
    public void setup() {
        FileRepoTestFixture.recreateUserFileTables(dsl, "0");
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode("sp-0").build());
        insertFiles();
        insertTree();
    }

    @AfterClass
    public static void tearDownConnection() throws Exception {
        connection.close();
    }

    @Test
    public void moveDirectoryUpdatesEntryParentAndPreservesSubtree() {
        fileRepo.moveFile(file(SOURCE), file(TARGET));

        assertEquals(entry(SOURCE).getEntryId(), entry(CHILD).getParentEntryId());
        assertEquals(entry(CHILD).getEntryId(), entry(LEAF).getParentEntryId());
        assertEquals(entry(TARGET).getEntryId(), entry(SOURCE).getParentEntryId());
        assertEquals("sp-0", fileRepo.queryFile(SOURCE).getSpaceCode());

        List<String> pathIds = fileRepo.getPathFiles(LEAF).stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(NEW_ROOT, TARGET, SOURCE, CHILD, LEAF), pathIds);
    }

    @Test
    public void movedSubtreeIsVisibleThroughEntryHierarchyQueries() {
        fileRepo.moveFile(file(SOURCE), file(TARGET));

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
    public void movingDirectoryIntoItsDescendantLeavesEntryTreeUnchanged() {
        String originalParentEntryId = entry(SOURCE).getParentEntryId();

        assertThrows(IllegalArgumentException.class, () -> fileRepo.moveFile(file(SOURCE), file(CHILD)));

        assertEquals(originalParentEntryId, entry(SOURCE).getParentEntryId());
        assertEquals(entry(SOURCE).getEntryId(), entry(CHILD).getParentEntryId());
        assertEquals(entry(CHILD).getEntryId(), entry(LEAF).getParentEntryId());
    }

    @Test
    public void pageFilesUsesEntryNodesForDirectoryFileAndResourceFilters() {
        insertPageNode(PAGE_DIRECTORY, "page-dir", 1, NodeType.FILE, null, FileEntryRepo.TYPE_DIR);
        insertPageNode(PAGE_FILE, "page-file.txt", 0, NodeType.FILE, null, FileEntryRepo.TYPE_FILE);
        insertPageNode(PAGE_RESOURCE, "page-resource", 0, NodeType.RESOURCE, "resource-0", FileEntryRepo.TYPE_RESOURCE);

        assertSinglePageNode("dir", PAGE_DIRECTORY, NodeType.FILE);
        assertSinglePageNode("file", PAGE_FILE, NodeType.FILE);
        assertSinglePageNode("resource", PAGE_RESOURCE, NodeType.RESOURCE);
    }

    private FileDB file(String fileId) {
        return fileRepo.queryFile(fileId);
    }

    private FileEntryDB entry(String fileId) {
        FileEntryDB entry = entryRepo.queryActiveByFileId("sp-0", fileId);
        assertNotNull(entry);
        return entry;
    }

    private void insertTree() {
        insertEntry(OLD_ROOT, null, "old-root", true);
        insertEntry(SOURCE, OLD_ROOT, "source", true);
        insertEntry(CHILD, SOURCE, "child", true);
        insertEntry(LEAF, CHILD, "leaf.txt", false);
        insertEntry(NEW_ROOT, null, "new-root", true);
        insertEntry(TARGET, NEW_ROOT, "target", true);
    }

    private void insertFiles() {
        insertFile(OLD_ROOT, "old-root", true);
        insertFile(SOURCE, "source", true);
        insertFile(CHILD, "child", true);
        insertFile(LEAF, "leaf.txt", false);
        insertFile(NEW_ROOT, "new-root", true);
        insertFile(TARGET, "target", true);
    }

    private void insertEntry(String fileId, String parentFileId, String filename, boolean directory) {
        dsl.execute("insert into file_entry_0 (entry_id, space_code, parent_entry_id, file_id, filename, type, "
                        + "cuid, cu_name, ctime, muid, mu_name, mtime) values (?, ?, ?, ?, ?, ?, 1, 'tester', "
                        + "timestamp '2026-01-01 00:00:00', 1, 'tester', timestamp '2026-01-01 00:00:00')",
                "e-" + fileId, "sp-0", parentFileId == null ? "" : "e-" + parentFileId, fileId, filename,
                directory ? FileEntryRepo.TYPE_DIR : FileEntryRepo.TYPE_FILE);
    }

    private void insertFile(String fileId, String filename, boolean directory) {
        dsl.execute("insert into file_0 (file_id, filename, is_dir, space_code, meta_data) values (?, ?, ?, ?, ?)",
                fileId, filename, directory ? 1 : 0, "sp-0", "{}");
    }

    private void insertPageNode(String fileId, String filename, int isDir, NodeType nodeType, String resourceId,
            String entryType) {
        dsl.execute("insert into file_0 (file_id, filename, is_dir, node_type, resource_id, space_code, meta_data) "
                        + "values (?, ?, ?, ?, ?, ?, ?)",
                fileId, filename, isDir, nodeType.getValue(), resourceId == null ? "" : resourceId, "sp-0", "{}");
        insertEntry(fileId, TARGET, filename, FileEntryRepo.TYPE_DIR.equals(entryType));
        if(!FileEntryRepo.TYPE_DIR.equals(entryType)) {
            dsl.execute("update file_entry_0 set type = ? where file_id = ?", entryType, fileId);
        }
    }

    private void assertSinglePageNode(String type, String expectedFileId, NodeType expectedNodeType) {
        Page<FileDB> page = fileRepo.pageFiles(PageFileOps.builder()
                .ancestorId(TARGET)
                .type(type)
                .page(1)
                .pageSize(10)
                .order("asc")
                .build());
        assertEquals(1, page.getTotal());
        assertEquals(1, page.getData().size());
        assertEquals(expectedFileId, page.getData().get(0).getFileId());
        assertEquals(expectedNodeType.getValue(), page.getData().get(0).getNodeType());
    }
}
