package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_ENTRY;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.ke.bella.files.protocol.FileNodeCount;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.utils.DigestUtils;
import com.ke.bella.files.utils.JsonUtils;

@Component
public class FileEntryRepo implements BaseRepo {
    public static final String ROOT_ENTRY_ID = "";
    public static final String TYPE_FILE = "file";
    public static final String TYPE_DIR = "dir";
    public static final String TYPE_RESOURCE = "resource";
    // 祖先链遍历的深度上限，超出视为数据成环等异常
    static final int MAX_TREE_DEPTH = 64;
    // MySQL 预处理语句占位符上限为 65535，子树迁移只告警不限规模，所有不定长 IN 列表必须分块
    private static final int SQL_IN_CHUNK_SIZE = 2000;

    private static final Logger LOGGER = LoggerFactory.getLogger(FileEntryRepo.class);

    private final DSLContext db;

    /**
     * 跨空间迁移子树规模的告警阈值：超过只记 warning（含规模、源/目标空间、耗时），不拒绝迁移。
     * 告警数据用于评估是否需要异步分批迁移的 Phase 2。
     */
    @Value("${bella.file-api.file-entry.cross-space-move-warn-threshold:5000}")
    private int crossSpaceMoveWarnThreshold;

    private int sqlInChunkSize = SQL_IN_CHUNK_SIZE;

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

    void setCrossSpaceMoveWarnThreshold(int threshold) {
        this.crossSpaceMoveWarnThreshold = threshold;
    }

    void setSqlInChunkSize(int size) {
        this.sqlInChunkSize = size;
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

    public FileNodeCount countNodes(String spaceCode, @Nullable String ancestorId) {
        Condition condition = FILE_ENTRY.SPACE_CODE.eq(spaceCode);
        if(StringUtils.isNotEmpty(ancestorId)) {
            condition = condition.and(FILE_ENTRY.PARENT_ENTRY_ID.eq(resolveParentEntryIdForRead(spaceCode, ancestorId)));
        }
        Result<Record2<String, Integer>> counts = entryDb(spaceCode)
                .select(FILE_ENTRY.TYPE, DSL.count())
                .from(FILE_ENTRY)
                .where(condition)
                .groupBy(FILE_ENTRY.TYPE)
                .fetch();
        FileNodeCount result = new FileNodeCount();
        counts.forEach(record -> {
            String type = record.value1();
            long count = record.value2();
            if(TYPE_FILE.equals(type)) {
                result.setFileCount(count);
            } else if(TYPE_DIR.equals(type)) {
                result.setDirectoryCount(count);
            } else if(TYPE_RESOURCE.equals(type)) {
                result.setResourceCount(count);
            } else {
                throw new IllegalStateException("Unsupported file_entry type: " + type);
            }
        });
        return result;
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
    private String entryType(FileDB file) {
        if(Integer.valueOf(1).equals(file.getIsDir())) {
            return TYPE_DIR;
        }
        return NodeType.RESOURCE.getValue().equals(file.getNodeType()) ? TYPE_RESOURCE : TYPE_FILE;
    }

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
        record.setType(entryType(file));
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
        record.setType(entryType(file));
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
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
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
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(entry.getEntryId()))
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

    /**
     * file 与 targetAncestor 是调用方已查出的快照，源空间取 file.space_code 缓存、不再回表；
     * 快照过期由事务内对 entry 行的加锁重读兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public FileEntryDB moveAcrossSpace(FileDB file, String targetSpaceCode, @Nullable FileDB targetAncestor) {
        if(!entryWriteEnabled()) {
            throw new IllegalStateException("cross-space move requires file_entry writes, current write-mode is closure");
        }
        String fileId = file.getFileId();
        String sourceSpaceCode = file.getSpaceCode();
        String targetAncestorId = targetAncestor == null ? null : targetAncestor.getFileId();
        FileEntryDB source = ensureLegacyEntry(sourceSpaceCode, fileId);
        if(sourceSpaceCode.equals(targetSpaceCode) && !closureWriteEnabled()) {
            // 同空间调用退化为普通移动：只改根 entry 的 parent_entry_id，子树与 file 缓存都不用动。
            // 闭包仍在写（dual）时不走此捷径，让原路径维护闭包一致性。
            move(sourceSpaceCode, fileId, targetAncestorId);
            return queryActiveByFileId(targetSpaceCode, fileId);
        }
        String targetParentEntryId = resolveParentEntryId(targetSpaceCode, targetAncestorId);
        // 源根与目标父两把锚点锁统一按 (物理分片, entry_id) 全序获取，消除 A→B 与 B→A
        // 并发迁移的环形等待；先锁锚点再 BFS 遍历子树，根不加锁时并发 rename/move/delete
        // 会让迁移使用过期的 filename 或父关系。子树行锁仍按 BFS 层序追加，
        // 与锚点构成的残余复合环依赖数据库死锁检测回滚兜底。
        if(ROOT_ENTRY_ID.equals(targetParentEntryId) || lockOrderKey(sourceSpaceCode, source.getEntryId())
                .compareTo(lockOrderKey(targetSpaceCode, targetParentEntryId)) <= 0) {
            source = lockSourceEntry(sourceSpaceCode, source.getEntryId());
            lockTargetParentEntry(targetSpaceCode, targetParentEntryId);
        } else {
            lockTargetParentEntry(targetSpaceCode, targetParentEntryId);
            source = lockSourceEntry(sourceSpaceCode, source.getEntryId());
        }
        SubtreeSnapshot subtree = TYPE_DIR.equals(source.getType())
                ? collectSubtreeDescendants(sourceSpaceCode, source)
                : SubtreeSnapshot.EMPTY;
        List<FileEntryDB> descendants = subtree.descendants;
        if(source.getEntryId().equals(targetParentEntryId)
                || descendants.stream().anyMatch(entry -> entry.getEntryId().equals(targetParentEntryId))) {
            throw new IllegalArgumentException("cannot move a node into itself or its descendant, fileId: " + fileId);
        }
        assertNameAvailable(targetSpaceCode, targetParentEntryId, source.getFilename(), null);
        assertDepthWithinLimitAfterMove(targetSpaceCode, targetParentEntryId, subtree.depth, fileId);
        if(descendants.isEmpty()) {
            moveLeafAcrossSpace(source, sourceSpaceCode, targetSpaceCode, targetParentEntryId, targetAncestorId);
        } else {
            moveSubtreeAcrossSpace(source, descendants, sourceSpaceCode, targetSpaceCode, targetParentEntryId);
        }
        return queryActiveByFileId(targetSpaceCode, fileId);
    }

    /**
     * 锚点锁的全序键：分片号定宽补零，避免字符串比较把 "10" 排在 "2" 前面。
     */
    private static String lockOrderKey(String spaceCode, String entryId) {
        return String.format("%02d", Integer.parseInt(FileRepo.getShardingKeyBySpaceCode(spaceCode))) + ':' + entryId;
    }

    /**
     * 子树根 entry FOR UPDATE 锁定并以锁后行为准：ensureLegacyEntry 是普通读，
     * 加锁前的并发 rename/move/delete 在锁定重读后会反映为最新状态或行缺失。
     */
    private FileEntryDB lockSourceEntry(String spaceCode, String entryId) {
        FileEntryDB locked = entryDb(spaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(entryId))
                .forUpdate()
                .fetchOneInto(FileEntryDB.class);
        if(locked == null) {
            throw new IllegalStateException("source file_entry no longer exists, entryId: " + entryId);
        }
        return locked;
    }

    /**
     * 目标父入口 FOR UPDATE 锁定并复核仍在目标空间：解析后若不持锁，
     * 并发事务可能把目标父目录迁走，本事务插入的入口将指向目标空间不存在的 entry 形成断链。
     * 空间根不是实体行、不会被迁走，无需加锁。
     */
    private void lockTargetParentEntry(String targetSpaceCode, String targetParentEntryId) {
        if(ROOT_ENTRY_ID.equals(targetParentEntryId)) {
            return;
        }
        FileEntryDB locked = entryDb(targetSpaceCode).selectFrom(FILE_ENTRY)
                .where(FILE_ENTRY.SPACE_CODE.eq(targetSpaceCode))
                .and(FILE_ENTRY.ENTRY_ID.eq(targetParentEntryId))
                .forUpdate()
                .fetchOneInto(FileEntryDB.class);
        if(locked == null) {
            throw new IllegalStateException(
                    "target parent file_entry not found in target space, entryId: " + targetParentEntryId);
        }
        if(!TYPE_DIR.equals(locked.getType())) {
            throw new IllegalArgumentException(
                    "target parent file_entry is not a directory, entryId: " + targetParentEntryId);
        }
    }

    /**
     * 迁移后总深度校验：目标父层级 + 子树自身层数不得超过 MAX_TREE_DEPTH。与迁入空间根时
     * 允许恰好 MAX_TREE_DEPTH 层子树的语义一致（迁移根本身占一层，最深节点相对空间根为
     * targetParentLevel + 1 + subtreeDepth 层）。不校验则迁移本身成功，但深层节点之后会被
     * pathFiles/ancestorFileIds 的超深度防环检查判为数据异常而不可读。
     * 目标父祖先链仅目标父本身持锁，链上并发结构变化与同空间 move 的防环校验同属接受的残余风险。
     */
    private void assertDepthWithinLimitAfterMove(String targetSpaceCode, String targetParentEntryId, int subtreeDepth,
            String fileId) {
        int targetParentLevel = 0;
        String cursor = targetParentEntryId;
        while (!ROOT_ENTRY_ID.equals(cursor)) {
            // 超限即停，兼作目标父祖先链的成环保护，向上遍历最多 MAX_TREE_DEPTH 层
            if(++targetParentLevel + subtreeDepth > MAX_TREE_DEPTH) {
                throw new IllegalArgumentException(
                        "target parent depth plus subtree depth exceeds max tree depth, fileId: " + fileId);
            }
            FileEntryDB parent = queryActiveByEntryId(targetSpaceCode, cursor);
            if(parent == null) {
                throw new EntryReadNotReadyException(targetSpaceCode, cursor);
            }
            cursor = parent.getParentEntryId();
        }
    }

    private static <T> List<List<T>> partition(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < values.size(); from += size) {
            chunks.add(values.subList(from, Math.min(from + size, values.size())));
        }
        return chunks;
    }

    private void moveLeafAcrossSpace(FileEntryDB source, String sourceSpaceCode, String targetSpaceCode,
            String targetParentEntryId, @Nullable String targetAncestorId) {
        String fileId = source.getFileId();
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
        updateFileSpaceCache(Collections.singletonList(fileId), targetSpaceCode);
    }

    /**
     * 非空目录跨空间迁移：同一本地事务内把子树全部 file_entry 改写到目标空间分片（保留 entry_id 与父子关系，
     * 仅根 entry 的 parent_entry_id 指向目标父入口），并批量刷新 file.space_code 缓存。
     * 只迁窄表 file_entry，不随迁闭包，因此仅允许在 write-mode=entry（闭包已停写）下执行；
     * file 物理行、file_id、bucket/path 和对象内容均不变；失败整体回滚，源目录保持完好。
     */
    private void moveSubtreeAcrossSpace(FileEntryDB source, List<FileEntryDB> descendants, String sourceSpaceCode,
            String targetSpaceCode, String targetParentEntryId) {
        if(closureWriteEnabled()) {
            throw new IllegalStateException(
                    "cross-space move of non-empty directories requires write-mode entry, closure is still being written");
        }
        long startMillis = System.currentTimeMillis();
        String fileId = source.getFileId();
        List<FileEntryDB> subtree = new ArrayList<>(descendants.size() + 1);
        subtree.add(source);
        subtree.addAll(descendants);
        List<String> entryIds = subtree.stream().map(FileEntryDB::getEntryId).collect(Collectors.toList());
        List<String> fileIds = subtree.stream().map(FileEntryDB::getFileId).collect(Collectors.toList());

        // 源/目标分片表同库，单条 insert-select 整体搬迁，行数据不经应用层；再整体删除源行
        FileEntryRecord audit = FILE_ENTRY.newRecord();
        fillUpdatorInfo(audit);
        Table<?> sourceTable = DSL.table(DSL.name("file_entry_" + FileRepo.getShardingKeyBySpaceCode(sourceSpaceCode)));
        Table<?> targetTable = DSL.table(DSL.name("file_entry_" + FileRepo.getShardingKeyBySpaceCode(targetSpaceCode)));
        int inserted = 0;
        for (List<String> chunk : partition(entryIds, sqlInChunkSize)) {
            inserted += db.execute("insert into {0} "
                            + "(entry_id, space_code, parent_entry_id, file_id, filename, type, cuid, cu_name, ctime, muid, mu_name, mtime) "
                            + "select entry_id, {1}, case when entry_id = {2} then {3} else parent_entry_id end, "
                            + "file_id, filename, type, cuid, cu_name, ctime, {4}, {5}, {6} "
                            + "from {7} where space_code = {8} and entry_id in ({9})",
                    targetTable, DSL.val(targetSpaceCode), DSL.val(source.getEntryId()), DSL.val(targetParentEntryId),
                    DSL.val(audit.getMuid() == null ? 0L : audit.getMuid()),
                    DSL.val(audit.getMuName() == null ? "" : audit.getMuName()), DSL.val(audit.getMtime()),
                    sourceTable, DSL.val(sourceSpaceCode),
                    DSL.list(chunk.stream().map(DSL::val).collect(Collectors.toList())));
        }
        if(inserted != entryIds.size()) {
            throw new IllegalStateException("insert target subtree file_entry failed, fileId: " + fileId
                    + ", expected: " + entryIds.size() + ", inserted: " + inserted);
        }
        int deleted = 0;
        for (List<String> chunk : partition(entryIds, sqlInChunkSize)) {
            deleted += entryDb(sourceSpaceCode).deleteFrom(FILE_ENTRY)
                    .where(FILE_ENTRY.SPACE_CODE.eq(sourceSpaceCode))
                    .and(FILE_ENTRY.ENTRY_ID.in(chunk))
                    .execute();
        }
        if(deleted != entryIds.size()) {
            throw new IllegalStateException("delete source subtree file_entry failed, fileId: " + fileId);
        }
        updateFileSpaceCache(fileIds, targetSpaceCode);
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        if(subtree.size() > crossSpaceMoveWarnThreshold) {
            LOGGER.warn("large cross-space subtree move, size: {}, source space: {}, target space: {}, elapsed: {}ms, fileId: {}",
                    subtree.size(), sourceSpaceCode, targetSpaceCode, elapsedMillis, fileId);
        }
    }

    /**
     * BFS 快照：子孙列表（不含根）与子树在根以下的层数（叶子/空目录为 0）。
     */
    private static final class SubtreeSnapshot {
        static final SubtreeSnapshot EMPTY = new SubtreeSnapshot(Collections.emptyList(), 0);

        final List<FileEntryDB> descendants;
        final int depth;

        SubtreeSnapshot(List<FileEntryDB> descendants, int depth) {
            this.descendants = descendants;
            this.depth = depth;
        }
    }

    /**
     * 逐层 BFS 枚举子树（不含根），层内批量 IN 查询并加锁，查询次数与树深同阶。
     * 层级数即目录深度，超过 MAX_TREE_DEPTH 视为成环等数据异常。
     */
    private SubtreeSnapshot collectSubtreeDescendants(String spaceCode, FileEntryDB root) {
        List<FileEntryDB> result = new ArrayList<>();
        List<String> frontier = Collections.singletonList(root.getEntryId());
        int depth = 0;
        while (!frontier.isEmpty()) {
            // 只取迁移必需的三列，行数据本身由 insert-select 在库内搬迁，不经应用层
            List<FileEntryDB> children = new ArrayList<>();
            for (List<String> chunk : partition(frontier, sqlInChunkSize)) {
                children.addAll(entryDb(spaceCode)
                        .select(FILE_ENTRY.ENTRY_ID, FILE_ENTRY.FILE_ID, FILE_ENTRY.TYPE)
                        .from(FILE_ENTRY)
                        .where(FILE_ENTRY.SPACE_CODE.eq(spaceCode))
                        .and(FILE_ENTRY.PARENT_ENTRY_ID.in(chunk))
                        .forUpdate()
                        .fetchInto(FileEntryDB.class));
            }
            // 深度在查到非空下一层后才累加：最深层是空目录时的“确认无子节点”查询不计入，
            // 恰好 MAX_TREE_DEPTH 层的合法子树可以迁移
            if(!children.isEmpty() && ++depth > MAX_TREE_DEPTH) {
                throw new IllegalStateException("file_entry subtree exceeds max depth, fileId: " + root.getFileId());
            }
            result.addAll(children);
            frontier = children.stream()
                    .filter(entry -> TYPE_DIR.equals(entry.getType()))
                    .map(FileEntryDB::getEntryId)
                    .collect(Collectors.toList());
        }
        return new SubtreeSnapshot(result, depth);
    }

    /**
     * 子树 file.space_code 派生缓存批量刷新：file 物理行按 file_id hash 分片，不随空间迁移，只改缓存值。
     * 每个分片一条 UPDATE，合并进同一个 JDBC batch 一次往返执行；
     * batch 由未分片 context 渲染会丢失 RenderMapping，因此显式写物理表名。
     */
    private void updateFileSpaceCache(List<String> fileIds, String targetSpaceCode) {
        Map<String, List<String>> idsByShard = fileIds.stream()
                .collect(Collectors.groupingBy(FileRepo::getShardingKeyByFileIdStatic));
        List<Query> updates = new ArrayList<>();
        idsByShard.forEach((shardKey, ids) -> partition(ids, sqlInChunkSize)
                .forEach(chunk -> updates.add(db.query("update {0} set space_code = {1} where file_id in ({2}) and status = {3}",
                        DSL.table(DSL.name("file_" + shardKey)), DSL.val(targetSpaceCode),
                        DSL.list(chunk.stream().map(DSL::val).collect(Collectors.toList())),
                        DSL.val(FileStatus.NOT_DELETED.getValue())))));
        int updated = Arrays.stream(db.batch(updates).execute()).sum();
        if(updated != fileIds.size()) {
            throw new IllegalStateException("update file space cache failed, expected: " + fileIds.size() + ", updated: " + updated);
        }
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
