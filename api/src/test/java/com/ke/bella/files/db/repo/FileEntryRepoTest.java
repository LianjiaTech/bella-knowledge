package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.Before;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.FileNodeCount;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileEntryRepoTest {
    private static final String SOURCE_SPACE = "sp-a";
    private static final String TARGET_SPACE = "sp-b";
    private static final String SAME_SHARD_SPACE = "sp-collision-17";

    private static Connection connection;
    private static DSLContext dsl;
    private FileRepo fileRepo;
    private FileEntryRepo entryRepo;

    @BeforeClass
    public static void setupConnection() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:fileEntry;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        dsl = DSL.using(connection, SQLDialect.H2);
    }

    @Before
    public void setup() {
        String sourceShard = FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE);
        String targetShard = FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE);
        assertNotEquals(sourceShard, targetShard);
        FileRepoTestFixture.recreateUserFileTables(dsl, sourceShard);
        FileRepoTestFixture.recreateUserFileTables(dsl, targetShard);
        IDGenerator.setInstanceId(1L);
        entryRepo = new FileEntryRepo(dsl);
        fileRepo = new FileRepo(dsl, entryRepo);
        setOperator(SOURCE_SPACE);
    }

    @AfterClass
    public static void tearDownConnection() throws Exception {
        connection.close();
    }

    @Test
    public void createMoveRenameDeleteAndRecreateKeepEntryConsistent() {
        FileDB firstParent = addDirectory(SOURCE_SPACE, "parent-a", null, "parent-a");
        FileDB secondParent = addDirectory(SOURCE_SPACE, "parent-b", null, "parent-b");
        FileDB file = addFile(SOURCE_SPACE, "report.txt", firstParent.getFileId(), "report");

        FileEntryDB original = entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId());
        assertNotNull(original);
        assertEquals(entryRepo.queryActiveByFileId(SOURCE_SPACE, firstParent.getFileId()).getEntryId(), original.getParentEntryId());
        assertTrue(entryRepo.exists(SOURCE_SPACE, firstParent.getFileId(), "report.txt"));
        // dual 模式下 entry 写不再降级：重名冲突直接失败
        assertThrows(IllegalStateException.class,
                () -> addFile(SOURCE_SPACE, "report.txt", firstParent.getFileId(), "duplicate"));

        fileRepo.moveFile(file, secondParent);
        FileEntryDB moved = entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId());
        assertEquals(original.getEntryId(), moved.getEntryId());
        assertEquals(entryRepo.queryActiveByFileId(SOURCE_SPACE, secondParent.getFileId()).getEntryId(), moved.getParentEntryId());

        fileRepo.updateFile(FileOps.builder().fileId(file.getFileId()).filename("renamed.txt").build());
        assertEquals("renamed.txt", entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId()).getFilename());

        fileRepo.updateFile(FileOps.builder().fileId(file.getFileId()).status(FileStatus.DELETED).build());
        fileRepo.deleteFileClosure(file.getFileId(), FileType.USER);
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId()));
        assertEquals(0, DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl)
                .fetchCount(FILE_ENTRY, FILE_ENTRY.FILE_ID.eq(file.getFileId())));
        FileDB recreated = addFile(SOURCE_SPACE, "renamed.txt", secondParent.getFileId(), "recreated");
        assertNotEquals(original.getEntryId(), entryRepo.queryActiveByFileId(SOURCE_SPACE, recreated.getFileId()).getEntryId());
    }

    @Test
    public void renameAndMoveOnlyUpdateMatchingSpaceWhenEntryIdsCollide() {
        assertEquals(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), FileRepo.getShardingKeyBySpaceCode(SAME_SHARD_SPACE));
        FileDB firstParent = addDirectory(SOURCE_SPACE, "parent-a", null, "same-shard-source-parent-a");
        FileDB secondParent = addDirectory(SOURCE_SPACE, "parent-b", null, "same-shard-source-parent-b");
        FileDB sourceFile = addFile(SOURCE_SPACE, "source.txt", firstParent.getFileId(), "same-shard-source-file");
        FileDB otherParent = addDirectory(SAME_SHARD_SPACE, "other-parent", null, "same-shard-other-parent");
        FileDB otherFile = addFile(SAME_SHARD_SPACE, "other.txt", otherParent.getFileId(), "same-shard-other-file");

        FileEntryDB sourceEntry = entryRepo.queryActiveByFileId(SOURCE_SPACE, sourceFile.getFileId());
        FileEntryDB otherEntry = entryRepo.queryActiveByFileId(SAME_SHARD_SPACE, otherFile.getFileId());
        DSLContext shardDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);
        assertEquals(1, shardDsl.update(FILE_ENTRY)
                .set(FILE_ENTRY.ENTRY_ID, sourceEntry.getEntryId())
                .where(FILE_ENTRY.SPACE_CODE.eq(SAME_SHARD_SPACE))
                .and(FILE_ENTRY.ENTRY_ID.eq(otherEntry.getEntryId()))
                .execute());

        entryRepo.rename(SOURCE_SPACE, sourceFile.getFileId(), "renamed.txt");
        assertEquals("renamed.txt", entryRepo.queryActiveByFileId(SOURCE_SPACE, sourceFile.getFileId()).getFilename());
        FileEntryDB unchangedOther = entryRepo.queryActiveByFileId(SAME_SHARD_SPACE, otherFile.getFileId());
        assertEquals("other.txt", unchangedOther.getFilename());

        entryRepo.move(SOURCE_SPACE, sourceFile.getFileId(), secondParent.getFileId());
        assertEquals(entryRepo.queryActiveByFileId(SOURCE_SPACE, secondParent.getFileId()).getEntryId(),
                entryRepo.queryActiveByFileId(SOURCE_SPACE, sourceFile.getFileId()).getParentEntryId());
        assertEquals(otherEntry.getParentEntryId(),
                entryRepo.queryActiveByFileId(SAME_SHARD_SPACE, otherFile.getFileId()).getParentEntryId());
    }

    @Test
    public void crossSpaceMoveRebuildsEntryAndClosureWithoutMovingFileRow() {
        FileDB source = addFile(SOURCE_SPACE, "cross.txt", null, "cross");
        FileDB movedDirectory = addDirectory(SOURCE_SPACE, "moved-dir", null, "moved-dir");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "target", null, "target");
        String sourceEntryId = entryRepo.queryActiveByFileId(SOURCE_SPACE, source.getFileId()).getEntryId();

        FileEntryDB target = entryRepo.moveAcrossSpace(source, TARGET_SPACE, targetParent);
        entryRepo.moveAcrossSpace(movedDirectory, TARGET_SPACE, targetParent);
        FileDB child = addFile(TARGET_SPACE, "child.txt", movedDirectory.getFileId(), "child");

        assertNotEquals(sourceEntryId, target.getEntryId());
        assertEquals(entryRepo.queryActiveByFileId(TARGET_SPACE, targetParent.getFileId()).getEntryId(), target.getParentEntryId());
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, source.getFileId()));
        assertEquals(0, DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl)
                .fetchCount(FILE_ENTRY, FILE_ENTRY.FILE_ID.eq(source.getFileId())));
        FileDB unchanged = fileRepo.queryFile(source.getFileId());
        assertEquals(TARGET_SPACE, unchanged.getSpaceCode());
        assertEquals(source.getBucket(), unchanged.getBucket());
        assertEquals(source.getPath(), unchanged.getPath());
        assertFalse(hasClosure(SOURCE_SPACE, source.getFileId(), source.getFileId()));
        assertTrue(hasClosure(TARGET_SPACE, targetParent.getFileId(), source.getFileId()));

        fileRepo.setFileEntryReadMode("entry");
        Page<FileDB> targetPage = fileRepo.pageFiles(PageFileOps.builder()
                .spaceCode(TARGET_SPACE)
                .ancestorId(targetParent.getFileId())
                .page(1)
                .pageSize(10)
                .build());
        assertEquals(2, targetPage.getTotal());
        assertTrue(targetPage.getData().stream().anyMatch(file -> source.getFileId().equals(file.getFileId())));
        assertTrue(targetPage.getData().stream().anyMatch(file -> movedDirectory.getFileId().equals(file.getFileId())));

        List<FileDB> targetList = fileRepo.listFile(null, 10, "asc", null, TARGET_SPACE, targetParent.getFileId());
        assertTrue(targetList.stream().anyMatch(file -> source.getFileId().equals(file.getFileId())));
        assertTrue(targetList.stream().anyMatch(file -> movedDirectory.getFileId().equals(file.getFileId())));

        Page<FileDB> movedDirectoryPage = fileRepo.pageFiles(PageFileOps.builder()
                .ancestorId(movedDirectory.getFileId())
                .page(1)
                .pageSize(10)
                .build());
        assertEquals(1, movedDirectoryPage.getTotal());
        assertEquals(child.getFileId(), movedDirectoryPage.getData().get(0).getFileId());
    }

    @Test
    public void crossSpaceMoveMigratesNonEmptyDirectorySubtreeInEntryMode() {
        entryRepo.setFileEntryWriteMode("entry");
        fileRepo.setFileEntryReadMode("entry");

        FileDB root = addDirectory(SOURCE_SPACE, "tree-root", null, "tree-root");
        FileDB fileA = addFile(SOURCE_SPACE, "tree-a.txt", root.getFileId(), "tree-a");
        FileDB sub = addDirectory(SOURCE_SPACE, "tree-sub", root.getFileId(), "tree-sub");
        FileDB fileB = addFile(SOURCE_SPACE, "tree-b.txt", sub.getFileId(), "tree-b");
        FileDB deep = addDirectory(SOURCE_SPACE, "tree-deep", sub.getFileId(), "tree-deep");
        FileDB fileC = addFile(SOURCE_SPACE, "tree-c.txt", deep.getFileId(), "tree-c");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "tree-target", null, "tree-target");

        List<FileDB> subtree = Arrays.asList(root, fileA, sub, fileB, deep, fileC);
        Map<String, FileEntryDB> before = subtree.stream()
                .collect(Collectors.toMap(FileDB::getFileId, file -> entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId())));

        FileEntryDB moved = entryRepo.moveAcrossSpace(root, TARGET_SPACE, targetParent);

        // 根 entry 保留 entry_id，仅 space_code 与 parent_entry_id 改写
        assertEquals(before.get(root.getFileId()).getEntryId(), moved.getEntryId());
        assertEquals(TARGET_SPACE, moved.getSpaceCode());
        assertEquals(entryRepo.queryActiveByFileId(TARGET_SPACE, targetParent.getFileId()).getEntryId(), moved.getParentEntryId());
        // 子孙 entry 全部迁到目标空间，entry_id 与父子关系原样保留
        for (FileDB node : subtree) {
            FileEntryDB migrated = entryRepo.queryActiveByFileId(TARGET_SPACE, node.getFileId());
            assertNotNull(migrated);
            assertEquals(before.get(node.getFileId()).getEntryId(), migrated.getEntryId());
            if(node != root) {
                assertEquals(before.get(node.getFileId()).getParentEntryId(), migrated.getParentEntryId());
            }
            // file 物理行不迁移：file_id、bucket、path 不变，space_code 缓存指向目标空间
            FileDB unchanged = fileRepo.queryFile(node.getFileId());
            assertEquals(TARGET_SPACE, unchanged.getSpaceCode());
            assertEquals(node.getBucket(), unchanged.getBucket());
            assertEquals(node.getPath(), unchanged.getPath());
        }
        // 源空间分片不残留任何子树 entry
        DSLContext sourceDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);
        assertEquals(0, sourceDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.SPACE_CODE.eq(SOURCE_SPACE)));

        // 迁移后双空间读取正确性
        FileNodeCount sourceCount = entryRepo.countNodes(SOURCE_SPACE, null);
        assertEquals(0L, sourceCount.getFileCount());
        assertEquals(0L, sourceCount.getDirectoryCount());
        assertTrue(fileRepo.listFile(null, 10, "asc", null, SOURCE_SPACE, null).isEmpty());
        List<String> targetChildren = fileRepo.listFile(null, 10, "asc", null, TARGET_SPACE, targetParent.getFileId()).stream()
                .map(FileDB::getFileId).collect(Collectors.toList());
        assertEquals(Collections.singletonList(root.getFileId()), targetChildren);
        assertEquals(Arrays.asList(targetParent.getFileId(), root.getFileId(), sub.getFileId(), deep.getFileId(), fileC.getFileId()),
                fileRepo.getPathFiles(fileC.getFileId()).stream().map(FileDB::getFileId).collect(Collectors.toList()));
        Page<FileDB> subPage = fileRepo.pageFiles(PageFileOps.builder()
                .spaceCode(TARGET_SPACE).ancestorId(sub.getFileId()).page(1).pageSize(10).build());
        assertEquals(2, subPage.getTotal());
    }

    @Test
    public void crossSpaceMoveOfNonEmptyDirectoryExceedingWarnThresholdStillSucceeds() {
        entryRepo.setFileEntryWriteMode("entry");
        entryRepo.setCrossSpaceMoveWarnThreshold(1);

        FileDB root = addDirectory(SOURCE_SPACE, "warn-root", null, "warn-root");
        addFile(SOURCE_SPACE, "warn-a.txt", root.getFileId(), "warn-a");
        addFile(SOURCE_SPACE, "warn-b.txt", root.getFileId(), "warn-b");

        // 超阈值只告警不拒绝
        FileEntryDB moved = entryRepo.moveAcrossSpace(root, TARGET_SPACE, null);
        assertEquals(TARGET_SPACE, moved.getSpaceCode());
        assertEquals(2L, entryRepo.countNodes(TARGET_SPACE, root.getFileId()).getFileCount());
    }

    @Test
    public void crossSpaceMoveOfNonEmptyDirectoryRequiresEntryWriteMode() {
        FileDB root = addDirectory(SOURCE_SPACE, "dual-root", null, "dual-root");
        addFile(SOURCE_SPACE, "dual-a.txt", root.getFileId(), "dual-a");

        // dual 模式闭包仍在写，非空目录不随迁闭包，必须拒绝；源目录保持完好
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> entryRepo.moveAcrossSpace(root, TARGET_SPACE, null));
        assertTrue(error.getMessage().contains("requires write-mode entry"));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, root.getFileId()));
        assertEquals(SOURCE_SPACE, fileRepo.queryFile(root.getFileId()).getSpaceCode());
    }

    @Test
    public void crossSpaceMoveRejectsTargetNameConflictBeforeAnyWrite() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB root = addDirectory(SOURCE_SPACE, "conflict-dir", null, "conflict-src");
        FileDB child = addFile(SOURCE_SPACE, "conflict-a.txt", root.getFileId(), "conflict-a");
        setOperator(TARGET_SPACE);
        addDirectory(TARGET_SPACE, "conflict-dir", null, "conflict-dst");

        assertThrows(IllegalStateException.class,
                () -> entryRepo.moveAcrossSpace(root, TARGET_SPACE, null));
        // 校验先于任何写入：源子树完好，目标空间无子树残留
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, root.getFileId()));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, child.getFileId()));
        assertNull(entryRepo.queryActiveByFileId(TARGET_SPACE, child.getFileId()));
        assertEquals(SOURCE_SPACE, fileRepo.queryFile(root.getFileId()).getSpaceCode());
    }

    @Test
    public void crossSpaceMoveWithinSameSpaceDelegatesToPlainMove() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB root = addDirectory(SOURCE_SPACE, "same-root", null, "same-root");
        FileDB child = addFile(SOURCE_SPACE, "same-a.txt", root.getFileId(), "same-a");
        FileDB sibling = addDirectory(SOURCE_SPACE, "same-target", null, "same-target");

        // 目标空间与源空间相同：退化为只改根 parent_entry_id，子树不搬迁
        FileEntryDB before = entryRepo.queryActiveByFileId(SOURCE_SPACE, root.getFileId());
        FileEntryDB moved = entryRepo.moveAcrossSpace(root, SOURCE_SPACE, sibling);
        assertEquals(before.getEntryId(), moved.getEntryId());
        assertEquals(entryRepo.queryActiveByFileId(SOURCE_SPACE, sibling.getFileId()).getEntryId(), moved.getParentEntryId());
        FileEntryDB childEntry = entryRepo.queryActiveByFileId(SOURCE_SPACE, child.getFileId());
        assertEquals(before.getEntryId(), childEntry.getParentEntryId());
    }

    @Test
    public void crossSpaceMoveHandlesSubtreeAtMaxDepthAndRejectsBeyond() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB root = addDirectory(SOURCE_SPACE, "depth-root", null, "depth-root");
        FileDB deepest = root;
        for (int level = 1; level <= FileEntryRepo.MAX_TREE_DEPTH; level++) {
            deepest = addDirectory(SOURCE_SPACE, "d-" + level, deepest.getFileId(), "depth-" + level);
        }

        // 恰好 MAX_TREE_DEPTH 层且最深节点是空目录：确认无子节点的查询不计入深度，迁到空间根成功
        assertNotNull(entryRepo.moveAcrossSpace(root, TARGET_SPACE, null));
        assertNotNull(entryRepo.queryActiveByFileId(TARGET_SPACE, deepest.getFileId()));

        // 超过 MAX_TREE_DEPTH 层的子树拒绝迁移
        FileDB overRoot = addDirectory(SOURCE_SPACE, "over-root", null, "over-root");
        FileDB overDeepest = overRoot;
        for (int level = 1; level <= FileEntryRepo.MAX_TREE_DEPTH + 1; level++) {
            overDeepest = addDirectory(SOURCE_SPACE, "o-" + level, overDeepest.getFileId(), "over-" + level);
        }
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> entryRepo.moveAcrossSpace(overRoot, TARGET_SPACE, null));
        assertTrue(error.getMessage().contains("exceeds max depth"));
    }

    @Test
    public void crossSpaceMoveAccountsForTargetParentDepth() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB targetDeepest = null;
        for (int level = 1; level <= FileEntryRepo.MAX_TREE_DEPTH; level++) {
            targetDeepest = addDirectory(TARGET_SPACE, "tp-" + level,
                    targetDeepest == null ? null : targetDeepest.getFileId(), "tp-" + level);
        }
        FileDB targetParent = targetDeepest;

        // 子树自身 1 层，挂到 MAX_TREE_DEPTH 层深的目标父下会产生超限深层节点：拒绝且无任何写入
        FileDB comboRoot = addDirectory(SOURCE_SPACE, "combo-root", null, "combo-root");
        FileDB comboChild = addFile(SOURCE_SPACE, "combo-child.txt", comboRoot.getFileId(), "combo-child");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> entryRepo.moveAcrossSpace(comboRoot, TARGET_SPACE, targetParent));
        assertTrue(error.getMessage().contains("exceeds max tree depth"));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, comboRoot.getFileId()));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, comboChild.getFileId()));
        assertNull(entryRepo.queryActiveByFileId(TARGET_SPACE, comboRoot.getFileId()));
        assertEquals(SOURCE_SPACE, fileRepo.queryFile(comboRoot.getFileId()).getSpaceCode());

        // 叶子（子树 0 层）挂到同一目标父下恰好触及上限：允许，且迁移后完整路径仍可读
        FileDB leaf = addFile(SOURCE_SPACE, "combo-leaf.txt", null, "combo-leaf");
        assertNotNull(entryRepo.moveAcrossSpace(leaf, TARGET_SPACE, targetParent));
        List<FileDB> path = entryRepo.pathFiles(TARGET_SPACE, leaf.getFileId());
        assertEquals(FileEntryRepo.MAX_TREE_DEPTH + 1, path.size());
        assertEquals(leaf.getFileId(), path.get(path.size() - 1).getFileId());
    }

    @Test
    public void crossSpaceMoveChunksAllInListsAndStaysConsistent() {
        entryRepo.setFileEntryWriteMode("entry");
        // 块大小压到 2，强制 BFS、insert-select、delete 和缓存刷新全部走分块路径
        entryRepo.setSqlInChunkSize(2);
        FileDB root = addDirectory(SOURCE_SPACE, "chunk-root", null, "chunk-root");
        for (int index = 0; index < 3; index++) {
            FileDB dir = addDirectory(SOURCE_SPACE, "chunk-dir-" + index, root.getFileId(), "chunk-dir-" + index);
            addFile(SOURCE_SPACE, "chunk-file-" + index + ".txt", dir.getFileId(), "chunk-file-" + index);
        }
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "chunk-target", null, "chunk-target");

        setOperator(SOURCE_SPACE);
        FileEntryDB moved = entryRepo.moveAcrossSpace(root, TARGET_SPACE, targetParent);
        assertNotNull(moved);
        DSLContext sourceDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);
        assertEquals(0, sourceDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.SPACE_CODE.eq(SOURCE_SPACE)));
        DSLContext targetDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE), dsl);
        // 目标空间：target 父目录 + 根 + 3 目录 + 3 文件
        assertEquals(8, targetDsl.fetchCount(FILE_ENTRY, FILE_ENTRY.SPACE_CODE.eq(TARGET_SPACE)));
        for (int index = 0; index < 3; index++) {
            FileDB movedFile = entryRepo.queryFile(TARGET_SPACE,
                    entryRepo.queryFile(TARGET_SPACE, root.getFileId(), "chunk-dir-" + index).getFileId(),
                    "chunk-file-" + index + ".txt");
            assertNotNull(movedFile);
            assertEquals(TARGET_SPACE, movedFile.getSpaceCode());
        }
    }

    @Test
    public void crossSpaceMoveWithinSamePhysicalShardKeepsBothSpacesIsolated() {
        entryRepo.setFileEntryWriteMode("entry");
        // 找一个与源空间落在同一物理分片的不同空间：insert-select 源表与目标表为同一张表
        String sourceShard = FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE);
        String siblingSpace = null;
        for (int index = 0; index < 1000 && siblingSpace == null; index++) {
            String candidate = "sp-shard-" + index;
            if(FileRepo.getShardingKeyBySpaceCode(candidate).equals(sourceShard)) {
                siblingSpace = candidate;
            }
        }
        assertNotNull(siblingSpace);
        FileDB root = addDirectory(SOURCE_SPACE, "shard-root", null, "shard-root");
        FileDB child = addFile(SOURCE_SPACE, "shard-child.txt", root.getFileId(), "shard-child");
        setOperator(siblingSpace);
        FileDB targetParent = addDirectory(siblingSpace, "shard-target", null, "shard-target");

        setOperator(SOURCE_SPACE);
        FileEntryDB moved = entryRepo.moveAcrossSpace(root, siblingSpace, targetParent);
        assertNotNull(moved);
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, root.getFileId()));
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, child.getFileId()));
        FileEntryDB movedChild = entryRepo.queryActiveByFileId(siblingSpace, child.getFileId());
        assertNotNull(movedChild);
        assertEquals(moved.getEntryId(), movedChild.getParentEntryId());
        assertEquals(siblingSpace, fileRepo.queryFile(child.getFileId()).getSpaceCode());
    }

    @Test
    public void crossSpaceMoveRejectsNonDirectoryTargetParent() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB movedDir = addDirectory(SOURCE_SPACE, "parent-check-src", null, "parent-check-src");
        addFile(SOURCE_SPACE, "parent-check-child.txt", movedDir.getFileId(), "parent-check-child");
        setOperator(TARGET_SPACE);
        FileDB targetFile = addFile(TARGET_SPACE, "parent-check-target.txt", null, "parent-check-target");

        setOperator(SOURCE_SPACE);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> entryRepo.moveAcrossSpace(movedDir, TARGET_SPACE, targetFile));
        assertTrue(error.getMessage().contains("not a directory"));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, movedDir.getFileId()));
    }

    @Test
    public void crossSpaceMoveRejectsMovingIntoOwnSubtree() {
        entryRepo.setFileEntryWriteMode("entry");
        FileDB root = addDirectory(SOURCE_SPACE, "cycle-root", null, "cycle-root");
        FileDB sub = addDirectory(SOURCE_SPACE, "cycle-sub", root.getFileId(), "cycle-sub");

        assertThrows(IllegalArgumentException.class,
                () -> entryRepo.moveAcrossSpace(root, SOURCE_SPACE, sub));
        assertThrows(IllegalArgumentException.class,
                () -> entryRepo.moveAcrossSpace(root, SOURCE_SPACE, root));
        assertNotNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, sub.getFileId()));
    }

    @Test
    public void legacyEntryOnlyConvergesOnIntegrityConstraintViolation() {
        DataAccessException duplicateKey = new DataAccessException("duplicate", new SQLException("duplicate", "23505"));
        DataAccessException connectionFailure = new DataAccessException("connection", new SQLException("connection", "08006"));

        assertTrue(FileEntryRepo.isIntegrityConstraintViolation(duplicateKey));
        assertFalse(FileEntryRepo.isIntegrityConstraintViolation(connectionFailure));
    }

    @Test
    public void entryReadResolvesAncestorChainsWithoutClosure() {
        FileDB rootDir = addDirectory(SOURCE_SPACE, "chain-root", null, "chain-root");
        FileDB childDir = addDirectory(SOURCE_SPACE, "chain-child", rootDir.getFileId(), "chain-child");
        FileDB leaf = addFile(SOURCE_SPACE, "chain-leaf.txt", childDir.getFileId(), "chain-leaf");
        FileDB rootFile = addFile(SOURCE_SPACE, "chain-top.txt", null, "chain-top");
        FileDB deleted = addFile(SOURCE_SPACE, "chain-del.txt", null, "chain-del");
        fileRepo.updateFile(FileOps.builder().fileId(deleted.getFileId()).status(FileStatus.DELETED).build());

        // 清空闭包表，证明以下读取完全来自 file_entry
        dsl.execute("delete from file_closure_" + FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE));
        fileRepo.setFileEntryReadMode("entry");

        assertEquals(childDir.getFileId(), fileRepo.getDirectAncestorId(leaf.getFileId()));
        assertNull(fileRepo.getDirectAncestorId(rootFile.getFileId()));

        List<String> pathIds = fileRepo.getPathFiles(leaf.getFileId()).stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(rootDir.getFileId(), childDir.getFileId(), leaf.getFileId()), pathIds);

        Map<String, List<String>> ancestors = fileRepo.getFileAncestorIds(SOURCE_SPACE,
                Arrays.asList(leaf.getFileId(), childDir.getFileId(), rootFile.getFileId(), deleted.getFileId()));
        assertEquals(Arrays.asList(rootDir.getFileId(), childDir.getFileId()), ancestors.get(leaf.getFileId()));
        assertEquals(Collections.singletonList(rootDir.getFileId()), ancestors.get(childDir.getFileId()));
        assertEquals(Collections.emptyList(), ancestors.get(rootFile.getFileId()));
        assertEquals(Collections.emptyList(), ancestors.get(deleted.getFileId()));
    }

    @Test
    public void entryOnlyModeStopsClosureWritesAndStaysConsistent() {
        entryRepo.setFileEntryWriteMode("entry");
        fileRepo.setFileEntryReadMode("entry");
        DSLContext shardDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);

        FileDB root = addDirectory(SOURCE_SPACE, "eo-root", null, "eo-root");
        FileDB child = addDirectory(SOURCE_SPACE, "eo-child", root.getFileId(), "eo-child");
        FileDB leaf = addFile(SOURCE_SPACE, "eo-leaf.txt", child.getFileId(), "eo-leaf");

        // 闭包表零写入，层级读写全部走 entry
        assertEquals(0, shardDsl.fetchCount(FILE_CLOSURE));
        assertEquals(child.getFileId(), fileRepo.getDirectAncestorId(leaf.getFileId()));
        assertEquals(Arrays.asList(root.getFileId(), child.getFileId(), leaf.getFileId()),
                fileRepo.getPathFiles(leaf.getFileId()).stream().map(FileDB::getFileId).collect(Collectors.toList()));

        // 防环校验由 entry 自己承担
        assertThrows(IllegalArgumentException.class,
                () -> fileRepo.moveFile(root, child));

        // entry 写是主写：重名冲突必须失败，不再降级
        assertThrows(IllegalStateException.class,
                () -> addFile(SOURCE_SPACE, "eo-child", root.getFileId(), "eo-dup"));

        fileRepo.moveFile(leaf, root);
        assertEquals(root.getFileId(), fileRepo.getDirectAncestorId(leaf.getFileId()));

        fileRepo.updateFile(FileOps.builder().fileId(leaf.getFileId()).status(FileStatus.DELETED).build());
        fileRepo.deleteFileClosure(leaf.getFileId(), FileType.USER);
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, leaf.getFileId()));
        assertEquals(0, shardDsl.fetchCount(FILE_CLOSURE));
    }

    @Test
    public void closureModeStopsEntryWritesAndKeepsClosureAuthoritative() {
        entryRepo.setFileEntryWriteMode("closure");
        DSLContext shardDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);

        FileDB root = addDirectory(SOURCE_SPACE, "cm-root", null, "cm-root");
        FileDB leaf = addFile(SOURCE_SPACE, "cm-leaf.txt", root.getFileId(), "cm-leaf");

        // entry 零写入，闭包链路完整可用
        assertEquals(0, shardDsl.fetchCount(FILE_ENTRY));
        assertEquals(root.getFileId(), fileRepo.getDirectAncestorId(leaf.getFileId()));

        fileRepo.moveFile(leaf, null);
        assertNull(fileRepo.getDirectAncestorId(leaf.getFileId()));
        fileRepo.updateFile(FileOps.builder().fileId(leaf.getFileId()).filename("cm-renamed.txt").build());
        fileRepo.updateFile(FileOps.builder().fileId(leaf.getFileId()).status(FileStatus.DELETED).build());
        assertEquals(0, shardDsl.fetchCount(FILE_ENTRY));

        // 跨空间移动依赖 entry 记录，closure 模式下必须拒绝
        assertThrows(IllegalStateException.class,
                () -> entryRepo.moveAcrossSpace(root, TARGET_SPACE, null));
    }

    @Test
    public void countNodesUsesEntriesForSpaceAndDirectChildren() {
        FileDB directory = addDirectory(SOURCE_SPACE, "count-dir", null, "count-dir");
        FileDB rootFile = addFile(SOURCE_SPACE, "count-root.txt", null, "count-root");
        FileDB childFile = addFile(SOURCE_SPACE, "count-child.txt", directory.getFileId(), "count-child");
        FileDB resource = addResource(SOURCE_SPACE, "count-resource", directory.getFileId(), "count-resource");
        FileDB nestedDirectory = addDirectory(SOURCE_SPACE, "count-nested", directory.getFileId(), "count-nested");
        addFile(SOURCE_SPACE, "count-grandchild.txt", nestedDirectory.getFileId(), "count-grandchild");

        DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl)
                .dropTable(FILE_CLOSURE)
                .execute();

        FileNodeCount spaceCount = entryRepo.countNodes(SOURCE_SPACE, null);
        assertEquals(3L, spaceCount.getFileCount());
        assertEquals(2L, spaceCount.getDirectoryCount());
        assertEquals(1L, spaceCount.getResourceCount());

        FileNodeCount childCount = entryRepo.countNodes(SOURCE_SPACE, directory.getFileId());
        assertEquals(1L, childCount.getFileCount());
        assertEquals(1L, childCount.getDirectoryCount());
        assertEquals(1L, childCount.getResourceCount());

        fileRepo.updateFile(FileOps.builder().fileId(rootFile.getFileId()).status(FileStatus.DELETED).build());
        assertEquals(2L, entryRepo.countNodes(SOURCE_SPACE, null).getFileCount());
        assertEquals(FileEntryRepo.TYPE_RESOURCE, entryRepo.queryActiveByFileId(SOURCE_SPACE, resource.getFileId()).getType());
        assertEquals(FileEntryRepo.TYPE_FILE, entryRepo.queryActiveByFileId(SOURCE_SPACE, childFile.getFileId()).getType());
    }

    @Test
    public void entryReadFiltersResourceNodesLikeClosure() {
        FileDB dir = addDirectory(SOURCE_SPACE, "res-dir", null, "res-dir");
        FileDB file = addFile(SOURCE_SPACE, "res-file.txt", dir.getFileId(), "res-file");
        FileDB resource = addResource(SOURCE_SPACE, "res-node", dir.getFileId(), "res-node");

        // 闭包读作为基准
        List<String> closureListed = listIds(dir.getFileId());
        List<String> closureFilePage = pageIds(dir.getFileId(), "file");
        List<String> closureResourcePage = pageIds(dir.getFileId(), "resource");
        assertEquals(Collections.singletonList(file.getFileId()), closureListed);
        assertEquals(Collections.singletonList(file.getFileId()), closureFilePage);
        assertEquals(Collections.singletonList(resource.getFileId()), closureResourcePage);

        fileRepo.setFileEntryReadMode("entry");
        assertEquals(closureListed, listIds(dir.getFileId()));
        assertEquals(closureFilePage, pageIds(dir.getFileId(), "file"));
        assertEquals(closureResourcePage, pageIds(dir.getFileId(), "resource"));
        assertEquals(2, pageIds(dir.getFileId(), null).size());
    }

    private List<String> listIds(String ancestorId) {
        return fileRepo.listFile(null, 10, "asc", null, SOURCE_SPACE, ancestorId).stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
    }

    private List<String> pageIds(String ancestorId, String type) {
        return fileRepo.pageFiles(PageFileOps.builder()
                .spaceCode(SOURCE_SPACE)
                .ancestorId(ancestorId)
                .type(type)
                .page(1)
                .pageSize(10)
                .order("asc")
                .build())
                .getData().stream()
                .map(FileDB::getFileId)
                .collect(Collectors.toList());
    }

    @Test
    public void entryListUsesStableCursorAndHandlesMissingAfter() {
        FileDB first = addFile(SOURCE_SPACE, "first.txt", null, "cursor-first");
        FileDB second = addFile(SOURCE_SPACE, "second.txt", null, "cursor-second");
        FileDB third = addFile(SOURCE_SPACE, "third.txt", null, "cursor-third");
        String missingCursorId = first.getFileId().replaceFirst("260808", "260807");
        DSLContext shardDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(SOURCE_SPACE), dsl);
        LocalDateTime sharedCtime = LocalDateTime.of(2026, 8, 9, 0, 0);
        shardDsl.update(FILE).set(FILE.CTIME, sharedCtime).execute();
        fileRepo.setFileEntryReadMode("entry");

        List<FileDB> firstPage = fileRepo.listFile(null, 2, "asc", null, SOURCE_SPACE, null);
        assertEquals(first.getFileId(), firstPage.get(0).getFileId());
        assertEquals(second.getFileId(), firstPage.get(1).getFileId());

        List<FileDB> secondPage = fileRepo.listFile(null, 2, "asc", second.getFileId(), SOURCE_SPACE, null);
        assertEquals(1, secondPage.size());
        assertEquals(third.getFileId(), secondPage.get(0).getFileId());
        assertTrue(fileRepo.listFile(null, 2, "asc", missingCursorId, SOURCE_SPACE, null).isEmpty());
    }

    @Test
    public void addFileWritesClosureInFileSpaceNotOperatorSpace() {
        // login context stays in SOURCE_SPACE while the file targets TARGET_SPACE,
        // mimicking an import with space_code in the request body
        setOperator(SOURCE_SPACE);
        String hash = String.valueOf(Math.abs(CustomStringUtils.hashCode(TARGET_SPACE)));
        String fileId = "file-260808000000000321-" + hash + FileType.USER.getSuffix();
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename("import.txt");
        file.setIsDir(0);
        file.setNodeType(NodeType.FILE.getValue());
        file.setResourceId("");
        file.setSpaceCode(TARGET_SPACE);
        file.setPurpose("assistants");
        file.setStatus(FileStatus.NOT_DELETED.getValue());
        file.setBucket("bucket");
        file.setPath("import/import.txt");
        file.setMetaData("{}");
        fileRepo.addFile(file, null, FileType.USER);

        DSLContext shardDsl = DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(TARGET_SPACE), dsl);
        List<String> closureSpaces = shardDsl.select(FILE_CLOSURE.SPACE_CODE).from(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .fetchInto(String.class);
        assertFalse(closureSpaces.isEmpty());
        assertTrue(closureSpaces.stream().allMatch(TARGET_SPACE::equals));

        // duplicate detection in the target space now sees the file
        assertTrue(fileRepo.exists(TARGET_SPACE, null, "import.txt"));
    }

    private FileDB addDirectory(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.DIRECTORY, 1, NodeType.DIRECTORY, "");
    }

    private FileDB addFile(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.USER, 0, NodeType.FILE, "");
    }

    private FileDB addResource(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.USER, 0, NodeType.RESOURCE, "res:" + seed);
    }

    private FileDB add(String spaceCode, String filename, String ancestorId, String seed, FileType type, int isDir,
            NodeType nodeType, String resourceId) {
        setOperator(spaceCode);
        String hash = String.valueOf(Math.abs(CustomStringUtils.hashCode(spaceCode)));
        String fileId = "file-260808000000" + String.format("%06d", Math.abs(seed.hashCode()) % 1000000) + "-" + hash + type.getSuffix();
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename(filename);
        file.setIsDir(isDir);
        file.setNodeType(nodeType.getValue());
        file.setResourceId(resourceId);
        file.setSpaceCode(spaceCode);
        file.setPurpose("assistants");
        file.setStatus(FileStatus.NOT_DELETED.getValue());
        file.setBucket("bucket");
        file.setPath("path/" + seed);
        file.setMetaData("{}");
        fileRepo.addFile(file, ancestorId, type);
        return fileRepo.queryFile(fileId, type);
    }

    private boolean hasClosure(String spaceCode, String ancestorId, String descendantId) {
        String shard = FileRepo.getShardingKeyBySpaceCode(spaceCode);
        DSLContext shardDsl = DSLContextHolder.get(shard, dsl);
        return shardDsl.fetchExists(DSL.selectOne().from(com.ke.bella.files.db.Tables.FILE_CLOSURE)
                .where(com.ke.bella.files.db.Tables.FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
                .and(com.ke.bella.files.db.Tables.FILE_CLOSURE.DESCENDANT_ID.eq(descendantId)));
    }

    private void setOperator(String spaceCode) {
        BellaContext.setOperator(Operator.builder().userId(1L).userName("tester").spaceCode(spaceCode).build());
    }
}
