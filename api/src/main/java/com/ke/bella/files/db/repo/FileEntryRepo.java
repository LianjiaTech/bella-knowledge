package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.SortField;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.tables.FileEntry;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.db.tables.records.FileClosureRecord;
import com.ke.bella.files.db.tables.records.FileEntryRecord;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.utils.DigestUtils;
import com.ke.bella.files.utils.JsonUtils;

@Component
public class FileEntryRepo implements BaseRepo {
    public static final String ROOT_ENTRY_ID = "";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_DIR = "dir";

    private final DSLContext db;

    @Value("${bella.file-api.file-entry.cross-space-move-enabled:false}")
    private boolean crossSpaceMoveEnabled;

    public FileEntryRepo(DSLContext db) {
        this.db = db;
    }

    void setCrossSpaceMoveEnabled(boolean enabled) {
        this.crossSpaceMoveEnabled = enabled;
    }

    private DSLContext entryDb(String spaceCode) {
        return DSLContextHolder.get(FileRepo.getShardingKeyBySpaceCode(spaceCode), db);
    }

    private DSLContext fileDb(String fileId) {
        return DSLContextHolder.get(FileRepo.getShardingKeyByFileIdStatic(fileId), db);
    }

    public static String legacyEntryId(String spaceCode, String fileId) {
        return "entry-legacy-" + DigestUtils.sha256(spaceCode + ":" + fileId);
    }

    public FileEntryDB queryActiveByFileId(String spaceCode, String fileId) {
        return entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.FILE_ID.eq(fileId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(FileEntryDB.class);
    }

    public FileEntryDB queryActiveByEntryId(String spaceCode, String entryId) {
        if(StringUtils.isEmpty(entryId)) {
            return null;
        }
        return entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(entryId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(FileEntryDB.class);
    }

    public FileEntryDB queryActiveByName(String spaceCode, String parentEntryId, String filename) {
        return entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.eq(parentEntryId))
                .and(FILE_ENTRY.FILENAME.eq(filename))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(FileEntryDB.class);
    }

    public boolean exists(String spaceCode, @Nullable String ancestorId, String filename) {
        return queryActiveByName(spaceCode, resolveParentEntryIdForRead(spaceCode, ancestorId), filename) != null;
    }

    public FileDB queryFile(String spaceCode, @Nullable String ancestorId, String filename) {
        FileEntryDB entry = queryActiveByName(spaceCode, resolveParentEntryIdForRead(spaceCode, ancestorId), filename);
        return entry == null ? null : queryActiveFile(entry.getFileId());
    }

    public List<FileDB> listFiles(String spaceCode, @Nullable String ancestorId) {
        String parentEntryId = resolveParentEntryIdForRead(spaceCode, ancestorId);
        List<FileEntryDB> entries = entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.eq(parentEntryId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .orderBy(DSL.when(FILE_ENTRY.TYPE.eq(TYPE_DIR), 1).otherwise(0).desc(), FILE_ENTRY.CTIME.desc())
                .fetchInto(FileEntryDB.class);
        return hydrate(entries);
    }

    public List<FileDB> listFiles(String spaceCode, @Nullable String ancestorId, @Nullable String purpose,
            int limit, String order, @Nullable FileDB afterFile) {
        String parentEntryId = resolveParentEntryIdForRead(spaceCode, ancestorId);
        List<String> fileIds = entryDb(spaceCode).select(FILE_ENTRY.FILE_ID)
                .from(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.eq(parentEntryId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchInto(String.class);
        if(fileIds.isEmpty()) {
            return Collections.emptyList();
        }

        boolean isAsc = "asc".equalsIgnoreCase(order);
        Comparator<FileDB> comparator = fileCursorComparator(isAsc);
        Map<String, List<String>> idsByShard = fileIds.stream()
                .collect(Collectors.groupingBy(FileRepo::getShardingKeyByFileIdStatic));
        List<FileDB> candidates = new ArrayList<>();
        idsByShard.forEach((shard, ids) -> {
            Condition condition = FILE.FILE_ID.in(ids).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()));
            if(StringUtils.isNotEmpty(purpose)) {
                condition = condition.and(FILE.PURPOSE.eq(purpose));
            }
            if(afterFile != null) {
                Condition sameIdAfterFileId = FILE.ID.eq(afterFile.getId())
                        .and(isAsc ? FILE.FILE_ID.gt(afterFile.getFileId()) : FILE.FILE_ID.lt(afterFile.getFileId()));
                Condition sameTimeAfterId = FILE.CTIME.eq(afterFile.getCtime())
                        .and((isAsc ? FILE.ID.gt(afterFile.getId()) : FILE.ID.lt(afterFile.getId())).or(sameIdAfterFileId));
                condition = condition.and(isAsc ? FILE.CTIME.gt(afterFile.getCtime()).or(sameTimeAfterId)
                        : FILE.CTIME.lt(afterFile.getCtime()).or(sameTimeAfterId));
            }
            SortField<?> ctimeOrder = isAsc ? FILE.CTIME.asc() : FILE.CTIME.desc();
            SortField<?> idOrder = isAsc ? FILE.ID.asc() : FILE.ID.desc();
            SortField<?> fileIdOrder = isAsc ? FILE.FILE_ID.asc() : FILE.FILE_ID.desc();
            SelectConditionStep<Record> query = DSLContextHolder.get(shard, db).select(FILE.fields())
                    .from(FILE)
                    .where(condition);
            candidates.addAll(query.orderBy(ctimeOrder, idOrder, fileIdOrder).limit(limit).fetchInto(FileDB.class));
        });
        return candidates.stream().sorted(comparator).limit(limit).collect(Collectors.toList());
    }

    public Page<FileDB> pageFiles(String spaceCode, PageFileOps ops, @Nullable String normalizedFileId) {
        String parentEntryId = resolveParentEntryIdForRead(spaceCode, ops.getAncestorId());
        List<FileEntryDB> entries = entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.eq(parentEntryId))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchInto(FileEntryDB.class);

        List<FileDB> filtered = hydrate(entries).stream()
                .filter(file -> matchesPageFilters(file, ops, normalizedFileId))
                .sorted(pageComparator(ops.getOrder()))
                .collect(Collectors.toList());
        int fromIndex = Math.min((ops.getPage() - 1) * ops.getPageSize(), filtered.size());
        int toIndex = Math.min(fromIndex + ops.getPageSize(), filtered.size());
        return Page.<FileDB>from(ops.getPage(), ops.getPageSize())
                .total(filtered.size())
                .list(new ArrayList<>(filtered.subList(fromIndex, toIndex)));
    }

    public List<FileDB> hydrate(List<FileEntryDB> entries) {
        if(entries.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<String>> idsByShard = entries.stream()
                .map(FileEntryDB::getFileId)
                .collect(Collectors.groupingBy(FileRepo::getShardingKeyByFileIdStatic));
        Map<String, FileDB> files = new LinkedHashMap<>();
        idsByShard.forEach((shard, ids) -> DSLContextHolder.get(shard, db).selectFrom(FILE)
                .where(FILE.FILE_ID.in(ids))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchInto(FileDB.class)
                .forEach(file -> files.put(file.getFileId(), file)));
        List<FileDB> result = new ArrayList<>();
        for (FileEntryDB entry : entries) {
            FileDB file = files.get(entry.getFileId());
            if(file == null) {
                throw new IllegalStateException("active file_entry points to missing file, entryId: " + entry.getEntryId());
            }
            result.add(file);
        }
        return result;
    }

    private Comparator<FileDB> fileCursorComparator(boolean isAsc) {
        Comparator<FileDB> comparator = Comparator.comparing(FileDB::getCtime)
                .thenComparing(FileDB::getId)
                .thenComparing(FileDB::getFileId);
        return isAsc ? comparator : comparator.reversed();
    }

    private boolean matchesPageFilters(FileDB file, PageFileOps ops, @Nullable String normalizedFileId) {
        if("dir".equals(ops.getType()) && !Integer.valueOf(1).equals(file.getIsDir())) {
            return false;
        }
        if("file".equals(ops.getType()) && !Integer.valueOf(0).equals(file.getIsDir())) {
            return false;
        }
        if(ops.getPurpose() != null && !ops.getPurpose().equals(file.getPurpose())) {
            return false;
        }
        if(normalizedFileId != null && !normalizedFileId.equals(file.getFileId())) {
            return false;
        }
        if(ops.getFilename() != null && (file.getFilename() == null || !file.getFilename().startsWith(ops.getFilename()))) {
            return false;
        }
        if(ops.getExtension() != null && !ops.getExtension().equals(file.getExtension())) {
            return false;
        }
        if(ops.getCuid() != null && !ops.getCuid().equals(file.getCuid())) {
            return false;
        }
        if(ops.getMuid() != null && !ops.getMuid().equals(file.getMuid())) {
            return false;
        }
        return matchesJsonFilter(file.getTags(), ops.getTags()) && matchesJsonFilter(file.getCities(), ops.getCities());
    }

    private boolean matchesJsonFilter(String rawValue, @Nullable List<String> expectedValues) {
        if(expectedValues == null) {
            return true;
        }
        if(expectedValues.isEmpty()) {
            return "[]".equals(rawValue);
        }
        List<String> actualValues = JsonUtils.fromJson(rawValue, new TypeReference<List<String>>() {
        });
        return actualValues != null && expectedValues.stream().anyMatch(actualValues::contains);
    }

    private Comparator<FileDB> pageComparator(String order) {
        Comparator<FileDB> ctimeComparator = Comparator.comparing(FileDB::getCtime,
                Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<FileDB> idComparator = Comparator.comparing(FileDB::getId,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if(!"asc".equalsIgnoreCase(order)) {
            ctimeComparator = ctimeComparator.reversed();
            idComparator = idComparator.reversed();
        }
        return Comparator.comparingInt((FileDB file) -> Objects.equals(file.getIsDir(), 1) ? 0 : 1)
                .thenComparing(ctimeComparator)
                .thenComparing(idComparator);
    }

    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB addEntry(String spaceCode, FileDB file, @Nullable String ancestorId) {
        String parentEntryId = resolveParentEntryId(spaceCode, ancestorId);
        assertNameAvailable(spaceCode, parentEntryId, file.getFilename(), null);
        FileEntryRecord record = FILE_ENTRY.newRecord();
        record.setEntryId(IDGenerator.FILE_ENTRY_ID_GEN.generate());
        record.setSpaceCode(spaceCode);
        record.setParentEntryId(parentEntryId);
        record.setFileId(file.getFileId());
        record.setFilename(file.getFilename());
        record.setType(Integer.valueOf(1).equals(file.getIsDir()) ? TYPE_DIR : TYPE_FILE);
        record.setStatus(FileStatus.NOT_DELETED.getValue());
        fillCreatorInfo(record);
        int inserted = entryDb(spaceCode).insertInto(FILE_ENTRY).set(record).execute();
        if(inserted != 1) {
            throw new IllegalStateException("insert file_entry failed, fileId: " + file.getFileId());
        }
        return queryActiveByFileId(spaceCode, file.getFileId());
    }

    private String resolveParentEntryIdForRead(String spaceCode, @Nullable String ancestorId) {
        if(StringUtils.isEmpty(ancestorId)) {
            return ROOT_ENTRY_ID;
        }
        FileEntryDB parent = queryActiveByFileId(spaceCode, ancestorId);
        if(parent == null) {
            throw new EntryReadNotReadyException(spaceCode, ancestorId);
        }
        return parent.getEntryId();
    }

    public String resolveParentEntryId(String spaceCode, @Nullable String ancestorId) {
        if(StringUtils.isEmpty(ancestorId)) {
            return ROOT_ENTRY_ID;
        }
        return ensureLegacyEntry(spaceCode, ancestorId).getEntryId();
    }

    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB ensureLegacyEntry(String spaceCode, String fileId) {
        FileEntryDB current = queryActiveByFileId(spaceCode, fileId);
        if(current != null) {
            validateEntry(current, spaceCode, fileId);
            return current;
        }

        FileDB file = queryActiveFile(spaceCode, fileId);
        if(file == null || !spaceCode.equals(file.getSpaceCode())) {
            throw new IllegalStateException("cannot build legacy file_entry for fileId: " + fileId);
        }
        String parentFileId = entryDb(spaceCode).select(FILE_CLOSURE.ANCESTOR_ID)
                .from(FILE_CLOSURE)
                .where(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
                .and(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .and(FILE_CLOSURE.DEPTH.eq(1L))
                .fetchOneInto(String.class);
        String parentEntryId = StringUtils.isEmpty(parentFileId) ? ROOT_ENTRY_ID : ensureLegacyEntry(spaceCode, parentFileId).getEntryId();

        FileEntryRecord record = FILE_ENTRY.newRecord();
        record.setEntryId(legacyEntryId(spaceCode, fileId));
        record.setSpaceCode(spaceCode);
        record.setParentEntryId(parentEntryId);
        record.setFileId(fileId);
        record.setFilename(file.getFilename());
        record.setType(Integer.valueOf(1).equals(file.getIsDir()) ? TYPE_DIR : TYPE_FILE);
        record.setStatus(FileStatus.NOT_DELETED.getValue());
        fillCreatorInfo(record);
        try {
            entryDb(spaceCode).insertInto(FILE_ENTRY).set(record).execute();
        } catch (DataAccessException e) {
            if(!isIntegrityConstraintViolation(e)) {
                throw e;
            }
            // Concurrent backfill/create converges on the deterministic legacy ID.
        }
        FileEntryDB created = queryActiveByFileId(spaceCode, fileId);
        validateEntry(created, spaceCode, fileId);
        if(!parentEntryId.equals(created.getParentEntryId()) || !file.getFilename().equals(created.getFilename())) {
            throw new IllegalStateException("legacy file_entry conflicts with closure/file state, fileId: " + fileId);
        }
        return created;
    }

    static boolean isIntegrityConstraintViolation(DataAccessException e) {
        return e.sqlStateClass() == SQLStateClass.C23_INTEGRITY_CONSTRAINT_VIOLATION;
    }

    public void rename(String spaceCode, String fileId, String filename) {
        FileEntryDB entry = ensureLegacyEntry(spaceCode, fileId);
        assertNameAvailable(spaceCode, entry.getParentEntryId(), filename, entry.getEntryId());
        int updated = entryDb(spaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.FILENAME, filename)
                .where(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .execute();
        if(updated != 1) {
            throw new IllegalStateException("rename file_entry failed, fileId: " + fileId);
        }
    }

    public void move(String spaceCode, String fileId, @Nullable String targetAncestorId) {
        FileEntryDB entry = ensureLegacyEntry(spaceCode, fileId);
        String parentEntryId = resolveParentEntryId(spaceCode, targetAncestorId);
        if(entry.getEntryId().equals(parentEntryId)) {
            throw new IllegalArgumentException("cannot move file_entry under itself, fileId: " + fileId);
        }
        assertNameAvailable(spaceCode, parentEntryId, entry.getFilename(), entry.getEntryId());
        int updated = entryDb(spaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.PARENT_ENTRY_ID, parentEntryId)
                .where(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .execute();
        if(updated != 1) {
            throw new IllegalStateException("move file_entry failed, fileId: " + fileId);
        }
    }

    public void delete(String spaceCode, String fileId) {
        FileEntryDB entry = ensureLegacyEntry(spaceCode, fileId);
        int activeCount = entryDb(spaceCode).fetchCount(FILE_ENTRY,
                FILE_ENTRY.FILE_ID.eq(fileId).and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue())));
        if(activeCount != 1) {
            throw new IllegalStateException("file must have exactly one active entry, fileId: " + fileId);
        }
        int updated = entryDb(spaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.STATUS, FileStatus.DELETED.getValue())
                .where(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .execute();
        if(updated != 1) {
            throw new IllegalStateException("delete file_entry failed, fileId: " + fileId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB moveAcrossSpace(String fileId, String targetSpaceCode, @Nullable String targetAncestorId) {
        if(!crossSpaceMoveEnabled) {
            throw new IllegalStateException("cross-space file entry move is disabled");
        }
        FileDB file = queryActiveFile(fileId);
        if(file == null) {
            throw new IllegalStateException("file not found, fileId: " + fileId);
        }
        String sourceSpaceCode = file.getSpaceCode();
        FileEntryDB source = ensureLegacyEntry(sourceSpaceCode, fileId);
        if(TYPE_DIR.equals(source.getType()) && entryDb(sourceSpaceCode).fetchCount(FILE_ENTRY,
                FILE_ENTRY.PARENT_ENTRY_ID.eq(source.getEntryId())
                        .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))) > 0) {
            throw new IllegalArgumentException("cross-space move of non-empty directories is not supported");
        }
        String targetParentEntryId = resolveParentEntryId(targetSpaceCode, targetAncestorId);
        assertNameAvailable(targetSpaceCode, targetParentEntryId, source.getFilename(), null);
        FileEntryRecord target = FILE_ENTRY.newRecord();
        target.setEntryId(IDGenerator.FILE_ENTRY_ID_GEN.generate());
        target.setSpaceCode(targetSpaceCode);
        target.setParentEntryId(targetParentEntryId);
        target.setFileId(fileId);
        target.setFilename(source.getFilename());
        target.setType(source.getType());
        target.setStatus(FileStatus.NOT_DELETED.getValue());
        fillCreatorInfo(target);
        entryDb(targetSpaceCode).insertInto(FILE_ENTRY).set(target).execute();
        int sourceUpdated = entryDb(sourceSpaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.STATUS, FileStatus.DELETED.getValue())
                .where(FILE_ENTRY.ENTRY_ID.eq(source.getEntryId()))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .execute();
        if(sourceUpdated != 1) {
            throw new IllegalStateException("delete source file_entry failed, fileId: " + fileId);
        }
        moveLeafClosureAcrossSpace(fileId, sourceSpaceCode, targetSpaceCode, targetAncestorId);
        int updated = fileDb(fileId).update(FILE).set(FILE.SPACE_CODE, targetSpaceCode)
                .where(FILE.FILE_ID.eq(fileId)).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())).execute();
        if(updated != 1) {
            throw new IllegalStateException("update file space cache failed, fileId: " + fileId);
        }
        return queryActiveByFileId(targetSpaceCode, fileId);
    }

    private void moveLeafClosureAcrossSpace(String fileId, String sourceSpaceCode, String targetSpaceCode,
            @Nullable String targetAncestorId) {
        DSLContext sourceDsl = entryDb(sourceSpaceCode);
        DSLContext targetDsl = entryDb(targetSpaceCode);
        int deleted = sourceDsl.deleteFrom(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .and(FILE_CLOSURE.SPACE_CODE.eq(sourceSpaceCode))
                .execute();
        if(deleted == 0) {
            throw new IllegalStateException("source file_closure not found, fileId: " + fileId);
        }

        long rootDepth = 1L;
        if(StringUtils.isNotEmpty(targetAncestorId)) {
            List<FileClosureRecord> ancestors = targetDsl.selectFrom(FILE_CLOSURE)
                    .where(FILE_CLOSURE.DESCENDANT_ID.eq(targetAncestorId))
                    .and(FILE_CLOSURE.SPACE_CODE.eq(targetSpaceCode))
                    .orderBy(FILE_CLOSURE.DEPTH.asc())
                    .fetchInto(FileClosureRecord.class);
            if(ancestors.isEmpty()) {
                throw new IllegalStateException("target ancestor closure not found, fileId: " + targetAncestorId);
            }
            rootDepth = ancestors.size() + 1L;
            for (FileClosureRecord ancestor : ancestors) {
                insertClosure(targetDsl, targetSpaceCode, ancestor.getAncestorId(), fileId, ancestor.getDepth() + 1L, -1L);
            }
        }
        insertClosure(targetDsl, targetSpaceCode, fileId, fileId, 0L, rootDepth);
    }

    private void insertClosure(DSLContext dsl, String spaceCode, String ancestorId, String descendantId, long depth,
            long rootDepth) {
        FileClosureRecord record = FILE_CLOSURE.newRecord();
        record.setSpaceCode(spaceCode);
        record.setAncestorId(ancestorId);
        record.setDescendantId(descendantId);
        record.setDepth(depth);
        record.setRootDepth(rootDepth);
        fillCreatorInfo(record);
        if(dsl.insertInto(FILE_CLOSURE).set(record).execute() != 1) {
            throw new IllegalStateException("insert target file_closure failed, fileId: " + descendantId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public BackfillBatchResult backfillBatch(String spaceCode, long minIdInclusive, long maxIdExclusive, int batchSize) {
        List<Record2<Long, String>> files = entryDb(spaceCode).select(FILE.ID, FILE.FILE_ID)
                .from(FILE)
                .where(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .and(FILE.ID.ge(minIdInclusive))
                .and(FILE.ID.lt(maxIdExclusive))
                .orderBy(FILE.ID.asc())
                .limit(batchSize)
                .fetch();
        files.forEach(file -> ensureLegacyEntry(spaceCode, file.value2()));
        long nextMinId = files.isEmpty() ? maxIdExclusive : files.get(files.size() - 1).value1() + 1;
        return new BackfillBatchResult(files.size(), nextMinId);
    }

    public EntryConsistencyReport compareSpace(String spaceCode) {
        DSLContext dsl = entryDb(spaceCode);
        FileEntry parent = FILE_ENTRY.as("parent");
        int activeFileCount = dsl.fetchCount(FILE,
                FILE.SPACE_CODE.eq(spaceCode).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())));
        int activeEntryCount = dsl.fetchCount(FILE_ENTRY,
                FILE_ENTRY.SPACE_CODE.eq(spaceCode).and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue())));
        int duplicateFileCount = dsl.fetchCount(dsl.select(FILE_ENTRY.FILE_ID)
                .from(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .groupBy(FILE_ENTRY.FILE_ID)
                .having(DSL.count().ne(1)));
        int orphanParentCount = dsl.fetchCount(dsl.select(FILE_ENTRY.ENTRY_ID)
                .from(FILE_ENTRY)
                .leftJoin(parent)
                .on(FILE_ENTRY.PARENT_ENTRY_ID.eq(parent.ENTRY_ID))
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.ne(ROOT_ENTRY_ID))
                .and(parent.ENTRY_ID.isNull()));
        return new EntryConsistencyReport(activeFileCount, activeEntryCount, duplicateFileCount, orphanParentCount);
    }

    public static class EntryReadNotReadyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        EntryReadNotReadyException(String spaceCode, String ancestorId) {
            super("file_entry parent is not backfilled, spaceCode: " + spaceCode + ", ancestorId: " + ancestorId);
        }
    }

    public static class BackfillBatchResult {
        private final int processed;
        private final long nextMinId;

        BackfillBatchResult(int processed, long nextMinId) {
            this.processed = processed;
            this.nextMinId = nextMinId;
        }

        public int getProcessed() {
            return processed;
        }

        public long getNextMinId() {
            return nextMinId;
        }
    }

    public static class EntryConsistencyReport {
        private final int activeFileCount;
        private final int activeEntryCount;
        private final int duplicateFileCount;
        private final int orphanParentCount;

        public EntryConsistencyReport(int activeFileCount, int activeEntryCount, int duplicateFileCount, int orphanParentCount) {
            this.activeFileCount = activeFileCount;
            this.activeEntryCount = activeEntryCount;
            this.duplicateFileCount = duplicateFileCount;
            this.orphanParentCount = orphanParentCount;
        }

        public int getActiveFileCount() { return activeFileCount; }
        public int getActiveEntryCount() { return activeEntryCount; }
        public int getDuplicateFileCount() { return duplicateFileCount; }
        public int getOrphanParentCount() { return orphanParentCount; }
        public boolean isConsistent() {
            return activeFileCount == activeEntryCount && duplicateFileCount == 0 && orphanParentCount == 0;
        }
    }

    private FileDB queryActiveFile(String fileId) {
        return fileDb(fileId).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(FileDB.class);
    }

    private FileDB queryActiveFile(String spaceCode, String fileId) {
        return entryDb(spaceCode).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .fetchOneInto(FileDB.class);
    }

    private void assertNameAvailable(String spaceCode, String parentEntryId, String filename, @Nullable String currentEntryId) {
        FileEntryDB conflict = queryActiveByName(spaceCode, parentEntryId, filename);
        if(conflict != null && !conflict.getEntryId().equals(currentEntryId)) {
            throw new IllegalStateException("active file_entry already exists, filename: " + filename);
        }
    }

    private void validateEntry(FileEntryDB entry, String spaceCode, String fileId) {
        if(entry == null || !spaceCode.equals(entry.getSpaceCode()) || !fileId.equals(entry.getFileId())) {
            throw new IllegalStateException("invalid file_entry state, fileId: " + fileId);
        }
    }
}
