package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.IDGenerator.DATASET_ID_GEN;
import static com.ke.bella.files.db.IDGenerator.QA_ID_GEN;
import static com.ke.bella.files.db.tables.Dataset.DATASET;
import static com.ke.bella.files.db.tables.DatasetQa.DATASET_QA;
import static com.ke.bella.files.db.tables.DatasetQaReference.DATASET_QA_REFERENCE;
import static com.ke.bella.files.db.tables.DatasetSharding.DATASET_SHARDING;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.jooq.SelectSeekStep1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.db.tables.pojos.DatasetShardingDB;
import com.ke.bella.files.db.tables.records.DatasetQaRecord;
import com.ke.bella.files.db.tables.records.DatasetQaReferenceRecord;
import com.ke.bella.files.db.tables.records.DatasetRecord;
import com.ke.bella.files.db.tables.records.DatasetShardingRecord;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.utils.DigestUtils;
import com.ke.bella.openapi.BellaContext;

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

    public String shardingKeyByDatasetId(String datasetId) {
        return queryDatasetShardingByDatasetId(datasetId).getKey();
    }

    public DatasetShardingDB queryDatasetShardingByDatasetId(String datasetId) {
        LocalDateTime time = timeFromCode(datasetId);
        return db.selectFrom(DATASET_SHARDING)
                .where(DATASET_SHARDING.KEY_TIME.le(time))
                .orderBy(DATASET_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(DatasetShardingDB.class);
    }

    public DatasetShardingDB queryLatestDatasetSharding() {
        return db.selectFrom(DATASET_SHARDING)
                .orderBy(DATASET_SHARDING.ID.desc())
                .limit(1)
                .fetchOneInto(DatasetShardingDB.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void newShardingTable(String lastKey) {
        LocalDateTime keyTime = LocalDateTime.now().plusMinutes(10L);
        String key = keyTime.format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));

        DatasetShardingRecord rec = db.selectFrom(DATASET_SHARDING)
                .where(DATASET_SHARDING.LAST_KEY.eq(lastKey)).forUpdate().fetchOne();
        if(rec != null) {
            return;
        }

        db.execute(createTableLikeSql(DATASET_QA.getName(), key));
        db.execute(createTableLikeSql(DATASET_QA_REFERENCE.getName(), key));

        addDatasetSharding(keyTime, lastKey, key);
    }

    private static String createTableLikeSql(String tableName, String key) {
        return String.format("create table `%s_%s` like `%s`", tableName, key, tableName);
    }

    private void addDatasetSharding(LocalDateTime keyTime, String lastKey, String key) {
        DatasetShardingRecord rec = DATASET_SHARDING.newRecord();
        rec.setKey(key);
        rec.setKeyTime(keyTime);
        rec.setLastKey(lastKey);
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
        rec.set(DATASET.SPACE_CODE, BellaContext.getOperator().getSpaceCode());

        if(!StringUtils.isEmpty(op.getRemark())) {
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
                .and(DATASET.SPACE_CODE.eq(BellaContext.getOperator().getSpaceCode()))
                .and(DATASET.STATUS.eq(status))
                .fetchOneInto(DatasetDB.class);
    }

    public void updateDataset(DatasetOps.DatasetOp op) {
        DatasetRecord rec = DATASET.newRecord();

        if(!StringUtils.isEmpty(op.getName())) {
            rec.set(DATASET.NAME, op.getName());
        }

        if(!StringUtils.isEmpty(op.getType())) {
            rec.set(DATASET.TYPE, op.getType());
        }

        if(!StringUtils.isEmpty(op.getRemark())) {
            rec.set(DATASET.REMARK, op.getRemark());
        }

        fillUpdatorInfo(rec);

        int execute = db.update(DATASET)
                .set(rec)
                .where(DATASET.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "dataset update failed");
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
        SelectSeekStep1<DatasetRecord, Long> sql = db.selectFrom(DATASET)
                .where(StringUtils.isEmpty(page.getName()) ? DSL.noCondition()
                        : DATASET.NAME.like("%" + DSL.escape(page.getName(), '\\') + "%"))
                .and(StringUtils.isEmpty(page.getType()) ? DSL.noCondition()
                        : DATASET.TYPE.eq(page.getType()))
                .and(DATASET.SPACE_CODE.eq(BellaContext.getOperator().getSpaceCode()))
                .and(DATASET.STATUS.eq(0))
                .orderBy("asc".equals(page.getOrder()) ? DATASET.ID.asc() : DATASET.ID.desc());

        return queryPage(db, sql, page.getPage(), page.getPageSize(), DatasetDB.class);
    }

    public DatasetQaDB addQa(DatasetOps.QAOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());

        String itemId = QA_ID_GEN.generate();

        DatasetQaRecord rec = DATASET_QA.newRecord();
        rec.setItemId(itemId);
        rec.setDatasetId(op.getDatasetId());
        rec.setQuestion(op.getQuestion());
        rec.setAnswer(op.getAnswer());
        rec.setDatasetShardingKey(shardingKey);

        // todo similar_questions
        fillCreatorInfo(rec);

        return db(shardingKey).insertInto(DATASET_QA).set(rec).returningResult().fetchOne().into(DatasetQaDB.class);
    }

    public DatasetQaDB getQa(DatasetOps.QAOp op) {
        return getQa(op, 0);
    }

    public DatasetQaDB getQa(DatasetOps.QAOp op, Integer status) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        return db(shardingKey).selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA.STATUS.eq(status))
                .fetchOneInto(DatasetQaDB.class);
    }

    public void updateQa(DatasetOps.QAOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());

        DatasetQaRecord rec = DATASET_QA.newRecord();

        if(!StringUtils.isEmpty(op.getQuestion())) {
            rec.set(DATASET_QA.QUESTION, op.getQuestion());
        }

        if(!StringUtils.isEmpty(op.getAnswer())) {
            rec.set(DATASET_QA.ANSWER, op.getAnswer());
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
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
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
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        DSLContext db0 = db(shardingKey);

        SelectSeekStep1<DatasetQaRecord, Long> sql = db0.selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.STATUS.eq(0))
                .orderBy("asc".equals(op.getOrder()) ? DATASET_QA.ID.asc() : DATASET_QA.ID.desc());

        return queryPage(db0, sql, op.getPage(), op.getPageSize(), DatasetQaDB.class);
    }

    public List<DatasetQaDB> listQa(DatasetOps.QaPage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        DSLContext db0 = db(shardingKey);

        return db0.selectFrom(DATASET_QA)
                .where(DATASET_QA.DATASET_ID.eq(op.getDatasetId()))
                .and(DATASET_QA.STATUS.eq(0))
                .orderBy("asc".equals(op.getOrder()) ? DATASET_QA.ID.asc() : DATASET_QA.ID.desc()).fetch().into(DatasetQaDB.class);
    }

    public List<DatasetQaReferenceDB> addQaReferences(String itemId, String datasetId, List<DatasetOps.QAReferenceOp> referenceOps) {
        String shardingKey = shardingKeyByDatasetId(datasetId);

        List<DatasetQaReferenceDB> result = new ArrayList<>();
        for (DatasetOps.QAReferenceOp referenceOp : referenceOps) {
            String referenceId = genReferenceId(itemId, datasetId, referenceOp.getFileId());

            DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();

            rec.setItemId(itemId);
            rec.setDatasetId(datasetId);
            rec.setFileId(referenceOp.getFileId());
            rec.setReferenceId(referenceId);
            rec.setPath(referenceOp.getPath());

            fillCreatorInfo(rec);

            DatasetQaReferenceDB referenceDB = db(shardingKey).insertInto(DATASET_QA_REFERENCE).set(rec).returningResult().fetchOne()
                    .into(DatasetQaReferenceDB.class);

            result.add(referenceDB);
        }

        return result;
    }

    public DatasetQaReferenceDB getQaReference(DatasetOps.QAReferenceOp op) {
        return getQaReference(op, 0);
    }

    public DatasetQaReferenceDB getQaReference(DatasetOps.QAReferenceOp op, Integer status) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        return db(shardingKey).selectFrom(DATASET_QA_REFERENCE)
                .where(DATASET_QA_REFERENCE.REFERENCE_ID.eq(op.getReferenceId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(status))
                .fetchOneInto(DatasetQaReferenceDB.class);
    }

    public DatasetQaReferenceDB addQaReference(DatasetOps.QAReferenceOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());

        DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();

        rec.setItemId(op.getItemId());
        rec.setDatasetId(op.getDatasetId());
        rec.setFileId(op.getFileId());
        rec.setReferenceId(genReferenceId(op.getItemId(), op.getDatasetId(), op.getFileId()));
        rec.setPath(op.getPath());

        fillCreatorInfo(rec);

        return db(shardingKey).insertInto(DATASET_QA_REFERENCE)
                .set(rec)
                .returningResult()
                .fetchOne()
                .into(DatasetQaReferenceDB.class);
    }

    @NotNull
    private static String genReferenceId(String itemId, String datasetId, String fileId) {
        return "reference-" + DigestUtils.sha256(String.format("%s-%s-%s", itemId, datasetId, fileId));
    }

    public void updateQaReference(DatasetOps.QAReferenceOp op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());

        DatasetQaReferenceRecord rec = DATASET_QA_REFERENCE.newRecord();

        if(!StringUtils.isEmpty(op.getPath())) {
            rec.set(DATASET_QA_REFERENCE.PATH, op.getPath());
        }

        if(!StringUtils.isEmpty(op.getFileId())) {
            rec.set(DATASET_QA_REFERENCE.FILE_ID, op.getFileId());
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
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());

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
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        DSLContext dslContext = db(shardingKey);

        SelectSeekStep1<DatasetQaReferenceRecord, Long> sql = dslContext.selectFrom(DATASET_QA_REFERENCE)
                .where(StringUtils.isEmpty(op.getItemId()) ? DSL.noCondition() : DATASET_QA_REFERENCE.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0))
                .orderBy("asc".equals(op.getOrder()) ? DATASET_QA_REFERENCE.ID.asc() : DATASET_QA_REFERENCE.ID.desc());

        return queryPage(dslContext, sql, 1, 30, DatasetQaReferenceDB.class);
    }

    public List<DatasetQaReferenceDB> listQaReferences(DatasetOps.QaReferencePage op) {
        String shardingKey = shardingKeyByDatasetId(op.getDatasetId());
        DSLContext dslContext = db(shardingKey);

        return dslContext.selectFrom(DATASET_QA_REFERENCE)
                .where(StringUtils.isEmpty(op.getItemId()) ? DSL.noCondition() : DATASET_QA_REFERENCE.ITEM_ID.eq(op.getItemId()))
                .and(DATASET_QA_REFERENCE.STATUS.eq(0))
                .orderBy("asc".equals(op.getOrder()) ? DATASET_QA_REFERENCE.ID.asc() : DATASET_QA_REFERENCE.ID.desc())
                .fetch().into(DatasetQaReferenceDB.class);
    }

    public void increaseShardingCount(String key, long delta) {
        db.update(DATASET_SHARDING)
                .set(DATASET_SHARDING.COUNT, DATASET_SHARDING.COUNT.plus(delta))
                .set(DATASET_SHARDING.MTIME, LocalDateTime.now())
                .where(DATASET_SHARDING.KEY.eq(key))
                .execute();
    }

}
