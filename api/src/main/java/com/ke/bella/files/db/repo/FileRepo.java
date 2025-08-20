package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;
import static com.ke.bella.files.db.repo.DSLContextHolder.targetTableName;
import static org.jooq.impl.DSL.field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.UpdateSetMoreStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.db.tables.records.FileClosureRecord;
import com.ke.bella.files.db.tables.records.FileProgressRecord;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.CustomStringUtils;

@Component
public class FileRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    public FileRepo(DSLContext db) {
        this.db = db;
    }

    private static String getShardingKeyByFileId(String fileId) {
        String[] parts = fileId.split("-");
        if(parts.length != 3) {
            throw new FileNotFoundException(fileId);
        }
        Integer spaceCodeHash = Integer.valueOf(parts[parts.length - 1]);
        return getShardingKeyBySpaceCodeHash(spaceCodeHash);
    }

    private static String getShardingKeyBySpaceCode(String spaceCode) {
        Integer hashCode = CustomStringUtils.hashCode(spaceCode);
        return getShardingKeyBySpaceCodeHash(hashCode);
    }

    private static String getShardingKeyBySpaceCodeHash(Integer spaceCodeHash) {
        spaceCodeHash = Math.abs(spaceCodeHash);
        return Integer.toString(spaceCodeHash % 16);
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
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record1<Integer>> sql = db(shardingKey).selectOne()
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.FILENAME.eq(filename))
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
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> sql = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
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
    public void addFile(FileDB fileDB, String ancestorId) {
        String shardingKey = getShardingKeyBySpaceCode(fileDB.getSpaceCode());
        FileRecord rec = FILE.newRecord();
        rec.from(fileDB);
        fillCreatorInfo(rec);
        int insertedNum = db(shardingKey).insertInto(FILE)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert file failed, fileId: " + fileDB.getFileId());
        }

        addFileClosures(fileDB.getFileId(), ancestorId);
    }

    public void updateFile(FileOps op, boolean increaseVersion) {
        String fileId = queryNewFileId(op.getFileId());
        String shardingKey = getShardingKeyByFileId(fileId);
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

        fillUpdatorInfo(rec);

        UpdateSetMoreStep<FileRecord> sql = db(shardingKey).update(FILE)
                .set(rec);
        if(increaseVersion) {
            sql.set(FILE.VERSION, FILE.VERSION.add(1));
        }
        int updatedNum = sql
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .execute();

        if(updatedNum != 1) {
            throw new IllegalStateException("update file failed, fileId: " + fileId);
        }
    }

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
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> query = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode));

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
        String shardingKey = getShardingKeyByFileId(fileId);
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
        String shardingKey = getShardingKeyByFileId(fileId);
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
            throw new IllegalStateException("insert progress failed, fileId: " + fileId + ", progressName: " + progressName);
        }

    }

    public void updateProgress(
            String fileId,
            String progressName,
            String status,
            String message,
            Integer percent) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
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
            throw new IllegalStateException("update progress failed, fileId: " + fileId + ", progressName: " + progressName);
        }
    }

    public List<FileDB> getFiles(ListFileOps ops) {
        Map<String, List<String>> shardToFileIds = ops.getFileIds().stream()
                .collect(Collectors.groupingBy(FileRepo::getShardingKeyByFileId));

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

    private InsertSetMoreStep<FileClosureRecord> createFileClosureInsert(DSLContext dsl, String fileId, String ancestorId,
            Long depth) {
        return createFileClosureInsert(dsl, fileId, ancestorId, depth, -1L);
    }

    private InsertSetMoreStep<FileClosureRecord> createFileClosureInsert(DSLContext dsl, String fileId, String ancestorId,
            Long depth,
            Long rootDepth) {
        FileClosureRecord rec = FILE_CLOSURE.newRecord();
        rec.setSpaceCode(BellaContextHelper.getOperateSpaceCode());
        rec.setAncestorId(ancestorId);
        rec.setDescendantId(fileId);
        rec.setDepth(depth);
        rec.setRootDepth(rootDepth);
        fillCreatorInfo(rec);

        return dsl.insertInto(FILE_CLOSURE).set(rec);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addFileClosures(String fileId, String ancestorId) {
        String shardingKey = getShardingKeyByFileId(fileId);
        DSLContext dsl = db(shardingKey);
        List<InsertSetMoreStep<FileClosureRecord>> inserts = new ArrayList<>();

        long rootDepth = 1L;

        if(StringUtils.isNotEmpty(ancestorId)) {
            List<FileClosureRecord> ancestorClosures = dsl.selectFrom(FILE_CLOSURE)
                    .where(FILE_CLOSURE.DESCENDANT_ID.eq(ancestorId))
                    .orderBy(FILE_CLOSURE.DEPTH.asc())
                    .fetchInto(FileClosureRecord.class);

            Assert.isTrue(!CollectionUtils.isEmpty(ancestorClosures),
                    "descendant_id not found in file_closure, descendant_id: " + ancestorId);

            rootDepth = (long) (ancestorClosures.size() + 1);

            for (FileClosureRecord ancestorClosure : ancestorClosures) {
                inserts.add(createFileClosureInsert(dsl, fileId, ancestorClosure.getAncestorId(), ancestorClosure.getDepth() + 1));
            }
        }

        inserts.add(createFileClosureInsert(dsl, fileId, fileId, 0L, rootDepth));

        int[] results = dsl.batch(inserts).execute();
        if(results.length != inserts.size()) {
            throw new IllegalStateException("batch insert file_closure failed, fileId: " + fileId);
        }
    }

    public void deleteFileClosure(String fileId) {
        String shardingKey = getShardingKeyByFileId(fileId);
        db(shardingKey).delete(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .or(FILE_CLOSURE.ANCESTOR_ID.eq(fileId))
                .execute();
    }

    public List<FileDB> findFiles(
            String ancestorId) {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);

        SelectConditionStep<Record> query = db(shardingKey).select(FILE.fields())
                .from(FILE_CLOSURE)
                .innerJoin(FILE)
                .on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
                .where(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode));

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

    public List<FileDB> getPathFiles(String fileId) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);

        return db(shardingKey).select(FILE.fields())
                .from(FILE)
                .innerJoin(FILE_CLOSURE)
                .on(FILE.FILE_ID.eq(FILE_CLOSURE.ANCESTOR_ID))
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .orderBy(FILE_CLOSURE.DEPTH.desc())
                .fetchInto(FileDB.class);
    }
}
