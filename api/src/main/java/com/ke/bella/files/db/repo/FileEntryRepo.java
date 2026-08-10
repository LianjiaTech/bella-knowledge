package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
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
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileEntryDB;
import com.ke.bella.files.db.tables.records.FileClosureRecord;
import com.ke.bella.files.db.tables.records.FileEntryRecord;
import com.ke.bella.files.enums.NodeType;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.utils.DigestUtils;
import com.ke.bella.files.utils.JsonUtils;

@Component
public class FileEntryRepo implements BaseRepo {
    public static final String ROOT_ENTRY_ID = "";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_DIR = "dir";
    // 祖先链遍历的深度上限，超出视为数据成环等异常
    static final int MAX_TREE_DEPTH = 64;

    private final DSLContext db;

    @Value("${bella.file-api.file-entry.cross-space-move-enabled:false}")
    private boolean crossSpaceMoveEnabled;

    /**
     * dual（默认）：闭包表和 entry 双写，任一失败整体回滚，保证两边严格一致；
     * entry：停写闭包，entry 是唯一记录，闭包读回退同时关闭；
     * closure：逃生阀——完全停写 entry，退回纯闭包链路。用于 dual 阶段 entry 侧出问题时不发版止血；
     * 切回 dual 后窗口期缺失的 entry 由懒迁移/回填补齐，切 entry 读之前必须重跑回填校验。
     * 下闭包表的路径：write-mode 切 entry → 观察 → drop 表，中间不需要发布。
     */
    @Value("${bella.file-api.file-entry.write-mode:dual}")
    private String writeMode;

    public FileEntryRepo(DSLContext db) {
        this.db = db;
    }

    void setCrossSpaceMoveEnabled(boolean enabled) {
        this.crossSpaceMoveEnabled = enabled;
    }

    void setFileEntryWriteMode(String writeMode) {
        this.writeMode = writeMode;
    }

    public boolean closureWriteEnabled() {
        return !"entry".equalsIgnoreCase(writeMode);
    }

    public boolean entryWriteEnabled() {
        return !"closure".equalsIgnoreCase(writeMode);
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
                .fetchOneInto(FileEntryDB.class);
    }

    public FileEntryDB queryActiveByEntryId(String spaceCode, String entryId) {
        if(StringUtils.isEmpty(entryId)) {
            return null;
        }
        return entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(entryId))
                .fetchOneInto(FileEntryDB.class);
    }

    public FileEntryDB queryActiveByName(String spaceCode, String parentEntryId, String filename) {
        return entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.PARENT_ENTRY_ID.eq(parentEntryId))
                .and(FILE_ENTRY.FILENAME.eq(filename))
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
            // 与闭包版 listFile 对齐：列表接口不返回 resource 节点
            Condition condition = FILE.FILE_ID.in(ids)
                    .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                    .and(FILE.NODE_TYPE.ne(NodeType.RESOURCE.getValue()));
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

    /**
     * 从根到自身的完整路径（含自身），沿 parent_entry_id 逐级点查，深度即目录层级。
     */
    public List<FileDB> pathFiles(String spaceCode, String fileId) {
        FileEntryDB entry = queryActiveByFileId(spaceCode, fileId);
        if(entry == null) {
            throw new EntryReadNotReadyException(spaceCode, fileId);
        }
        return hydrate(chainToRoot(spaceCode, entry));
    }

    @Nullable
    public String directAncestorFileId(String spaceCode, String fileId) {
        FileEntryDB entry = queryActiveByFileId(spaceCode, fileId);
        if(entry == null) {
            throw new EntryReadNotReadyException(spaceCode, fileId);
        }
        if(ROOT_ENTRY_ID.equals(entry.getParentEntryId())) {
            return null;
        }
        FileEntryDB parent = queryActiveByEntryId(spaceCode, entry.getParentEntryId());
        if(parent == null) {
            throw new EntryReadNotReadyException(spaceCode, entry.getParentEntryId());
        }
        return parent.getFileId();
    }

    /**
     * 批量祖先链：按层聚合 IN 查询解析父链，查询次数与最大树深同阶，而非文件数。
     * 无 entry 且 file 已删除/不存在的 fileId 返回空数组（与闭包实现一致）；
     * 无 entry 但 file 仍活跃说明数据未迁移完成，抛 EntryReadNotReadyException 由调用方回退闭包读。
     */
    public Map<String, List<String>> ancestorFileIds(String spaceCode, List<String> fileIds) {
        Map<String, List<String>> result = new HashMap<>();
        for (String fileId : fileIds) {
            result.put(fileId, new ArrayList<>());
        }
        if(fileIds.isEmpty()) {
            return result;
        }

        Map<String, FileEntryDB> entriesByEntryId = new HashMap<>();
        Map<String, FileEntryDB> entriesByFileId = new HashMap<>();
        entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.FILE_ID.in(fileIds))
                .fetchInto(FileEntryDB.class)
                .forEach(entry -> {
                    entriesByFileId.put(entry.getFileId(), entry);
                    entriesByEntryId.put(entry.getEntryId(), entry);
                });
        assertNoActiveFileMissingEntry(spaceCode, fileIds, entriesByFileId.keySet());

        Set<String> unresolved = entriesByFileId.values().stream()
                .map(FileEntryDB::getParentEntryId)
                .filter(parentEntryId -> !ROOT_ENTRY_ID.equals(parentEntryId))
                .collect(Collectors.toSet());
        int depth = 0;
        while (!unresolved.isEmpty()) {
            if(++depth > MAX_TREE_DEPTH) {
                throw new IllegalStateException("file_entry ancestor chain exceeds max depth, spaceCode: " + spaceCode);
            }
            List<FileEntryDB> parents = entryDb(spaceCode).selectFrom(FILE_ENTRY)
                    .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                    .and(FILE_ENTRY.ENTRY_ID.in(unresolved))
                    .fetchInto(FileEntryDB.class);
            if(parents.size() < unresolved.size()) {
                parents.forEach(parent -> unresolved.remove(parent.getEntryId()));
                throw new EntryReadNotReadyException(spaceCode, unresolved.iterator().next());
            }
            Set<String> next = new HashSet<>();
            for (FileEntryDB parent : parents) {
                entriesByEntryId.put(parent.getEntryId(), parent);
                String grandParentEntryId = parent.getParentEntryId();
                if(!ROOT_ENTRY_ID.equals(grandParentEntryId) && !entriesByEntryId.containsKey(grandParentEntryId)) {
                    next.add(grandParentEntryId);
                }
            }
            unresolved.clear();
            unresolved.addAll(next);
        }

        for (Map.Entry<String, FileEntryDB> mapping : entriesByFileId.entrySet()) {
            List<String> ancestors = new ArrayList<>();
            FileEntryDB current = mapping.getValue();
            while (!ROOT_ENTRY_ID.equals(current.getParentEntryId())) {
                if(ancestors.size() >= MAX_TREE_DEPTH) {
                    throw new IllegalStateException("file_entry ancestor chain exceeds max depth, fileId: " + mapping.getKey());
                }
                current = entriesByEntryId.get(current.getParentEntryId());
                ancestors.add(current.getFileId());
            }
            Collections.reverse(ancestors);
            result.put(mapping.getKey(), ancestors);
        }
        return result;
    }

    private void assertNoActiveFileMissingEntry(String spaceCode, List<String> fileIds, Set<String> foundFileIds) {
        List<String> missing = fileIds.stream()
                .filter(fileId -> !foundFileIds.contains(fileId))
                .collect(Collectors.toList());
        if(missing.isEmpty()) {
            return;
        }
        String activeMissing = entryDb(spaceCode).select(FILE.FILE_ID)
                .from(FILE)
                .where(FILE.FILE_ID.in(missing))
                .and(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
                .limit(1)
                .fetchOneInto(String.class);
        if(activeMissing != null) {
            throw new EntryReadNotReadyException(spaceCode, activeMissing);
        }
    }

    /**
     * 沿目标父节点的祖先链向上校验，禁止把节点移入自身或其子孙。
     * 闭包表停写后这是唯一的防环校验，不能依赖闭包 move 里的同名检查。
     */
    private void assertNotSelfOrDescendant(String spaceCode, FileEntryDB entry, String targetParentEntryId) {
        String cursor = targetParentEntryId;
        int depth = 0;
        while (!ROOT_ENTRY_ID.equals(cursor)) {
            if(cursor.equals(entry.getEntryId())) {
                throw new IllegalArgumentException("cannot move a node into itself or its descendant, fileId: " + entry.getFileId());
            }
            if(++depth > MAX_TREE_DEPTH) {
                throw new IllegalStateException("file_entry ancestor chain exceeds max depth, entryId: " + targetParentEntryId);
            }
            FileEntryDB parent = queryActiveByEntryId(spaceCode, cursor);
            if(parent == null) {
                throw new EntryReadNotReadyException(spaceCode, cursor);
            }
            cursor = parent.getParentEntryId();
        }
    }

    private List<FileEntryDB> chainToRoot(String spaceCode, FileEntryDB entry) {
        LinkedList<FileEntryDB> chain = new LinkedList<>();
        chain.addFirst(entry);
        FileEntryDB current = entry;
        while (!ROOT_ENTRY_ID.equals(current.getParentEntryId())) {
            if(chain.size() > MAX_TREE_DEPTH) {
                throw new IllegalStateException("file_entry ancestor chain exceeds max depth, fileId: " + entry.getFileId());
            }
            FileEntryDB parent = queryActiveByEntryId(spaceCode, current.getParentEntryId());
            if(parent == null) {
                throw new EntryReadNotReadyException(spaceCode, current.getParentEntryId());
            }
            chain.addFirst(parent);
            current = parent;
        }
        return chain;
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
        if("file".equals(ops.getType())
                && (!Integer.valueOf(0).equals(file.getIsDir()) || !NodeType.FILE.getValue().equals(file.getNodeType()))) {
            return false;
        }
        if("resource".equals(ops.getType())
                && (!Integer.valueOf(0).equals(file.getIsDir()) || !NodeType.RESOURCE.getValue().equals(file.getNodeType()))) {
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

    /**
     * entry 写失败一律抛出，让外层主事务整体回滚，file/闭包/entry 严格一致。
     * write-mode=closure 是逃生阀：entry 写全部空操作，退回纯闭包链路。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB addEntry(String spaceCode, FileDB file, @Nullable String ancestorId) {
        if(!entryWriteEnabled()) {
            return null;
        }
        String parentEntryId = resolveParentEntryId(spaceCode, ancestorId);
        assertNameAvailable(spaceCode, parentEntryId, file.getFilename(), null);
        FileEntryRecord record = FILE_ENTRY.newRecord();
        record.setEntryId(IDGenerator.FILE_ENTRY_ID_GEN.generate());
        record.setSpaceCode(spaceCode);
        record.setParentEntryId(parentEntryId);
        record.setFileId(file.getFileId());
        record.setFilename(file.getFilename());
        record.setType(Integer.valueOf(1).equals(file.getIsDir()) ? TYPE_DIR : TYPE_FILE);
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
        if(!closureWriteEnabled()) {
            // 闭包已停写，数据可能陈旧，不能用于重建 entry；活跃文件缺 entry 说明数据有洞，必须报错
            throw new IllegalStateException("file_entry missing while closure is no longer authoritative, fileId: " + fileId);
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
        fillCreatorInfo(record);
        try {
            entryDb(spaceCode).insertInto(FILE_ENTRY).set(record).execute();
        } catch (DataAccessException e) {
            if(!isIntegrityConstraintViolation(e)) {
                throw e;
            }
            // Concurrent legacy-entry creation converges on the deterministic ID.
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
        if(!entryWriteEnabled()) {
            return;
        }
        FileEntryDB entry = ensureLegacyEntry(spaceCode, fileId);
        assertNameAvailable(spaceCode, entry.getParentEntryId(), filename, entry.getEntryId());
        int updated = entryDb(spaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.FILENAME, filename)
                .where(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
                .execute();
        if(updated != 1) {
            throw new IllegalStateException("rename file_entry failed, fileId: " + fileId);
        }
    }

    public void move(String spaceCode, String fileId, @Nullable String targetAncestorId) {
        if(!entryWriteEnabled()) {
            return;
        }
        FileEntryDB entry = ensureLegacyEntry(spaceCode, fileId);
        String parentEntryId = resolveParentEntryId(spaceCode, targetAncestorId);
        assertNotSelfOrDescendant(spaceCode, entry, parentEntryId);
        assertNameAvailable(spaceCode, parentEntryId, entry.getFilename(), entry.getEntryId());
        int updated = entryDb(spaceCode).update(FILE_ENTRY)
                .set(FILE_ENTRY.PARENT_ENTRY_ID, parentEntryId)
                .where(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
                .execute();
        if(updated != 1) {
            throw new IllegalStateException("move file_entry failed, fileId: " + fileId);
        }
    }

    public void delete(String spaceCode, String fileId) {
        if(!entryWriteEnabled()) {
            return;
        }
        int deleted = entryDb(spaceCode).deleteFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.FILE_ID.eq(fileId))
                .execute();
        if(deleted > 1) {
            throw new IllegalStateException("multiple file_entry rows deleted, fileId: " + fileId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB moveAcrossSpace(String fileId, String targetSpaceCode, @Nullable String targetAncestorId) {
        if(!crossSpaceMoveEnabled) {
            throw new IllegalStateException("cross-space file entry move is disabled");
        }
        if(!entryWriteEnabled()) {
            throw new IllegalStateException("cross-space move requires file_entry writes, current write-mode is closure");
        }
        FileDB file = queryActiveFile(fileId);
        if(file == null) {
            throw new IllegalStateException("file not found, fileId: " + fileId);
        }
        String sourceSpaceCode = file.getSpaceCode();
        FileEntryDB source = ensureLegacyEntry(sourceSpaceCode, fileId);
        if(TYPE_DIR.equals(source.getType()) && entryDb(sourceSpaceCode).fetchCount(FILE_ENTRY,
                FILE_ENTRY.PARENT_ENTRY_ID.eq(source.getEntryId())) > 0) {
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
        fillCreatorInfo(target);
        entryDb(targetSpaceCode).insertInto(FILE_ENTRY).set(target).execute();
        int sourceUpdated = entryDb(sourceSpaceCode).deleteFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(sourceSpaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(source.getEntryId()))
                .and(FILE_ENTRY.FILE_ID.eq(fileId))
                .execute();
        if(sourceUpdated != 1) {
            throw new IllegalStateException("delete source file_entry failed, fileId: " + fileId);
        }
        if(closureWriteEnabled()) {
            moveLeafClosureAcrossSpace(fileId, sourceSpaceCode, targetSpaceCode, targetAncestorId);
        }
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

    public static class EntryReadNotReadyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        EntryReadNotReadyException(String spaceCode, String ancestorId) {
            super("file_entry parent is not initialized, spaceCode: " + spaceCode + ", ancestorId: " + ancestorId);
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
