package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;
import static com.ke.bella.files.db.Tables.FILE_SHARDING;
import static com.ke.bella.files.db.repo.DSLContextHolder.targetTableName;
import static org.jooq.impl.DSL.field;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.ke.bella.files.db.FileIdGenerator;
import com.ke.bella.files.db.repo.FileEntryRepo.EntryReadNotReadyException;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.db.tables.pojos.FileShardingDB;
import com.ke.bella.files.db.tables.records.FileClosureRecord;
import com.ke.bella.files.db.tables.records.FileProgressRecord;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.db.tables.records.FileShardingRecord;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileNodeCount;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.files.utils.JsonUtils;

@Component
public class FileRepo implements BaseRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileRepo.class);
    private final DSLContext db;
    private final FileEntryRepo fileEntryRepo;

    @Value("${bella.file-api.file-entry.read-mode:closure}")
    private String fileEntryReadMode;

    public FileRepo(DSLContext db, FileEntryRepo fileEntryRepo) {
        this.db = db;
        this.fileEntryRepo = fileEntryRepo;
    }

    public String getShardingKeyByFileId(String fileId, FileType fileType) {
        if(fileType.isUsersType()) {
            return getUserFileShardingKey(fileId);
        } else if(fileType.notUsersType()) {
            return getTmpOrSysFileShardingKey(fileId, fileType);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }

    public String getShardingKeyByFileId(String fileId) {
        FileType fileType = FileType.fromFileId(fileId);
        return getShardingKeyByFileId(fileId, fileType);
    }

    private String getUserFileShardingKey(String fileId) {
        String spaceCodeHashStr = FileIdGenerator.extractSpaceCodeHash(fileId);
        Integer spaceCodeHash = Integer.valueOf(spaceCodeHashStr);
        return getShardingKeyBySpaceCodeHash(spaceCodeHash);
    }

    private String getTmpOrSysFileShardingKey(String fileId, FileType fileType) {
        LocalDateTime time = FileIdGenerator.extractTimeFromFileId(fileId);
        FileShardingDB sharding = db.selectFrom(FILE_SHARDING)
                .where(FILE_SHARDING.KEY_TIME.le(time))
                .and(FILE_SHARDING.TYPE.eq(fileType.getType()))
                .orderBy(FILE_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(FileShardingDB.class);
        if(sharding == null) {
            throw new IllegalStateException(
                    "No file_sharding record found for " + fileType.getType() + " type. Please ensure database is properly initialized.");
        }
        return StringUtils.isEmpty(sharding.getKey()) ? fileType.getType() : fileType.getType() + "_" + sharding.getKey();
    }

    public static String getShardingKeyBySpaceCode(String spaceCode) {
        Integer hashCode = CustomStringUtils.hashCode(spaceCode);
        return getShardingKeyBySpaceCodeHash(hashCode);
    }

    private static String getShardingKeyBySpaceCodeHash(Integer spaceCodeHash) {
        spaceCodeHash = Math.abs(spaceCodeHash);
        return Integer.toString(spaceCodeHash % 16);
    }

    public static String getShardingKeyByFileIdStatic(String fileId) {
        return getShardingKeyBySpaceCodeHash(Integer.valueOf(FileIdGenerator.extractSpaceCodeHash(fileId)));
    }

    private DSLContext db(String shardingKey) {
        return DSLContextHolder.get(shardingKey, db);
    }

    private String queryNewFileId(String fileId) {
        if(fileId.startsWith("file-")) {
            return fileId;
        }
        String newFileId = db.select(FILE_MAPPING.FILE_ID)
                .from(FILE_MAPPING)
                .where(FILE_MAPPING.FILE_ID_OLD.eq(fileId))
                .fetchOneInto(String.class);
        if(newFileId == null) {
            throw new FileNotFoundException(fileId);
        }
        return newFileId;
    }

    public FileDB queryFile(String fileId, FileType fileType) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .fetchOneInto(FileDB.class);
    }

    public FileDB queryFile(String fileId) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .fetchOneInto(FileDB.class);
    }

    public FileDB queryFileByDomTreeFileId(String domTreeFileId) {
        String shardingKey = getShardingKeyByFileId(domTreeFileId);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.DOM_TREE_FILE_ID.eq(domTreeFileId)
                        .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .fetchOne()
                .into(FileDB.class);
    }

    public FileDB queryFileByPdfFileId(String pdfFileId) {
        String shardingKey = getShardingKeyByFileId(pdfFileId);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.PDF_FILE_ID.eq(pdfFileId)
                        .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .fetchOne()
                .into(FileDB.class);
    }

    public boolean exists(String spaceCode, @Nullable String ancestorId, @NotNull String filename) {
        if(useEntryRead()) {
            try {
                return fileEntryRepo.exists(spaceCode, ancestorId, filename);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("exists", e);
            }
        }
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record1<Integer>> sql = db(shardingKey).selectOne()
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.FILENAME.eq(filename))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode));

        if(StringUtils.isEmpty(ancestorId)) {
            sql.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
        } else {
            sql.and(FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
                    .and(FILE_CLOSURE.DEPTH.eq(1L));
        }

        return sql.limit(1).fetchOptional().isPresent();
    }

    public FileDB queryFile(String spaceCode, @Nullable String ancestorId, @NotNull String filename) {
        if(useEntryRead()) {
            try {
                return fileEntryRepo.queryFile(spaceCode, ancestorId, filename);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("queryFile", e);
            }
        }
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> sql = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .and(FILE.FILENAME.eq(filename));

        if(StringUtils.isEmpty(ancestorId)) {
            sql.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
        } else {
            sql.and(FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
                    .and(FILE_CLOSURE.DEPTH.eq(1L));
        }

        return sql.limit(1).fetchOneInto(FileDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public String addFile(FileDB fileDB, String ancestorId, FileType fileType) {
        String shardingKey = getShardingKeyByFileId(fileDB.getFileId());

        FileRecord rec = FILE.newRecord();
        rec.from(fileDB);
        fillCreatorInfo(rec);
        int insertedNum = db(shardingKey).insertInto(FILE)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert file failed, fileId: " + fileDB.getFileId());
        }

        if(fileType.needsDirectorySupport()) {
            if(fileEntryRepo.closureWriteEnabled()) {
                addFileClosures(fileDB.getSpaceCode(), fileDB.getFileId(), ancestorId);
            }
            fileEntryRepo.addEntry(fileDB.getSpaceCode(), fileDB, ancestorId);
        }

        return shardingKey;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFile(FileOps op, boolean increaseVersion) {
        String fileId = queryNewFileId(op.getFileId());
        String shardingKey = getShardingKeyByFileId(fileId);
        FileType fileType = FileType.fromFileId(fileId);
        boolean renameEntry = op.getFilename() != null;
        boolean deleteEntry = op.getStatus() == FileStatus.DELETED;
        FileDB currentFile = fileType.needsDirectorySupport() && (renameEntry || deleteEntry) ? queryFile(fileId, fileType) : null;
        FileRecord rec = FILE.newRecord();
        rec.setFileId(fileId);
        if(op.getStatus() != null) {
            rec.setStatus(op.getStatus().getValue());
        }
        if(op.getBroadcastStatus() != null) {
            rec.setBroadcastStatus(op.getBroadcastStatus().getValue());
        }
        if(op.getDomTreeFileId() != null) {
            rec.setDomTreeFileId(op.getDomTreeFileId());
        }
        if(op.getPdfFileId() != null) {
            rec.setPdfFileId(op.getPdfFileId());
        }
        if(op.getFilename() != null) {
            rec.setFilename(op.getFilename());
        }
        if(op.getPurpose() != null) {
            rec.setPurpose(op.getPurpose());
        }
        if(op.getMetadata() != null) {
            rec.setMetaData(op.getMetadata());
        }
        if(op.getMimeType() != null) {
            rec.setMimeType(op.getMimeType());
        }
        if(op.getType() != null) {
            rec.setType(op.getType());
        }
        if(op.getExtension() != null) {
            rec.setExtension(op.getExtension());
        }
        if(op.getPath() != null) {
            rec.setPath(op.getPath());
        }
        if(op.getBytes() != null) {
            rec.setBytes(op.getBytes());
        }
        if(op.getDescription() != null) {
            rec.setDescription(op.getDescription());
        }
        if(op.getCities() != null) {
            rec.setCities(JsonUtils.toJson(op.getCities()));
        }
        if(op.getTags() != null) {
            rec.setTags(JsonUtils.toJson(op.getTags()));
        }

        fillUpdatorInfo(rec);

        UpdateSetMoreStep<FileRecord> sql = db(shardingKey).update(FILE)
                .set(rec);
        if(increaseVersion) {
            sql.set(FILE.VERSION, FILE.VERSION.add(1));
        }
        int updatedNum = sql
                .where(FILE.FILE_ID.eq(fileId))
                .execute();

        if(updatedNum != 1) {
            throw new IllegalStateException("update file failed, fileId: " + fileId);
        }
        if(currentFile != null && renameEntry && !Objects.equals(currentFile.getFilename(), op.getFilename())) {
            fileEntryRepo.rename(currentFile.getSpaceCode(), fileId, op.getFilename());
        }
        if(currentFile != null && op.getStatus() == FileStatus.DELETED) {
            fileEntryRepo.delete(currentFile.getSpaceCode(), fileId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateFile(FileOps op) {
        updateFile(op, false);
    }

    public List<FileDB> listFile(
            String purpose,
            Integer limit,
            String order,
            String after,
            String spaceCode,
            String ancestorId) {
        if(useEntryRead()) {
            try {
                FileDB afterFile = null;
                if(StringUtils.isNotEmpty(after)) {
                    afterFile = queryFile(after);
                    if(afterFile == null) {
                        return Collections.emptyList();
                    }
                }
                return fileEntryRepo.listFiles(spaceCode, ancestorId, purpose, limit, order, afterFile);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("listFile", e);
            }
        }
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> query = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .and(FILE.NODE_TYPE.ne(NodeType.RESOURCE.getValue()));

        if(StringUtils.isEmpty(ancestorId)) {
            query.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
        } else {
            query.and(FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
                    .and(FILE_CLOSURE.DEPTH.eq(1L));
        }

        if(StringUtils.isNotEmpty(purpose)) {
            query = query.and(FILE.PURPOSE.eq(purpose));
        }
        if(StringUtils.isNotEmpty(after)) {
            after = queryNewFileId(after);
            LocalDateTime afterCtime = db(shardingKey).select(FILE.CTIME)
                    .from(FILE)
                    .where(FILE.FILE_ID.eq(after))
                    .fetchOneInto(LocalDateTime.class);
            query = query.and("asc".equalsIgnoreCase(order) ? FILE.CTIME.gt(afterCtime) : FILE.CTIME.lt(afterCtime));
        }

        return query
                .orderBy("asc".equalsIgnoreCase(order) ? FILE.CTIME.asc() : FILE.CTIME.desc())
                .limit(limit)
                .fetchInto(FileDB.class);
    }

    public FileProgressDB queryProgress(
            String fileId,
            String progressName) {
        fileId = queryNewFileId(fileId);
        FileType fileType = FileType.fromFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        return db(shardingKey).selectFrom(FILE_PROGRESS)
                .where(FILE_PROGRESS.FILE_ID.eq(fileId).and(FILE_PROGRESS.NAME.eq(progressName)))
                .fetchOneInto(FileProgressDB.class);
    }

    public void insertProgress(
            String fileId,
            String progressName,
            String status,
            String message,
            Integer percent) {
        fileId = queryNewFileId(fileId);
        FileType fileType = FileType.fromFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        FileProgressRecord rec = FILE_PROGRESS.newRecord();
        rec.setFileId(fileId);
        rec.setName(progressName);
        rec.setStatus(status);
        rec.setPercent(percent);
        fillCreatorInfo(rec);
        if(message != null) {
            rec.setMessage(message);
        }
        int insertedNum = db(shardingKey).insertInto(FILE_PROGRESS)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert progress failed, file_id: " + fileId + ", progress_name: " + progressName);
        }
    }

    public void updateProgress(
            String fileId,
            String progressName,
            String status,
            String message,
            Integer percent) {
        fileId = queryNewFileId(fileId);
        FileType fileType = FileType.fromFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        FileProgressRecord rec = FILE_PROGRESS.newRecord();
        rec.setStatus(status);
        rec.setPercent(percent);
        if(message != null) {
            rec.setMessage(message);
        }
        fillUpdatorInfo(rec);
        int updatedNum = db(shardingKey).update(FILE_PROGRESS)
                .set(rec)
                .where(FILE_PROGRESS.FILE_ID.eq(fileId).and(FILE_PROGRESS.NAME.eq(progressName)))
                .execute();
        if(updatedNum != 1) {
            throw new IllegalStateException("update progress failed, file_id: " + fileId + ", progress_name: " + progressName);
        }
    }

    public List<FileDB> getFiles(ListFileOps ops) {
        Map<String, List<String>> shardToFileIds = ops.getFileIds().stream()
                .collect(Collectors.groupingBy(this::getShardingKeyByFileId));

        List<SelectConditionStep<Record>> selects = shardToFileIds.entrySet().stream()
                .map(entry -> db
                        .select()
                        .from(targetTableName(FILE.getName(), entry.getKey()))
                        .where(field(FILE.STATUS.getName()).eq(FileStatus.NOT_DELETED.getValue()))
                        .and(field(FILE.FILE_ID.getName()).in(entry.getValue())))
                .collect(Collectors.toList());

        if(selects.isEmpty()) {
            return Collections.emptyList();
        }

        SelectOrderByStep<Record> records = selects.get(0);
        for (int i = 1; i < selects.size(); i++) {
            records = records.unionAll(selects.get(i));
        }

        return records.fetchInto(FileDB.class);
    }

    private InsertSetMoreStep<FileClosureRecord> createFileClosureInsert(DSLContext dsl, String spaceCode, String fileId, String ancestorId,
            Long depth) {
        return createFileClosureInsert(dsl, spaceCode, fileId, ancestorId, depth, -1L);
    }

    private InsertSetMoreStep<FileClosureRecord> createFileClosureInsert(DSLContext dsl, String spaceCode, String fileId, String ancestorId,
            Long depth,
            Long rootDepth) {
        FileClosureRecord rec = FILE_CLOSURE.newRecord();
        // closure rows must live in the file's space, not the operator's:
        // imports may target a space other than the login context
        rec.setSpaceCode(spaceCode);
        rec.setAncestorId(ancestorId);
        rec.setDescendantId(fileId);
        rec.setDepth(depth);
        rec.setRootDepth(rootDepth);
        fillCreatorInfo(rec);

        return dsl.insertInto(FILE_CLOSURE).set(rec);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFileClosures(String spaceCode, String fileId, String ancestorId) {
        String shardingKey = getShardingKeyByFileId(fileId);
        DSLContext dsl = db(shardingKey);
        List<InsertSetMoreStep<FileClosureRecord>> inserts = new ArrayList<>();

        long rootDepth = 1L;

        if(StringUtils.isNotEmpty(ancestorId)) {
            List<FileClosureRecord> ancestorClosures = loadAncestorClosuresForUpdate(dsl, ancestorId);

            Assert.isTrue(!CollectionUtils.isEmpty(ancestorClosures),
                    "descendant_id not found in file_closure, descendant_id: " + ancestorId);

            rootDepth = (long) (ancestorClosures.size() + 1);

            for (FileClosureRecord ancestorClosure : ancestorClosures) {
                inserts.add(createFileClosureInsert(dsl, spaceCode, fileId, ancestorClosure.getAncestorId(), ancestorClosure.getDepth() + 1));
            }
        }

        inserts.add(createFileClosureInsert(dsl, spaceCode, fileId, fileId, 0L, rootDepth));

        int[] results = dsl.batch(inserts).execute();
        if(results.length != inserts.size()) {
            throw new IllegalStateException("batch insert file_closure failed, fileId: " + fileId);
        }
    }

    private List<FileClosureRecord> loadAncestorClosuresForUpdate(DSLContext dsl, String ancestorId) {
        dsl.select(FILE_CLOSURE.ID)
                .from(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(ancestorId))
                .forUpdate()
                .fetch();
        return dsl.selectFrom(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(ancestorId))
                .orderBy(FILE_CLOSURE.DEPTH.asc())
                .forUpdate()
                .fetchInto(FileClosureRecord.class);
    }

    public void deleteFileClosure(String fileId, FileType fileType) {
        if(!fileEntryRepo.closureWriteEnabled()) {
            return;
        }
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        db(shardingKey).delete(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .or(FILE_CLOSURE.ANCESTOR_ID.eq(fileId))
                .execute();
    }

    /**
     * 同空间移动：file_entry 是主记录，闭包表仅在 write-mode 仍写闭包（dual/closure）时同步维护。
     * file 与 targetAncestor 是调用方已查出的快照，不再回表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void moveFile(FileDB file, @Nullable FileDB targetAncestor) {
        String fileId = file.getFileId();
        String targetAncestorId = targetAncestor == null ? null : targetAncestor.getFileId();
        if(fileEntryRepo.closureWriteEnabled()) {
            String shardingKey = getShardingKeyByFileId(fileId);
            DSLContext dsl = db(shardingKey);
            ClosureMoveSnapshot snapshot = loadClosureMoveSnapshot(dsl, fileId, targetAncestorId);

            deleteExternalClosures(dsl, snapshot);
            insertExternalClosures(dsl, snapshot, fileId, file.getSpaceCode());
            updateSubtreeRootDepths(dsl, snapshot, fileId);
        }
        fileEntryRepo.move(file.getSpaceCode(), fileId, targetAncestorId);
    }

    /**
     * 跨空间移动薄委托：事务由 moveAcrossSpace 自身声明，加入调用方已开启的事务。
     */
    public void moveFileAcrossSpace(FileDB file, String targetSpaceCode, @Nullable FileDB targetAncestor) {
        fileEntryRepo.moveAcrossSpace(file, targetSpaceCode, targetAncestor);
    }

    private ClosureMoveSnapshot loadClosureMoveSnapshot(DSLContext dsl, String fileId, String targetAncestorId) {
        List<FileClosureRecord> subtreeClosures = dsl.selectFrom(FILE_CLOSURE)
                .where(FILE_CLOSURE.ANCESTOR_ID.eq(fileId))
                .orderBy(FILE_CLOSURE.DEPTH.asc())
                .forUpdate()
                .fetchInto(FileClosureRecord.class);
        Assert.isTrue(!CollectionUtils.isEmpty(subtreeClosures),
                "ancestor_id not found in file_closure, ancestor_id: " + fileId);
        boolean sourceSelfClosureExists = subtreeClosures.stream()
                .anyMatch(closure -> StringUtils.equals(closure.getDescendantId(), fileId) && closure.getDepth() == 0L);
        Assert.isTrue(sourceSelfClosureExists, "self closure not found, fileId: " + fileId);

        boolean targetInSubtree = subtreeClosures.stream()
                .anyMatch(closure -> StringUtils.equals(closure.getDescendantId(), targetAncestorId));
        Assert.isTrue(!targetInSubtree, "cannot move a directory into itself or its descendant");

        List<FileClosureRecord> sourceAncestorClosures = dsl.selectFrom(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .orderBy(FILE_CLOSURE.DEPTH.asc())
                .forUpdate()
                .fetchInto(FileClosureRecord.class);
        Assert.isTrue(!CollectionUtils.isEmpty(sourceAncestorClosures),
                "descendant_id not found in file_closure, descendant_id: " + fileId);

        List<FileClosureRecord> targetAncestorClosures = Collections.emptyList();
        long targetRootDepth = 0L;
        if(StringUtils.isNotEmpty(targetAncestorId)) {
            targetAncestorClosures = dsl.selectFrom(FILE_CLOSURE)
                    .where(FILE_CLOSURE.DESCENDANT_ID.eq(targetAncestorId))
                    .orderBy(FILE_CLOSURE.DEPTH.asc())
                    .forUpdate()
                    .fetchInto(FileClosureRecord.class);
            Assert.isTrue(!CollectionUtils.isEmpty(targetAncestorClosures),
                    "descendant_id not found in file_closure, descendant_id: " + targetAncestorId);

            FileClosureRecord targetSelfClosure = targetAncestorClosures.stream()
                    .filter(closure -> closure.getDepth() == 0L)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "self closure not found, descendant_id: " + targetAncestorId));
            Assert.isTrue(targetSelfClosure.getRootDepth() > 0,
                    "invalid root_depth for target ancestor, descendant_id: " + targetAncestorId);
            targetRootDepth = targetSelfClosure.getRootDepth();
        }

        List<String> subtreeIds = subtreeClosures.stream()
                .map(FileClosureRecord::getDescendantId)
                .collect(Collectors.toList());
        List<String> externalAncestorIds = sourceAncestorClosures.stream()
                .map(FileClosureRecord::getAncestorId)
                .filter(ancestorId -> !StringUtils.equals(ancestorId, fileId))
                .collect(Collectors.toList());

        return new ClosureMoveSnapshot(subtreeIds, externalAncestorIds, targetAncestorId,
                targetAncestorClosures.size(), targetRootDepth);
    }

    private void deleteExternalClosures(DSLContext dsl, ClosureMoveSnapshot snapshot) {
        if(!snapshot.externalAncestorIds.isEmpty()) {
            dsl.delete(FILE_CLOSURE)
                    .where(FILE_CLOSURE.ANCESTOR_ID.in(snapshot.externalAncestorIds))
                    .and(FILE_CLOSURE.DESCENDANT_ID.in(snapshot.subtreeIds))
                    .execute();
        }
    }

    private void insertExternalClosures(DSLContext dsl, ClosureMoveSnapshot snapshot, String fileId, String spaceCode) {
        if(StringUtils.isEmpty(snapshot.targetAncestorId)) {
            return;
        }

        Table<FileClosureRecord> target = FILE_CLOSURE.as("target_closure");
        Table<FileClosureRecord> subtree = FILE_CLOSURE.as("subtree_closure");
        Field<String> targetAncestorId = target.field(FILE_CLOSURE.ANCESTOR_ID);
        Field<Long> targetDepth = target.field(FILE_CLOSURE.DEPTH);
        Field<String> targetDescendantId = target.field(FILE_CLOSURE.DESCENDANT_ID);
        Field<String> subtreeAncestorId = subtree.field(FILE_CLOSURE.ANCESTOR_ID);
        Field<String> subtreeDescendantId = subtree.field(FILE_CLOSURE.DESCENDANT_ID);
        Field<Long> subtreeDepth = subtree.field(FILE_CLOSURE.DEPTH);
        FileClosureRecord audit = FILE_CLOSURE.newRecord();
        fillCreatorInfo(audit);

        int insertedCount = dsl.insertInto(FILE_CLOSURE,
                FILE_CLOSURE.ANCESTOR_ID, FILE_CLOSURE.DESCENDANT_ID, FILE_CLOSURE.SPACE_CODE,
                FILE_CLOSURE.DEPTH, FILE_CLOSURE.ROOT_DEPTH, FILE_CLOSURE.CUID, FILE_CLOSURE.CU_NAME,
                FILE_CLOSURE.CTIME, FILE_CLOSURE.MUID, FILE_CLOSURE.MU_NAME, FILE_CLOSURE.MTIME)
                .select(dsl.select(targetAncestorId, subtreeDescendantId,
                        DSL.val(spaceCode), targetDepth.add(1L).add(subtreeDepth),
                        DSL.val(-1L), DSL.val(audit.getCuid() == null ? 0L : audit.getCuid()),
                        DSL.val(audit.getCuName() == null ? "" : audit.getCuName()), DSL.val(audit.getCtime()),
                        DSL.val(audit.getMuid() == null ? 0L : audit.getMuid()),
                        DSL.val(audit.getMuName() == null ? "" : audit.getMuName()), DSL.val(audit.getMtime()))
                        .from(target)
                        .crossJoin(subtree)
                        .where(targetDescendantId.eq(snapshot.targetAncestorId))
                        .and(subtreeAncestorId.eq(fileId)))
                .execute();

        int expectedInsertCount = snapshot.targetAncestorCount * snapshot.subtreeIds.size();
        if(insertedCount != expectedInsertCount) {
            throw new IllegalStateException("insert moved file_closure failed, fileId: " + fileId);
        }
    }

    private void updateSubtreeRootDepths(DSLContext dsl, ClosureMoveSnapshot snapshot, String fileId) {
        long rootDepthOffset = snapshot.targetRootDepth + 1L;
        int updatedCount;
        if(isH2Database(dsl)) {
            updatedCount = dsl.execute("update {0} self_closure "
                            + "set root_depth = {1} + (select subtree_closure.depth from {0} subtree_closure "
                            + "where subtree_closure.ancestor_id = {2} "
                            + "and subtree_closure.descendant_id = self_closure.descendant_id) "
                            + "where self_closure.ancestor_id = self_closure.descendant_id "
                            + "and self_closure.depth = 0 "
                            + "and exists (select 1 from {0} subtree_closure "
                            + "where subtree_closure.ancestor_id = {2} "
                            + "and subtree_closure.descendant_id = self_closure.descendant_id)",
                    FILE_CLOSURE, DSL.val(rootDepthOffset), DSL.val(fileId));
        } else {
            updatedCount = dsl.execute("update {0} self_closure "
                            + "join {0} subtree_closure "
                            + "on subtree_closure.ancestor_id = {1} "
                            + "and subtree_closure.descendant_id = self_closure.descendant_id "
                            + "set self_closure.root_depth = {2} + subtree_closure.depth "
                            + "where self_closure.ancestor_id = self_closure.descendant_id "
                            + "and self_closure.depth = 0",
                    FILE_CLOSURE, DSL.val(fileId), DSL.val(rootDepthOffset));
        }
        if(updatedCount != snapshot.subtreeIds.size()) {
            throw new IllegalStateException("update file_closure root_depth failed, fileId: " + fileId);
        }
    }

    private boolean isH2Database(DSLContext dsl) {
        return dsl.dialect().family() == SQLDialect.H2;
    }

    private static class ClosureMoveSnapshot {
        private final List<String> subtreeIds;
        private final List<String> externalAncestorIds;
        private final String targetAncestorId;
        private final int targetAncestorCount;
        private final long targetRootDepth;

        ClosureMoveSnapshot(List<String> subtreeIds, List<String> externalAncestorIds, String targetAncestorId,
                int targetAncestorCount, long targetRootDepth) {
            this.subtreeIds = subtreeIds;
            this.externalAncestorIds = externalAncestorIds;
            this.targetAncestorId = targetAncestorId;
            this.targetAncestorCount = targetAncestorCount;
            this.targetRootDepth = targetRootDepth;
        }
    }

    public List<FileDB> findFiles(String spaceCode, String ancestorId) {
        if(useEntryRead()) {
            try {
                return fileEntryRepo.listFiles(spaceCode, ancestorId);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("findFiles", e);
            }
        }
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> query = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()));

        if(StringUtils.isEmpty(ancestorId)) {
            query.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
        } else {
            query.and(FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
                    .and(FILE_CLOSURE.DEPTH.eq(1L));
        }

        return query
                .orderBy(FILE.IS_DIR.desc(),
                        FILE.CTIME.desc())
                .fetchInto(FileDB.class);
    }

    private boolean useEntryRead() {
        return "entry".equalsIgnoreCase(fileEntryReadMode);
    }

    void setFileEntryReadMode(String readMode) {
        this.fileEntryReadMode = readMode;
    }

    private void logEntryReadFallback(String operation, EntryReadNotReadyException e) {
        if(!fileEntryRepo.closureWriteEnabled()) {
            // 闭包已停写，回退读只会读到陈旧数据，宁可失败暴露问题
            throw e;
        }
        LOGGER.warn("file_entry read is not ready, falling back to closure, operation: {}, reason: {}", operation, e.getMessage());
    }

    public FileNodeCount countNodes(String spaceCode, String ancestorId) {
        return fileEntryRepo.countNodes(spaceCode, ancestorId);
    }

    public List<FileDB> getPathFiles(String fileId) {
        fileId = queryNewFileId(fileId);
        if(useEntryRead() && FileType.fromFileId(fileId).needsDirectorySupport()) {
            FileDB file = queryFile(fileId);
            if(file != null) {
                try {
                    return fileEntryRepo.pathFiles(file.getSpaceCode(), fileId);
                } catch (EntryReadNotReadyException e) {
                    logEntryReadFallback("getPathFiles", e);
                }
            }
        }
        String shardingKey = getShardingKeyByFileId(fileId);

        return db(shardingKey).select(FILE.fields())
                .from(FILE)
                .innerJoin(FILE_CLOSURE)
                .on(FILE.FILE_ID.eq(FILE_CLOSURE.ANCESTOR_ID))
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .orderBy(FILE_CLOSURE.DEPTH.desc())
                .fetchInto(FileDB.class);
    }

    @Nullable
    public String getDirectAncestorId(String fileId) {
        fileId = queryNewFileId(fileId);
        if(useEntryRead() && FileType.fromFileId(fileId).needsDirectorySupport()) {
            FileDB file = queryFile(fileId);
            if(file != null) {
                try {
                    return fileEntryRepo.directAncestorFileId(file.getSpaceCode(), fileId);
                } catch (EntryReadNotReadyException e) {
                    logEntryReadFallback("getDirectAncestorId", e);
                }
            }
        }
        String shardingKey = getShardingKeyByFileId(fileId);

        return db(shardingKey).select(FILE_CLOSURE.ANCESTOR_ID)
                .from(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId)
                        .and(FILE_CLOSURE.DEPTH.eq(1L)))
                .fetchOptional()
                .map(record -> record.getValue(FILE_CLOSURE.ANCESTOR_ID))
                .orElse(null);

    }

    public FileShardingDB queryLatestFileSharding(String type) {
        return db.selectFrom(FILE_SHARDING)
                .where(FILE_SHARDING.TYPE.eq(type))
                .orderBy(FILE_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(FileShardingDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void newFileShardingTable(String lastKey, String type) {
        LocalDateTime keyTime = LocalDateTime.now().plusMinutes(10L);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));

        FileShardingRecord rec = db.selectFrom(FILE_SHARDING)
                .where(FILE_SHARDING.LAST_KEY.eq(lastKey))
                .and(FILE_SHARDING.TYPE.eq(type))
                .forUpdate().fetchOne();
        if(rec != null) {
            return;
        }

        FileType fileType = FileType.fromType(type);
        if(fileType == FileType.SYSTEM) {
            db.execute(createTableLikeSql(FILE.getName(), FileType.SYSTEM.getType(), key));
        } else if(fileType == FileType.TEMP) {
            db.execute(createTableLikeSql(FILE.getName(), FileType.TEMP.getType(), key));
            // temp 分片上存在需要进度追踪的文件（如 vision、assistants_chat），
            // 滚表时必须同步创建对应的进度分表，否则进度读写会因缺表失败
            db.execute(createTableLikeSql(FILE_PROGRESS.getName(), FileType.TEMP.getType(), key));
        }

        addFileSharding(keyTime, lastKey, key, type);
    }

    private static String createTableLikeSql(String tableName, String type, String key) {
        return String.format("create table `%s_%s_%s` like `%s_%s`", tableName, type, key, tableName, type);
    }

    private void addFileSharding(LocalDateTime keyTime, String lastKey, String key, String type) {
        FileShardingRecord rec = FILE_SHARDING.newRecord();
        rec.setKey(key);
        rec.setKeyTime(keyTime);
        rec.setLastKey(lastKey);
        rec.setType(type);
        fillCreatorInfo(rec);

        db.insertInto(FILE_SHARDING)
                .set(rec)
                .execute();
    }

    public int increaseFileShardingCount(String physicalShardingKey, long delta, String type) {
        String metadataKey = toFileShardingMetadataKey(physicalShardingKey, type);
        return db.update(FILE_SHARDING)
                .set(FILE_SHARDING.COUNT, FILE_SHARDING.COUNT.plus(delta))
                .set(FILE_SHARDING.MTIME, LocalDateTime.now())
                .where(FILE_SHARDING.KEY.eq(metadataKey))
                .and(FILE_SHARDING.TYPE.eq(type))
                .execute();
    }

    static String toFileShardingMetadataKey(String physicalShardingKey, String type) {
        if(type.equals(physicalShardingKey)) {
            return "";
        }

        String prefix = type + "_";
        if(physicalShardingKey.startsWith(prefix)) {
            return physicalShardingKey.substring(prefix.length());
        }

        throw new IllegalArgumentException(
                "Invalid physical file sharding key: " + physicalShardingKey + ", type: " + type);
    }

    public Page<FileDB> pageFiles(PageFileOps ops) {
        if(useEntryRead()) {
            String entrySpaceCode = ops.getSpaceCode();
            if(StringUtils.isEmpty(entrySpaceCode)) {
                FileDB ancestor = queryFile(ops.getAncestorId());
                if(ancestor == null) {
                    throw new FileNotFoundException(ops.getAncestorId());
                }
                entrySpaceCode = ancestor.getSpaceCode();
            }
            String normalizedFileId = ops.getFileId() == null ? null : queryNewFileId(ops.getFileId());
            try {
                return fileEntryRepo.pageFiles(entrySpaceCode, ops, normalizedFileId);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("pageFiles", e);
            }
        }

        String shardingKey;
        if(StringUtils.isNotEmpty(ops.getAncestorId())) {
            shardingKey = getShardingKeyByFileId(ops.getAncestorId());
        } else {
            shardingKey = getShardingKeyBySpaceCode(ops.getSpaceCode());
        }

        Condition whereCondition = buildWhereConditionForPageFiles(ops);

        SelectConditionStep<Record> sql = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(whereCondition);

        boolean isAsc = "asc".equalsIgnoreCase(ops.getOrder());
        SortField<?> ctimeOrder = isAsc ? FILE.CTIME.asc() : FILE.CTIME.desc();
        // 使用自增 ID 作为二级排序键，方向与 ctime 保持一致，避免相同 ctime 时分页数据重复
        SortField<?> idOrder = isAsc ? FILE.ID.asc() : FILE.ID.desc();

        sql.orderBy(FILE.IS_DIR.desc(), ctimeOrder, idOrder);

        return queryPage(db(shardingKey), sql, ops.getPage(), ops.getPageSize(), FileDB.class);

    }

    /**
     * 构造分页查询的通用 where 条件，供数据查询与计数查询复用。
     * 将 `FILE_CLOSURE` 与 `FILE` 的筛选条件分组，避免交错，提升可读性。
     */
    private Condition buildWhereConditionForPageFiles(PageFileOps ops) {
        // FILE_CLOSURE 条件分组
        Condition closureCondition;
        if(StringUtils.isNotEmpty(ops.getAncestorId())) {
            closureCondition = FILE_CLOSURE.ANCESTOR_ID.eq(ops.getAncestorId())
                    .and(FILE_CLOSURE.DEPTH.eq(1L));
        } else {
            closureCondition = FILE_CLOSURE.SPACE_CODE.eq(ops.getSpaceCode())
                    .and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
        }

        return closureCondition.and(buildFileConditionForPageFiles(ops));
    }

    private Condition buildFileConditionForPageFiles(PageFileOps ops) {
        Condition fileCondition = FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue());
        if(StringUtils.isEmpty(ops.getAncestorId())) {
            fileCondition = fileCondition.and(FILE.SPACE_CODE.eq(ops.getSpaceCode()));
        }
        if("dir".equals(ops.getType())) {
            fileCondition = fileCondition.and(FILE.IS_DIR.eq(1));
        } else if("file".equals(ops.getType())) {
            fileCondition = fileCondition.and(FILE.IS_DIR.eq(0))
                    .and(FILE.NODE_TYPE.eq(NodeType.FILE.getValue()));
        } else if("resource".equals(ops.getType())) {
            fileCondition = fileCondition.and(FILE.IS_DIR.eq(0))
                    .and(FILE.NODE_TYPE.eq(NodeType.RESOURCE.getValue()));
        }
        if(ops.getPurpose() != null) {
            fileCondition = fileCondition.and(FILE.PURPOSE.eq(ops.getPurpose()));
        }
        if(ops.getFileId() != null) {
            String normalizedId = queryNewFileId(ops.getFileId());
            fileCondition = fileCondition.and(FILE.FILE_ID.eq(normalizedId));
        }
        if(ops.getFilename() != null) {
            fileCondition = fileCondition.and(FILE.FILENAME.like(DSL.concat(DSL.escape(ops.getFilename(), '\\'), "%")));
        }
        if(ops.getExtension() != null) {
            fileCondition = fileCondition.and(FILE.EXTENSION.eq(ops.getExtension()));
        }
        if(ops.getTags() != null) {
            fileCondition = applyJsonArrayFilter(fileCondition, FILE.TAGS, ops.getTags());
        }
        if(ops.getCities() != null) {
            fileCondition = applyJsonArrayFilter(fileCondition, FILE.CITIES, ops.getCities());
        }
        if(ops.getCuid() != null) {
            fileCondition = fileCondition.and(FILE.CUID.eq(ops.getCuid()));
        }
        if(ops.getMuid() != null) {
            fileCondition = fileCondition.and(FILE.MUID.eq(ops.getMuid()));
        }

        return fileCondition;
    }

    private Condition buildContainsAnyCondition(org.jooq.Field<String> field, List<String> values) {
        Condition orCondition = null;
        for (String value : values) {
            String jsonScalar = JsonUtils.toJson(value);
            Condition single = DSL.condition("JSON_CONTAINS(COALESCE(NULLIF({0}, ''), '[]'), {1}, '$')", field, DSL.inline(jsonScalar));
            orCondition = (orCondition == null) ? single : orCondition.or(single);
        }
        return orCondition;
    }

    private Condition applyJsonArrayFilter(Condition base, org.jooq.Field<String> field, List<String> values) {
        // 空集合：仅匹配数据库中严格等于 '[]' 的记录
        if(values.isEmpty()) {
            return base.and(field.eq("[]"));
        }
        // 非空集合：任意一个命中（OR），使用 JSON_CONTAINS
        Condition orCondition = buildContainsAnyCondition(field, values);
        if(orCondition == null) {
            // 所有入参均为空串或无效，视为不加筛选
            return base;
        }
        return base.and(orCondition);
    }

    /**
     * 批量获取文件的祖先ID列表
     * 利用闭包表特性，一次查询获取所有文件的祖先路径
     *
     * @param spaceCode 空间编码，用于分表
     * @param fileIds   文件ID列表
     *
     * @return Map<String, List<String>>
     *         key为fileId，value为从根路径开始的祖先ID数组（根路径文件返回空数组）
     */
    public Map<String, List<String>> getFileAncestorIds(String spaceCode, List<String> fileIds) {
        if(CollectionUtils.isEmpty(fileIds)) {
            return Collections.emptyMap();
        }
        if(useEntryRead()) {
            try {
                return fileEntryRepo.ancestorFileIds(spaceCode, fileIds);
            } catch (EntryReadNotReadyException e) {
                logEntryReadFallback("getFileAncestorIds", e);
            }
        }
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);
        // 查询所有文件的祖先关系
        // depth > 0 排除自己，只查询祖先
        Result<Record3<String, String, Long>> records = db(shardingKey)
                .select(FILE_CLOSURE.DESCENDANT_ID, FILE_CLOSURE.ANCESTOR_ID, FILE_CLOSURE.DEPTH)
                .from(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.in(fileIds))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.DEPTH.gt(0L))
                .orderBy(FILE_CLOSURE.DESCENDANT_ID.asc(), FILE_CLOSURE.DEPTH.desc())
                .fetch();
        // 按 descendant_id 分组，并按 depth 降序排列（从根路径到直接父节点）
        Map<String, List<String>> result = new HashMap<>();
        // 初始化所有 fileId 的结果为空数组（处理根路径文件的情况）
        for (String fileId : fileIds) {
            result.put(fileId, new ArrayList<>());
        }
        // 填充祖先路径
        for (Record3<String, String, Long> record : records) {
            String descendantId = record.get(FILE_CLOSURE.DESCENDANT_ID);
            String ancestorId = record.get(FILE_CLOSURE.ANCESTOR_ID);
            result.get(descendantId).add(ancestorId);
        }
        return result;
    }
}
