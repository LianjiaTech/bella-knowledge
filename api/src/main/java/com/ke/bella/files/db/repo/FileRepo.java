package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;
import static com.ke.bella.files.db.Tables.FILE_SHARDING;
import static com.ke.bella.files.db.repo.DSLContextHolder.targetTableName;
import static org.jooq.impl.DSL.field;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.UpdateSetMoreStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.ke.bella.files.db.FileIdGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.db.tables.pojos.FileShardingDB;
import com.ke.bella.files.db.tables.records.FileProgressRecord;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.db.tables.records.FileShardingRecord;
import com.ke.bella.files.enums.FileType;
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
    private final DSLContext db;
    private final FileEntryRepo fileEntryRepo;

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
        return fileEntryRepo.exists(spaceCode, ancestorId, filename);
    }

    public FileDB queryFile(String spaceCode, @Nullable String ancestorId, @NotNull String filename) {
        return fileEntryRepo.queryFile(spaceCode, ancestorId, filename);
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

    @Transactional(rollbackFor = Exception.class)
    public void updateCreatorInfo(String fileId, Long cuid, String cuName, LocalDateTime ctime) {
        Map<Field<?>, Object> values = new HashMap<>();
        if(cuid != null) {
            values.put(FILE.CUID, cuid);
        }
        if(cuName != null) {
            values.put(FILE.CU_NAME, cuName);
        }
        if(ctime != null) {
            values.put(FILE.CTIME, ctime);
        }
        Assert.notEmpty(values, "at least one creator field is required");
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        int updatedNum = db(shardingKey).update(FILE)
                .set(values)
                .where(FILE.FILE_ID.eq(fileId))
                .execute();

        if(updatedNum != 1) {
            throw new IllegalStateException("update file creator failed, fileId: " + fileId);
        }
    }

    public List<FileDB> listFile(
            String purpose, Integer limit, String order, String after, String spaceCode, String ancestorId) {
        FileDB afterFile = null;
        if(StringUtils.isNotEmpty(after)) {
            afterFile = queryFile(after);
            if(afterFile == null) return Collections.emptyList();
        }
        return fileEntryRepo.listFiles(spaceCode, ancestorId, purpose, limit, order, afterFile);
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

    public List<FileProgressDB> queryProgresses(String fileId) {
        fileId = queryNewFileId(fileId);
        FileType fileType = FileType.fromFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        return db(shardingKey).selectFrom(FILE_PROGRESS)
                .where(FILE_PROGRESS.FILE_ID.eq(fileId))
                .orderBy(FILE_PROGRESS.MTIME.desc(), FILE_PROGRESS.NAME.asc())
                .fetchInto(FileProgressDB.class);
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

    @Transactional(rollbackFor = Exception.class)
    public void moveFile(FileDB file, @Nullable FileDB targetAncestor) {
        fileEntryRepo.move(file.getSpaceCode(), file.getFileId(), targetAncestor == null ? null : targetAncestor.getFileId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void moveFileAcrossSpace(FileDB file, String targetSpaceCode, @Nullable FileDB targetAncestor) {
        fileEntryRepo.moveAcrossSpace(file, targetSpaceCode, targetAncestor);
    }

    public List<FileDB> findFiles(String spaceCode, String ancestorId) {
        return fileEntryRepo.listFiles(spaceCode, ancestorId);
    }

    public FileNodeCount countNodes(String spaceCode, String ancestorId) {
        return fileEntryRepo.countNodes(spaceCode, ancestorId);
    }

    public List<FileDB> getPathFiles(String fileId) {
        fileId = queryNewFileId(fileId);
        FileDB file = queryFile(fileId);
        if(file == null || !FileType.fromFileId(fileId).needsDirectorySupport()) return Collections.emptyList();
        return fileEntryRepo.pathFiles(file.getSpaceCode(), fileId);
    }

    @Nullable
    public String getDirectAncestorId(String fileId) {
        fileId = queryNewFileId(fileId);
        FileDB file = queryFile(fileId);
        if(file == null || !FileType.fromFileId(fileId).needsDirectorySupport()) return null;
        return fileEntryRepo.directAncestorFileId(file.getSpaceCode(), fileId);
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
        String spaceCode = ops.getSpaceCode();
        if(StringUtils.isNotEmpty(ops.getAncestorId())) {
            FileDB ancestor = queryFile(ops.getAncestorId());
            if(ancestor == null) return Page.<FileDB>from(ops.getPage(), ops.getPageSize()).total(0).list(Collections.emptyList());
            spaceCode = ancestor.getSpaceCode();
        }
        String normalizedFileId = ops.getFileId() == null ? null : queryNewFileId(ops.getFileId());
        return fileEntryRepo.pageFiles(spaceCode, ops, normalizedFileId);
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

    public Map<String, List<String>> getFileAncestorIds(String spaceCode, List<String> fileIds) {
        if(CollectionUtils.isEmpty(fileIds)) return Collections.emptyMap();
        return fileEntryRepo.ancestorFileIds(spaceCode, fileIds);
    }
}
