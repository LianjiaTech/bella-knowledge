package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.IDGenerator.DATASET_ID_GEN;
import static com.ke.bella.files.db.IDGenerator.QA_ID_GEN;
import static com.ke.bella.files.db.tables.Dataset.DATASET;
import static com.ke.bella.files.db.tables.DatasetDocument.DATASET_DOCUMENT;
import static com.ke.bella.files.db.tables.DatasetQa.DATASET_QA;
import static com.ke.bella.files.db.tables.DatasetQaReference.DATASET_QA_REFERENCE;
import static com.ke.bella.files.db.tables.DatasetSharding.DATASET_SHARDING;
import static com.ke.bella.files.protocol.DatasetOps.DatasetType;
import static com.ke.bella.files.protocol.DatasetOps.DatasetType.document;
import static com.ke.bella.files.protocol.DatasetOps.DatasetType.qa;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetDocumentDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.db.tables.pojos.DatasetShardingDB;
import com.ke.bella.files.db.tables.records.DatasetDocumentRecord;
import com.ke.bella.files.db.tables.records.DatasetQaRecord;
import com.ke.bella.files.db.tables.records.DatasetQaReferenceRecord;
import com.ke.bella.files.db.tables.records.DatasetRecord;
import com.ke.bella.files.db.tables.records.DatasetShardingRecord;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.service.DatasetService;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.DigestUtils;
import com.ke.bella.files.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatasetRepo implements BaseRepo {

    private static final String yyMMddHHmmss = "yyMMddHHmmss";

    @Resource
    private DSLContext db;

    private DSLContext db(String shardingKey) {
        return DSLContextHolder.get(shardingKey, db);
    }

    public DatasetRepo(DSLContext db) {
        this.db = db;
    }

    public String shardingKeyByDatasetId(String datasetId, DatasetType type) {
        return queryDatasetShardingByDatasetId(datasetId, type).getKey();
    }

    public DatasetShardingDB queryDatasetShardingByDatasetId(String datasetId, DatasetType type) {
        LocalDateTime time = timeFromCode(datasetId);
        return db.selectFrom(DATASET_SHARDING)
                .where(DATASET_SHARDING.KEY_TIME.le(time))
                .and(DATASET_SHARDING.TYPE.eq(type.name()))
                .orderBy(DATASET_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(DatasetShardingDB.class);
    }

    public DatasetShardingDB queryLatestDatasetSharding(String type) {
        return db.selectFrom(DATASET_SHARDING)
                .where(DATASET_SHARDING.TYPE.eq(type))
                .orderBy(DATASET_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(DatasetShardingDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void newShardingTable(String lastKey, String type) {
        LocalDateTime keyTime = LocalDateTime.now().plusMinutes(10L);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));

        DatasetShardingRecord rec = db.selectFrom(DATASET_SHARDING)
                .where(DATASET_SHARDING.LAST_KEY.eq(lastKey))
                .and(DATASET_SHARDING.TYPE.eq(type))
                .forUpdate().fetchOne();
        if(rec != null) {
            return;
        }

        if(qa.name().equals(type)) {
            db.execute(createTableLikeSql(DATASET_QA.getName(), key));
            db.execute(createTableLikeSql(DATASET_QA_REFERENCE.getName(), key));
        } else if(document.name().equals(type)) {
            db.execute(createTableLikeSql(DATASET_DOCUMENT.getName(), key));
        }

        addDatasetSharding(keyTime, lastKey, key, type);
    }

    private static String createTableLikeSql(String tableName, String key) {
        return String.format("create table `%s_%s` like `%s`", tableName, key, tableName);
    }

    private void addDatasetSharding(LocalDateTime keyTime, String lastKey, String key, String type) {
        DatasetShardingRecord rec = DATASET_SHARDING.newRecord();
        rec.setKey(key);
        rec.setKeyTime(keyTime);
        rec.setLastKey(lastKey);
        rec.setType(type);
        fillCreatorInfo(rec);

        db.insertInto(DATASET_SHARDING)
                .set(rec)
                .execute();
    }

    private static LocalDateTime timeFromCode(String datasetId) {
        int index = datasetId.indexOf('-') + 1;
        String str = datasetId.substring(index, index + 12);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(yyMMddHHmmss);
        return LocalDateTime.parse(str, formatter);
    }

    public DatasetDB addDataset(DatasetOps.DatasetOp op) {
        DatasetRecord rec = DATASET.newRecord();

        String datasetId = DATASET_ID_GEN.generate();

        rec.setDatasetId(datasetId);
        rec.set(DATASET.NAME, op.getName());
        rec.set(DATASET.TYPE, op.getType());
        rec.set(DATASET.SPACE_CODE, BellaContextHelper.getOperateSpaceCode());

        if(op.getRemark() != null) {
            rec.set(DATASET.REMARK, op.getRemark());
        }

        fillCreatorInfo(rec);

        return db.insertInto(DATASET).set(rec).returningResult().fetchOne().into(DatasetDB.class);
    }

    public DatasetDB getDataset(DatasetOps.DatasetOp op) {
        return getDataset(op, 0);
    }

    public DatasetDB getDataset(DatasetOps.DatasetOp op, Integer status) {
        return db.selectFrom(DATASET)
                .where(DATASET.DATASET_ID.eq(op.getDatasetId()))
                .and(StringUtils.isEmpty(op.getName()) ? DSL.noCondition()
                        : DATASET.NAME.like("%" + DSL.escape(op.getName(), '\\') + "%"))
                .and(StringUtils.isEmpty(op.getType()) ? DSL.noCondition()
                        : DATASET.TYPE.eq(op.getType()))
//				fixme: 权限控制
//                .and(DATASET.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(DATASET.STATUS.eq(status))
                .fetchOneInto(DatasetDB.class);
    }

    public void updateDataset(DatasetOps.DatasetOp op) {
        DatasetRecord rec = DATASET.newRecord();

        if(op.getName() != null) {
            rec.set(DATASET.NAME, op.getName());
        }

        if(op.getType() != null) {
            rec.set(DATASET.TYPE, op.getType());
        }

        if(op.getRemark() != null) {
            rec.set(DATASET.REMARK, op.getRemark());
        }

        if(op.getLatestExportTime() != null) {
            rec.set(DATASET.LATEST_EXPORT_TIME, op.getLatestExportTime());
        }

        if(op.getLatestExportFileId() != null) {
            rec.set(DATASET.LATEST_EXPORT_FILE_ID, op.getLatestExportFileId());
        }

        if(op.getLatestExportTime() == null && op.getLatestExportFileId() == null) {
            fillUpdatorInfo(rec);
        }

        int execute = db.update(DATASET)
                .set(rec)
                .where(DATASET.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "dataset update failed");
    }

    public void updateDatasetItems(String datasetId) {
        DatasetRecord rec = DATASET.newRecord();

        rec.set(DATASET.MTIME, LocalDateTime.now());
        rec.set(DATASET.MUID, BellaContextHelper.getOperatorUserId());
        rec.set(DATASET.MU_NAME, BellaContextHelper.getOperatorUserName());

        int execute = db.update(DATASET)
                .set(rec)
                .where(DATASET.DATASET_ID.eq(datasetId))
                .and(DATASET.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "dataset items update failed");
    }

    public void deleteDataset(DatasetOps.DatasetOp op) {
        DatasetRecord rec = DATASET.newRecord();
        rec.set(DATASET.STATUS, -1);

        int execute = db.update(DATASET)
                .set(rec)
                .where(DATASET.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "dataset delete failed");
    }

    public Page<DatasetDB> pageDataset(DatasetOps.DatasetPage page) {
        SelectConditionStep<DatasetRecord> sql = db.selectFrom(DATASET)
                .where(StringUtils.isEmpty(page.getName()) ? DSL.noCondition()
                        : DATASET.NAME.like("%" + DSL.escape(page.getName(), '\\') + "%"))
                .and(StringUtils.isEmpty(page.getType()) ? DSL.noCondition()
                        : DATASET.TYPE.eq(page.getType()))
                .and(DATASET.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(DATASET.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(page.getOrderBy()) ? "ctime" : page.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(page.getOrder());

        sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc());

        return queryPage(db, sql, page.getPage(), page.getPageSize(), DatasetDB.class);
    }

    public Long increaseItemCount(String datasetId) {
        return increaseItemCount(datasetId, 1);
    }

    public Long increaseItemCount(String datasetId, int count) {
        int execute = db.update(DATASET)
                .set(DATASET.COUNT, DATASET.COUNT.add(count))
                .where(DATASET.DATASET_ID.eq(datasetId))
                .execute();

        Assert.isTrue(execute == 1, "dataset qa_count update failed");

        return db.select(DATASET.COUNT)
                .from(DATASET)
                .where(DATASET.DATASET_ID.eq(datasetId))
                .and(DATASET.STATUS.eq(0))
                .fetchOne(DATASET.COUNT);
    }

    public Long decreaseItemCount(String datasetId) {
        return decreaseItemCount(datasetId, 1);
    }

    public Long decreaseItemCount(String datasetId, int count) {
        int execute = db.update(DATASET)
                .set(DATASET.COUNT, DATASET.COUNT.sub(count))
                .where(DATASET.DATASET_ID.eq(datasetId))
                .execute();

        Assert.isTrue(execute == 1, "dataset qa_count update failed");

        return db.select(DATASET.COUNT)
                .from(DATASET)
                .where(DATASET.DATASET_ID.eq(datasetId))
                .and(DATASET.STATUS.eq(0))
                .fetchOne(DATASET.COUNT);
    }

    public DatasetQaDB addQa(DatasetOps.QAOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);

        String itemId = QA_ID_GEN.generate();

        DatasetQaRecord rec = DATASET_QA.newRecord();
        rec.setItemId(itemId);
        rec.setDatasetId(op.getDatasetId());
        rec.setQuestion(op.getQuestion());
        rec.setSimilarQ1(op.getSimilarQ1());
        rec.setSimilarQ2(op.getSimilarQ2());
        rec.setSimilarQ3(op.getSimilarQ3());
        rec.setAnswer(op.getAnswer());
        if(op.getReasoning() != null) {
            rec.setReasoning(op.getReasoning());
        }

        // 直接使用 QAOp 中的 tags，转换为 JSON 字符串
        if(op.getTags() != null) {
            rec.setTags(JsonUtils.toJson(op.getTags()));
        } else {
            rec.setTags("[]");
        }
        rec.setDatasetShardingKey(shardingKey);

        fillCreatorInfo(rec);

        return db(shardingKey).insertInto(DATASET_QA).set(rec).returningResult().fetchOne().into(DatasetQaDB.class);
    }

    public DatasetQaDB getQa(DatasetOps.QAOp op) {
        return getQa(op, 0);
    }

    public DatasetQaDB getQa(DatasetOps.QAOp op, Integer status) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        return db(shardingKey).selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA.STATUS.eq(status))
                .fetchOneInto(DatasetQaDB.class);
    }

    public void updateQa(DatasetOps.QAOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);

        DatasetQaRecord rec = DATASET_QA.newRecord();

        if(op.getQuestion() != null) {
            rec.set(DATASET_QA.QUESTION, op.getQuestion());
        }

        if(op.getAnswer() != null) {
            rec.set(DATASET_QA.ANSWER, op.getAnswer());
        }

        if(op.getReasoning() != null) {
            rec.set(DATASET_QA.REASONING, op.getReasoning());
        }

        if(op.getTags() != null) {
            if(!op.getTags().isEmpty()) {
                rec.set(DATASET_QA.TAGS, JsonUtils.toJson(op.getTags()));
            } else {
                rec.set(DATASET_QA.TAGS, "[]");
            }
        }

        fillUpdatorInfo(rec);

        int execute = db(shardingKey).update(DATASET_QA)
                .set(rec)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "qa update failed");
    }

    public void deleteQa(DatasetOps.QAOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        DatasetQaRecord rec = DATASET_QA.newRecord();
        rec.set(DATASET_QA.STATUS, -1);

        int execute = db(shardingKey).update(DATASET_QA)
                .set(rec)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "qa delete failed");
    }

    public Page<DatasetQaDB> pageQa(DatasetOps.QaPage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        DSLContext db0 = db(shardingKey);

        SelectConditionStep<DatasetQaRecord> sql = db0.selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc());

        return queryPage(db0, sql, op.getPage(), op.getPageSize(), DatasetQaDB.class);
    }

    public List<DatasetQaDB> listQa(DatasetOps.QaPage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        DSLContext db0 = db(shardingKey);

        SelectConditionStep<DatasetQaRecord> sql = db0.selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        return sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc())
                .fetch().into(DatasetQaDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addQaReferences(String itemId, String datasetId, List<DatasetOps.QAReferenceOp> referenceOps) {
        String shardingKey = shardingKeyByDatasetId(datasetId, qa);
        DSLContext db0 = db(shardingKey);

        List<InsertOnDuplicateSetMoreStep<DatasetQaReferenceRecord>> queries = new ArrayList<>(referenceOps.size());

        for (DatasetOps.QAReferenceOp referenceOp : referenceOps) {
            String referenceId = genReferenceId(itemId, referenceOp.getFileId(), referenceOp.getPath());

            DatasetQaReferenceRecord insertRec = DATASET_QA_REFERENCE.newRecord();
            insertRec.setItemId(itemId);
            insertRec.setDatasetId(datasetId);
            insertRec.setFileId(referenceOp.getFileId());
            insertRec.setReferenceId(referenceId);
            insertRec.setPath(referenceOp.getPath());
            if(referenceOp.getSnippet() != null) {
                insertRec.setSnippet(referenceOp.getSnippet());
            }
            fillCreatorInfo(insertRec);

            DatasetQaReferenceRecord updateRec = DATASET_QA_REFERENCE.newRecord();
            updateRec.setStatus(0);
            if(referenceOp.getSnippet() != null) {
                updateRec.setSnippet(referenceOp.getSnippet());
            }
            fillCreatorInfo(updateRec);

            InsertOnDuplicateSetMoreStep<DatasetQaReferenceRecord> sql = db0.insertInto(DATASET_QA_REFERENCE)
                    .set(insertRec)
                    .onDuplicateKeyUpdate()
                    .set(updateRec);

            queries.add(sql);
        }

        db0.batch(queries).execute();
    }

    public DatasetQaReferenceDB getQaReference(DatasetOps.QAReferenceOp op) {
        return getQaReference(op, 0);
    }

    public DatasetQaReferenceDB getQaReference(DatasetOps.QAReferenceOp op, Integer status) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        return db(shardingKey).selectFrom(DATASET_QA_REFERENCE)
                .where(DATASET_QA_REFERENCE.REFERENCE_ID.eq(op.getReferenceId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(status))
                .fetchOneInto(DatasetQaReferenceDB.class);
    }

    public DatasetQaReferenceDB addQaReference(DatasetOps.QAReferenceOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        String referenceId = genReferenceId(op.getItemId(), op.getFileId(), op.getPath());

        DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();

        rec.setItemId(op.getItemId());
        rec.setDatasetId(op.getDatasetId());
        rec.setFileId(op.getFileId());
        rec.setReferenceId(referenceId);
        rec.setPath(op.getPath());
        if(op.getSnippet() != null) {
            rec.setSnippet(op.getSnippet());
        }

        fillCreatorInfo(rec);

        DatasetQaReferenceRecord updateRec = DATASET_QA_REFERENCE.newRecord();
        updateRec.setStatus(0);
        if(op.getSnippet() != null) {
            updateRec.setSnippet(op.getSnippet());
        }
        fillCreatorInfo(updateRec);

        db(shardingKey).insertInto(DATASET_QA_REFERENCE)
                .set(rec)
                .onDuplicateKeyUpdate()
                .set(updateRec)
                .execute();

        return db(shardingKey).selectFrom(DATASET_QA_REFERENCE)
                .where(DATASET_QA_REFERENCE.REFERENCE_ID.eq(referenceId))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0))
                .fetchOneInto(DatasetQaReferenceDB.class);
    }

    @NotNull
    private static String genReferenceId(String itemId, String fileId, String path) {
        return "reference-" + DigestUtils.sha256(String.format("%s-%s-%s", itemId, fileId, path));
    }

    public void updateQaReference(DatasetOps.QAReferenceOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);

        DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();

        if(op.getPath() != null) {
            rec.set(DATASET_QA_REFERENCE.PATH, op.getPath());
        }

        if(op.getFileId() != null) {
            rec.set(DATASET_QA_REFERENCE.FILE_ID, op.getFileId());
        }

        if(op.getSnippet() != null) {
            rec.set(DATASET_QA_REFERENCE.SNIPPET, op.getSnippet());
        }

        fillUpdatorInfo(rec);

        int execute = db(shardingKey).update(DATASET_QA_REFERENCE)
                .set(rec)
                .where(DATASET_QA_REFERENCE.REFERENCE_ID.eq(op.getReferenceId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "qa reference update failed");
    }

    public void deleteQaReference(DatasetOps.QAReferenceOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);

        DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();
        rec.set(DATASET_QA_REFERENCE.STATUS, -1);

        int execute = db(shardingKey).update(DATASET_QA_REFERENCE)
                .set(rec)
                .where(DATASET_QA_REFERENCE.REFERENCE_ID.eq(op.getReferenceId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "qa reference delete failed");
    }

    public Page<DatasetQaReferenceDB> pageQaReferences(DatasetOps.QaReferencePage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        DSLContext dslContext = db(shardingKey);

        SelectConditionStep<DatasetQaReferenceRecord> sql = dslContext.selectFrom(DATASET_QA_REFERENCE)
                .where(StringUtils.isEmpty(op.getItemId()) ? DSL.noCondition() : DATASET_QA_REFERENCE.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc());

        return queryPage(dslContext, sql, op.getPage(), op.getPageSize(), DatasetQaReferenceDB.class);
    }

    public List<DatasetQaReferenceDB> listQaReferences(DatasetOps.QaReferencePage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), qa);
        DSLContext dslContext = db(shardingKey);

        SelectConditionStep<DatasetQaReferenceRecord> sql = dslContext.selectFrom(DATASET_QA_REFERENCE)
                .where(StringUtils.isEmpty(op.getItemId()) ? DSL.noCondition() : DATASET_QA_REFERENCE.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        return sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc())
                .fetch().into(DatasetQaReferenceDB.class);
    }

    public void increaseShardingCount(String key, long delta, String type) {
        db.update(DATASET_SHARDING)
                .set(DATASET_SHARDING.COUNT, DATASET_SHARDING.COUNT.plus(delta))
                .set(DATASET_SHARDING.MTIME, LocalDateTime.now())
                .where(DATASET_SHARDING.KEY.eq(key))
                .and(DATASET_SHARDING.TYPE.eq(type))
                .execute();
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DatasetDocumentDB> addDocuments(DatasetOps.DocumentCreateOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), document);
        DSLContext db0 = db(shardingKey);

        List<InsertOnDuplicateSetMoreStep<DatasetDocumentRecord>> queries = new ArrayList<>(op.getFileIds().size());

        for (String fileId : op.getFileIds()) {
            DatasetDocumentRecord insertRec = DATASET_DOCUMENT.newRecord();
            insertRec.setDatasetId(op.getDatasetId());
            insertRec.setFileId(fileId);
            insertRec.setDatasetShardingKey(shardingKey);
            fillCreatorInfo(insertRec);

            DatasetDocumentRecord updateRec = DATASET_DOCUMENT.newRecord();
            updateRec.setStatus(0);
            fillCreatorInfo(updateRec);

            InsertOnDuplicateSetMoreStep<DatasetDocumentRecord> sql = db0.insertInto(DATASET_DOCUMENT)
                    .set(insertRec)
                    .onDuplicateKeyUpdate()
                    .set(updateRec);

            queries.add(sql);
        }

        int[] batchResults = db0.batch(queries).execute();

        for (int i = 0; i < batchResults.length; i++) {
            if(batchResults[i] == 0) {
                String failedFileId = op.getFileIds().get(i);
                Assert.isTrue(false, "failed to insert document for file_id: " + failedFileId);
            }
        }

        List<DatasetDocumentDB> results = db0.selectFrom(DATASET_DOCUMENT)
                .where(DATASET_DOCUMENT.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_DOCUMENT.FILE_ID.in(op.getFileIds()))
                .and(DATASET_DOCUMENT.STATUS.eq(0))
                .fetch()
                .into(DatasetDocumentDB.class);

        return results;
    }

    public DatasetDocumentDB getDocument(DatasetOps.DocumentOp op) {
        return getDocument(op, 0);
    }

    public DatasetDocumentDB getDocument(DatasetOps.DocumentOp op, Integer status) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), document);
        return db(shardingKey).selectFrom(DATASET_DOCUMENT)
                .where(DATASET_DOCUMENT.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_DOCUMENT.FILE_ID.eq(op.getFileId()))
                .and(DATASET_DOCUMENT.STATUS.eq(status))
                .fetchOneInto(DatasetDocumentDB.class);
    }

    public void deleteDocument(DatasetOps.DocumentOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), document);
        DatasetDocumentRecord rec = DATASET_DOCUMENT.newRecord();
        rec.set(DATASET_DOCUMENT.STATUS, -1);

        fillUpdatorInfo(rec);

        int execute = db(shardingKey).update(DATASET_DOCUMENT)
                .set(rec)
                .where(DATASET_DOCUMENT.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_DOCUMENT.FILE_ID.eq(op.getFileId()))
                .and(DATASET_DOCUMENT.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "document delete failed");
    }

    public Page<DatasetDocumentDB> pageDocument(DatasetOps.DocumentPage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), document);
        DSLContext db0 = db(shardingKey);

        SelectConditionStep<DatasetDocumentRecord> sql = db0.selectFrom(DATASET_DOCUMENT)
                .where(DATASET_DOCUMENT.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_DOCUMENT.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc());

        return queryPage(db0, sql, op.getPage(), op.getPageSize(), DatasetDocumentDB.class);
    }

    public List<DatasetDocumentDB> listDocument(DatasetOps.DocumentPage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId(), document);
        DSLContext db0 = db(shardingKey);

        SelectConditionStep<DatasetDocumentRecord> sql = db0.selectFrom(DATASET_DOCUMENT)
                .where(DATASET_DOCUMENT.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_DOCUMENT.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(op.getOrderBy()) ? "ctime" : op.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(op.getOrder());

        return sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc())
                .fetch().into(DatasetDocumentDB.class);
    }

    /**
     * 使用游标流式处理QA数据及其关联引用，适用于大数据量导出
     *
     * @param datasetId  数据集ID
     * @param qaConsumer QA数据消费者
     */
    public void streamQaWithReferences(String datasetId, Consumer<DatasetService.DatasetQaWithReferences> qaConsumer) {
        // 1. 获取分片键和数据库上下文
        String shardingKey = shardingKeyByDatasetId(datasetId, qa);
        DSLContext dslContext = db(shardingKey);

        // 2. 构建查询游标，使用LEFT JOIN联查QA和references数据
        try (Cursor<Record> cursor = dslContext
                .select(DATASET_QA.fields())
                .select(DATASET_QA_REFERENCE.fields())
                .from(DATASET_QA)
                .leftJoin(DATASET_QA_REFERENCE)
                .on(DATASET_QA.ITEM_ID.eq(DATASET_QA_REFERENCE.ITEM_ID)
                        .and(DATASET_QA.DATASET_ID.eq(DATASET_QA_REFERENCE.DATASET_ID))
                        .and(DATASET_QA_REFERENCE.STATUS.eq(0)))
                .where(DATASET_QA.DATASET_ID.eq(datasetId))
                .and(DATASET_QA.STATUS.eq(0))
                .orderBy(DATASET_QA.ID.asc(), DATASET_QA_REFERENCE.ID.asc())
                .fetchLazy()) {

            // 3. 流式处理：逐个QA收集其所有references，然后输出
            String currentItemId = null;
            DatasetQaDB currentQa = null;
            List<DatasetQaReferenceDB> currentReferences = new ArrayList<>();

            for (Record record : cursor) {
                DatasetQaDB qa = record.into(DATASET_QA.getRecordType()).into(DatasetQaDB.class);
                String itemId = qa.getItemId();

                if(!itemId.equals(currentItemId)) {
                    // 先输出上一个QA（如果存在）
                    if(currentQa != null) {
                        DatasetService.DatasetQaWithReferences qaWithRefs = new DatasetService.DatasetQaWithReferences(currentQa,
                                new ArrayList<>(currentReferences));
                        qaConsumer.accept(qaWithRefs);
                    }

                    currentItemId = itemId;
                    currentQa = qa;
                    currentReferences.clear();
                }

                // 收集当前QA的reference（如果存在）
                if(record.get(DATASET_QA_REFERENCE.ID) != null) {
                    DatasetQaReferenceDB reference = record.into(DATASET_QA_REFERENCE.getRecordType()).into(DatasetQaReferenceDB.class);
                    currentReferences.add(reference);
                }
            }

            // 输出最后一个QA
            if(currentQa != null) {
                DatasetService.DatasetQaWithReferences qaWithRefs = new DatasetService.DatasetQaWithReferences(currentQa,
                        new ArrayList<>(currentReferences));
                qaConsumer.accept(qaWithRefs);
            }
        }
    }
}
