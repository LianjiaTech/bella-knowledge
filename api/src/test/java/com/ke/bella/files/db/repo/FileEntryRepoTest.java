package com.ke.bella.files.db.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.Before;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.Operator;

public class FileEntryRepoTest {
    private static final String SOURCE_SPACE = "sp-a";
    private static final String TARGET_SPACE = "sp-b";

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
        dsl.execute("drop table if exists file_" + targetShard);
        dsl.execute("drop table if exists file_closure_" + targetShard);
        dsl.execute("drop table if exists file_entry_" + targetShard);
        dsl.execute("create table file_" + targetShard + " as select * from file_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_closure_" + targetShard + " as select * from file_closure_" + sourceShard + " where 1 = 0");
        dsl.execute("create table file_entry_" + targetShard + " as select * from file_entry_" + sourceShard + " where 1 = 0");
        IDGenerator.setInstanceId(1L);
        fileRepo = new FileRepo(dsl);
        entryRepo = new FileEntryRepo(dsl);
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
        assertThrows(IllegalStateException.class,
                () -> addFile(SOURCE_SPACE, "report.txt", firstParent.getFileId(), "duplicate"));

        fileRepo.moveFileClosures(file.getFileId(), secondParent.getFileId());
        FileEntryDB moved = entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId());
        assertEquals(original.getEntryId(), moved.getEntryId());
        assertEquals(entryRepo.queryActiveByFileId(SOURCE_SPACE, secondParent.getFileId()).getEntryId(), moved.getParentEntryId());

        fileRepo.updateFile(FileOps.builder().fileId(file.getFileId()).filename("renamed.txt").build());
        assertEquals("renamed.txt", entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId()).getFilename());

        fileRepo.updateFile(FileOps.builder().fileId(file.getFileId()).status(FileStatus.DELETED).build());
        fileRepo.deleteFileClosure(file.getFileId(), FileType.USER);
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, file.getFileId()));
        FileDB recreated = addFile(SOURCE_SPACE, "renamed.txt", secondParent.getFileId(), "recreated");
        assertNotEquals(original.getEntryId(), entryRepo.queryActiveByFileId(SOURCE_SPACE, recreated.getFileId()).getEntryId());
    }

    @Test
    public void crossSpaceMoveRebuildsEntryAndClosureWithoutMovingFileRow() {
        FileDB source = addFile(SOURCE_SPACE, "cross.txt", null, "cross");
        setOperator(TARGET_SPACE);
        FileDB targetParent = addDirectory(TARGET_SPACE, "target", null, "target");
        String sourceEntryId = entryRepo.queryActiveByFileId(SOURCE_SPACE, source.getFileId()).getEntryId();

        entryRepo.setCrossSpaceMoveEnabled(true);
        FileEntryDB target = entryRepo.moveAcrossSpace(source.getFileId(), TARGET_SPACE, targetParent.getFileId());

        assertNotEquals(sourceEntryId, target.getEntryId());
        assertEquals(entryRepo.queryActiveByFileId(TARGET_SPACE, targetParent.getFileId()).getEntryId(), target.getParentEntryId());
        assertNull(entryRepo.queryActiveByFileId(SOURCE_SPACE, source.getFileId()));
        FileDB unchanged = fileRepo.queryFile(source.getFileId());
        assertEquals(TARGET_SPACE, unchanged.getSpaceCode());
        assertEquals(source.getBucket(), unchanged.getBucket());
        assertEquals(source.getPath(), unchanged.getPath());
        assertFalse(hasClosure(SOURCE_SPACE, source.getFileId(), source.getFileId()));
        assertTrue(hasClosure(TARGET_SPACE, targetParent.getFileId(), source.getFileId()));
    }

    private FileDB addDirectory(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.DIRECTORY, 1);
    }

    private FileDB addFile(String spaceCode, String filename, String ancestorId, String seed) {
        return add(spaceCode, filename, ancestorId, seed, FileType.USER, 0);
    }

    private FileDB add(String spaceCode, String filename, String ancestorId, String seed, FileType type, int isDir) {
        setOperator(spaceCode);
        String hash = String.valueOf(Math.abs(CustomStringUtils.hashCode(spaceCode)));
        String fileId = "file-260808000000" + String.format("%06d", Math.abs(seed.hashCode()) % 1000000) + "-" + hash + type.getSuffix();
        FileDB file = new FileDB();
        file.setFileId(fileId);
        file.setFilename(filename);
        file.setIsDir(isDir);
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
