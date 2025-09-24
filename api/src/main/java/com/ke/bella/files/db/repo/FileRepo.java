package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_CLOSURE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;
import static com.ke.bella.files.db.Tables.FILE_SHARDING;
import static com.ke.bella.files.db.repo.DSLContextHolder.targetTableName;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.jooq.Condition;
import org.jooq.SortField;
import org.jooq.SelectConditionStep;
import org.jooq.SelectOrderByStep;
import org.jooq.UpdateSetMoreStep;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import com.ke.bella.files.db.FileIdGenerator;
import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.db.tables.pojos.FileShardingDB;
import com.ke.bella.files.db.tables.records.FileClosureRecord;
import com.ke.bella.files.db.tables.records.FileProgressRecord;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.db.tables.records.FileShardingRecord;
import com.ke.bella.files.enums.FileType;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.Page;
import com.ke.bella.files.protocol.PageFileOps;
import com.ke.bella.files.protocol.FileCountInfo;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.CustomStringUtils;
import com.ke.bella.files.utils.JsonUtils;

@Component
public class FileRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    public FileRepo(DSLContext db) {
        this.db = db;
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
            addFileClosures(fileDB.getFileId(), ancestorId);
        }

        return shardingKey;
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

    public void deleteFileClosure(String fileId, FileType fileType) {
        String shardingKey = getShardingKeyByFileId(fileId, fileType);
        db(shardingKey).delete(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId))
                .or(FILE_CLOSURE.ANCESTOR_ID.eq(fileId))
                .execute();
    }

    public List<FileDB> findFiles(String spaceCode, String ancestorId) {
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

    @Nullable
    public String getDirectAncestorId(String fileId) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);

        return db(shardingKey).select(FILE_CLOSURE.ANCESTOR_ID)
                .from(FILE_CLOSURE)
                .where(FILE_CLOSURE.DESCENDANT_ID.eq(fileId)
                        .and(FILE_CLOSURE.DEPTH.eq(1L)))
                .fetchOptional()
                .map(record -> record.getValue(FILE_CLOSURE.ANCESTOR_ID))
                .orElse(null);

	}

	public Page<FileDB> pageFiles(PageFileOps ops) {
		String shardingKey;
		if(StringUtils.isNotEmpty(ops.getAncestorId())) {
			shardingKey = getShardingKeyByFileId(ops.getAncestorId());
		} else {
			shardingKey = getShardingKeyBySpaceCode(ops.getSpaceCode());
		}

		// 共用的 where 条件
		Condition whereCondition = buildWhereCondition(ops);

		// 数据查询与计数查询共用 from/join 结构
		SelectConditionStep<Record> baseQuery = db(shardingKey).select(FILE.fields())
			.from(FILE_CLOSURE)
			.innerJoin(FILE)
			.on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
			.where(whereCondition);

		SelectConditionStep<Record1<Integer>> countQuery = db(shardingKey).select(count())
			.from(FILE_CLOSURE)
			.innerJoin(FILE)
			.on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
			.where(whereCondition);

		Long totalResult = countQuery.fetchOneInto(Long.class);
		long total = totalResult != null ? totalResult : 0L;

		boolean isAsc = "asc".equalsIgnoreCase(ops.getOrder());
		SortField<?> ctimeOrder = isAsc ? FILE.CTIME.asc() : FILE.CTIME.desc();

		List<FileDB> records = baseQuery
			.orderBy(FILE.IS_DIR.desc(), ctimeOrder)
			.limit(ops.getPageSize())
			.offset((ops.getPageNo() - 1) * ops.getPageSize())
			.fetchInto(FileDB.class);

		long pages = (total + ops.getPageSize() - 1) / ops.getPageSize();

		return Page.<FileDB>builder()
			.pageNo(ops.getPageNo())
			.pageSize(ops.getPageSize())
			.total(total)
			.pages(pages)
			.records(records)
			.build();
	}

	/**
	 * 构造分页查询的通用 where 条件，供数据查询与计数查询复用。
	 */
	private Condition buildWhereCondition(PageFileOps ops) {
		Condition condition = FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue());

		// 作用域：ancestorId 指定目录的直系子节点；否则为 spaceCode 根层
		if(StringUtils.isNotEmpty(ops.getAncestorId())) {
			condition = condition
				.and(FILE_CLOSURE.ANCESTOR_ID.eq(ops.getAncestorId()))
				.and(FILE_CLOSURE.DEPTH.eq(1L));
		} else {
			condition = condition
				.and(FILE.SPACE_CODE.eq(ops.getSpaceCode()))
				.and(FILE_CLOSURE.SPACE_CODE.eq(ops.getSpaceCode()))
				.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
		}

		// 基础过滤项
		if(StringUtils.isNotEmpty(ops.getPurpose())) {
			condition = condition.and(FILE.PURPOSE.eq(ops.getPurpose()));
		}
		if(StringUtils.isNotEmpty(ops.getFilename())) {
			condition = condition.and(FILE.FILENAME.like(ops.getFilename() + "%"));
		}

		// 类型过滤
		if(StringUtils.isNotBlank(ops.getType())) {
			switch (ops.getType()) {
			case "directory":
				condition = condition.and(FILE.IS_DIR.eq(1));
				break;
			case "file":
				condition = condition.and(FILE.IS_DIR.eq(0));
			default:
				break;
			}
		}

		// 标签过滤（任一匹配）
		if(!CollectionUtils.isEmpty(ops.getTags())) {
			condition = condition.and(FILE.TAGS.isNotNull())
				.and(buildContainsAnyCondition(FILE.TAGS, ops.getTags()));
		}

		// 城市过滤（任一匹配）
		if(!CollectionUtils.isEmpty(ops.getCities())) {
			condition = condition.and(FILE.CITIES.isNotNull())
				.and(buildContainsAnyCondition(FILE.CITIES, ops.getCities()));
		}

		// 创建/更新人过滤
		if(ops.getCuid() != null) {
			condition = condition.and(FILE.CUID.eq(ops.getCuid()));
		}
		if(ops.getMuid() != null) {
			condition = condition.and(FILE.MUID.eq(ops.getMuid()));
		}

		return condition;
	}

	/**
	 * 构造 JSON/文本字段 contains 任一值的 OR 条件。
	 */
	private Condition buildContainsAnyCondition(org.jooq.Field<String> field, List<String> values) {
		Condition orCondition = null;
		for (String value : values) {
			Condition single = field.contains(value);
			orCondition = (orCondition == null) ? single : orCondition.or(single);
		}
		return orCondition;
	}

	public int countFiles(String ancestorId, String type) {
		String shardingKey = getShardingKeyByFileId(ancestorId);

		SelectConditionStep<Record1<Integer>> countQuery = db(shardingKey).select(count())
			.from(FILE_CLOSURE)
			.innerJoin(FILE)
			.on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
			.where(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
			.and(FILE_CLOSURE.ANCESTOR_ID.eq(ancestorId))
			.and(FILE_CLOSURE.DEPTH.eq(1L));

		if(StringUtils.isNotBlank(type)) {
			switch (type) {
			case "directory":
				countQuery = countQuery.and(FILE.IS_DIR.eq(1));
				break;
			case "file":
				countQuery = countQuery.and(FILE.IS_DIR.eq(0));
			default:
				break;
			}
		}
		Integer result = countQuery.fetchOneInto(Integer.class);
		return result != null ? result : 0;
	}

	public List<FileCountInfo> batchCountFiles(List<String> ancestorIds, String type, String spaceCode) {
		if(CollectionUtils.isEmpty(ancestorIds)) {
			return Collections.emptyList();
		}

		String shardingKey = getShardingKeyBySpaceCode(spaceCode);

		SelectConditionStep<org.jooq.Record2<String, Integer>> countQuery = db(shardingKey)
			.select(FILE_CLOSURE.ANCESTOR_ID, count().as("file_count"))
			.from(FILE_CLOSURE)
			.innerJoin(FILE)
			.on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
			.where(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()))
			.and(FILE_CLOSURE.ANCESTOR_ID.in(ancestorIds))
			.and(FILE_CLOSURE.DEPTH.eq(1L));

		if(StringUtils.isNotBlank(type)) {
			switch (type) {
			case "directory":
				countQuery = countQuery.and(FILE.IS_DIR.eq(1));
				break;
			case "file":
				countQuery = countQuery.and(FILE.IS_DIR.eq(0));
				break;
			default:
				break;
			}
		}

		Map<String, Integer> countMap = countQuery
			.groupBy(FILE_CLOSURE.ANCESTOR_ID)
			.fetchMap(FILE_CLOSURE.ANCESTOR_ID, field("file_count", Integer.class));

		return ancestorIds.stream()
			.map(ancestorId -> new FileCountInfo(ancestorId, countMap.getOrDefault(ancestorId, 0)))
			.collect(Collectors.toList());
	}

	/**
	 * 批量检查目标ID是否是指定目录列表中任一目录的后代
	 *
	 * @param targetId 目标ID（要检查的节点）
	 * @param dirIds   目录ID列表（祖先候选列表）
	 *
	 * @return 包含targetId作为后代的目录ID集合
	 */
	public List<String> batchCheckTargetAsDescendant(String targetId, List<String> dirIds) {
		if(dirIds.isEmpty() || targetId == null) {
			return Collections.emptyList();
		}

		targetId = queryNewFileId(targetId);
		List<String> normalizedDirIds = dirIds.stream()
			.map(this::queryNewFileId)
			.collect(Collectors.toList());

		String shardingKey = getShardingKeyByFileId(targetId);

		return db(shardingKey).select(FILE_CLOSURE.ANCESTOR_ID)
			.from(FILE_CLOSURE)
			.where(FILE_CLOSURE.DESCENDANT_ID.eq(targetId))
			.and(FILE_CLOSURE.ANCESTOR_ID.in(normalizedDirIds))
			.fetchInto(String.class);
	}

	/**
	 * 批量获取文件的直接父目录ID
	 *
	 * @param spaceCode 空间编码
	 * @param fileIds   文件ID列表
	 *
	 * @return 文件ID到父目录ID的映射
	 */
	public Map<String, String> batchGetDirectAncestorIds(String spaceCode, List<String> fileIds) {
		if(fileIds.isEmpty()) {
			return Collections.emptyMap();
		}

		List<String> normalizedFileIds = fileIds.stream()
			.map(this::queryNewFileId)
			.collect(Collectors.toList());

		String shardingKey = getShardingKeyBySpaceCode(spaceCode);

		Map<String, String> result = new HashMap<>();

		db(shardingKey).select(FILE_CLOSURE.DESCENDANT_ID, FILE_CLOSURE.ANCESTOR_ID)
			.from(FILE_CLOSURE)
			.where(FILE_CLOSURE.DESCENDANT_ID.in(normalizedFileIds))
			.and(FILE_CLOSURE.DEPTH.eq(1L))
			.fetch()
			.forEach(record -> {
				String descendantId = record.getValue(FILE_CLOSURE.DESCENDANT_ID);
				String ancestorId = record.getValue(FILE_CLOSURE.ANCESTOR_ID);
				result.put(descendantId, ancestorId);
			});

		return result;
	}

	/**
	 * 批量检查目标目录下是否存在指定文件名的文件
	 *
	 * @param spaceCode        空间编码
	 * @param targetAncestorId 目标父目录ID
	 * @param filenames        要检查的文件名列表
	 *
	 * @return 存在的文件名列表
	 */
	public List<String> batchCheckExistingFilenames(String spaceCode, String targetAncestorId, List<String> filenames) {
		if(filenames.isEmpty()) {
			return Collections.emptyList();
		}

		String shardingKey = getShardingKeyBySpaceCode(spaceCode);

		SelectConditionStep<Record1<String>> query = db(shardingKey)
			.select(FILE.FILENAME)
			.from(FILE_CLOSURE)
			.innerJoin(FILE)
			.on(FILE_CLOSURE.DESCENDANT_ID.eq(FILE.FILE_ID))
			.where(FILE.FILENAME.in(filenames))
			.and(FILE_CLOSURE.SPACE_CODE.eq(spaceCode))
			.and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()));

		if(StringUtils.isEmpty(targetAncestorId)) {
			query.and(FILE_CLOSURE.ROOT_DEPTH.eq(1L));
		} else {
			query.and(FILE_CLOSURE.ANCESTOR_ID.eq(targetAncestorId))
				.and(FILE_CLOSURE.DEPTH.eq(1L));
		}

		return query.fetchInto(String.class);
	}

	/**
	 * 将以fileId为根的子树移动到新的父节点下 该操作通过更新闭包表实现： 1) 删除旧祖先节点到子树中所有后代节点的路径 2) 插入新祖先链到子树中所有后代节点的路径 3) 重新计算子树中所有节点的ROOT_DEPTH
	 * <p>
	 * 调用此方法前必须验证以下约束： - newAncestorId为null（移动到根目录）或指向一个目录 - newAncestorId不在fileId的子树内 - 空间编码必须相同
	 */
	@Transactional(rollbackFor = Exception.class)
	public void moveSubtree(String fileId, String newAncestorId) {
		MoveSubtreeContext context = validateAndPrepareMove(fileId, newAncestorId);

		deleteOldClosureRows(context);
		insertNewClosureRows(context);
		updateRootDepths(context);
	}

	private MoveSubtreeContext validateAndPrepareMove(String fileId, String newAncestorId) {
		fileId = queryNewFileId(fileId);
		String shardingKey = getShardingKeyByFileId(fileId);
		DSLContext dsl = db(shardingKey);

		String targetAncestorId = StringUtils.isEmpty(newAncestorId) ? null : queryNewFileId(newAncestorId);

		FileDB movingFile = dsl.selectFrom(FILE)
			.where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
			.fetchOneInto(FileDB.class);
		Assert.notNull(movingFile, "file not found: " + fileId);
		String spaceCode = movingFile.getSpaceCode();

		List<FileClosureRecord> targetAncestorChain = new ArrayList<>();
		int targetChainLength = 0;
		if(targetAncestorId != null) {
			FileDB targetFile = dsl.selectFrom(FILE)
				.where(FILE.FILE_ID.eq(targetAncestorId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
				.fetchOneInto(FileDB.class);
			Assert.notNull(targetFile, "target ancestor not found: " + targetAncestorId);
			Assert.isTrue(targetFile.getIsDir() != null && targetFile.getIsDir() == 1, "target must be a directory: " + targetAncestorId);
			Assert.isTrue(StringUtils.equals(targetFile.getSpaceCode(), spaceCode), "space mismatch");

			targetAncestorChain = dsl.selectFrom(FILE_CLOSURE)
				.where(FILE_CLOSURE.DESCENDANT_ID.eq(targetAncestorId))
				.orderBy(FILE_CLOSURE.DEPTH.asc())
				.fetchInto(FileClosureRecord.class);
			targetChainLength = targetAncestorChain.size();
		}

		List<FileClosureRecord> subtree = dsl.selectFrom(FILE_CLOSURE)
			.where(FILE_CLOSURE.ANCESTOR_ID.eq(fileId))
			.orderBy(FILE_CLOSURE.DEPTH.asc())
			.fetchInto(FileClosureRecord.class);
		Assert.isTrue(!subtree.isEmpty(), "subtree not found in file_closure, ancestor_id: " + fileId);

		List<String> descendantIds = subtree.stream().map(FileClosureRecord::getDescendantId).collect(Collectors.toList());

		return new MoveSubtreeContext(dsl, fileId, targetAncestorId, spaceCode,
			targetAncestorChain, targetChainLength, subtree, descendantIds);
	}

	private void deleteOldClosureRows(MoveSubtreeContext context) {
		context.dsl.delete(FILE_CLOSURE)
			.where(FILE_CLOSURE.DESCENDANT_ID.in(context.descendantIds))
			.and(FILE_CLOSURE.ANCESTOR_ID.in(
				context.dsl.select(FILE_CLOSURE.ANCESTOR_ID)
					.from(FILE_CLOSURE)
					.where(FILE_CLOSURE.DESCENDANT_ID.eq(context.fileId)
						.and(FILE_CLOSURE.DEPTH.gt(0L)))))
			.execute();
	}

	private void insertNewClosureRows(MoveSubtreeContext context) {
		if(context.targetAncestorId == null) {
			return;
		}

		List<InsertSetMoreStep<FileClosureRecord>> inserts = new ArrayList<>();
		for (FileClosureRecord targetAncestor : context.targetAncestorChain) {
			String ancestor = targetAncestor.getAncestorId();
			long depthToTarget = targetAncestor.getDepth();

			for (FileClosureRecord sub : context.subtree) {
				String descendant = sub.getDescendantId();
				long relDepth = sub.getDepth();

				FileClosureRecord rec = FILE_CLOSURE.newRecord();
				rec.setSpaceCode(context.spaceCode);
				rec.setAncestorId(ancestor);
				rec.setDescendantId(descendant);
				rec.setDepth(depthToTarget + 1 + relDepth);
				rec.setRootDepth(-1L);
				fillCreatorInfo(rec);

				inserts.add(context.dsl.insertInto(FILE_CLOSURE).set(rec));
			}
		}
		if(!inserts.isEmpty()) {
			int[] results = context.dsl.batch(inserts).execute();
			if(results.length != inserts.size()) {
				throw new IllegalStateException("batch insert file_closure failed for move, fileId: " + context.fileId);
			}
		}
	}

	private void updateRootDepths(MoveSubtreeContext context) {
		List<org.jooq.Query> updates = new ArrayList<>();
		for (FileClosureRecord sub : context.subtree) {
			String descendant = sub.getDescendantId();
			long relDepth = sub.getDepth();
			long newRootDepth = (long) (context.targetChainLength + 1 + relDepth);
			updates.add(
				context.dsl.update(FILE_CLOSURE)
					.set(FILE_CLOSURE.ROOT_DEPTH, newRootDepth)
					.where(FILE_CLOSURE.ANCESTOR_ID.eq(descendant)
						.and(FILE_CLOSURE.DESCENDANT_ID.eq(descendant)))
			);
		}
		if(!updates.isEmpty()) {
			context.dsl.batch(updates).execute();
		}
	}

	private static class MoveSubtreeContext {
		final DSLContext dsl;
		final String fileId;
		final String targetAncestorId;
		final String spaceCode;
		final List<FileClosureRecord> targetAncestorChain;
		final int targetChainLength;
		final List<FileClosureRecord> subtree;
		final List<String> descendantIds;

		MoveSubtreeContext(DSLContext dsl, String fileId, String targetAncestorId, String spaceCode,
			List<FileClosureRecord> targetAncestorChain, int targetChainLength,
			List<FileClosureRecord> subtree, List<String> descendantIds) {
			this.dsl = dsl;
			this.fileId = fileId;
			this.targetAncestorId = targetAncestorId;
			this.spaceCode = spaceCode;
			this.targetAncestorChain = targetAncestorChain;
			this.targetChainLength = targetChainLength;
			this.subtree = subtree;
			this.descendantIds = descendantIds;
		}
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

    public void increaseFileShardingCount(String key, long delta, String type) {
        db.update(FILE_SHARDING)
                .set(FILE_SHARDING.COUNT, FILE_SHARDING.COUNT.plus(delta))
                .set(FILE_SHARDING.MTIME, LocalDateTime.now())
                .where(FILE_SHARDING.KEY.eq(key))
                .and(FILE_SHARDING.TYPE.eq(type))
                .execute();
    }
}
