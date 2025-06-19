package com.ke.bella.files.service;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.ke.bella.files.DatasetShardingCountUpdator;
import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.db.repo.DatasetRepo;
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.protocol.DatasetOps.DatasetOp;
import com.ke.bella.files.protocol.DatasetOps.DatasetPage;
import com.ke.bella.files.protocol.DatasetOps.QAOp;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatasetService {

    @Resource
    DatasetRepo repo;

    @Resource
    DatasetShardingCountUpdator counter;

    public DatasetDB createDataset(DatasetOp op) {
        return repo.addDataset(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetDB updateDataset(DatasetOp op) {
        repo.updateDataset(op);
        return repo.getDataset(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetDB deleteDataset(DatasetOp op) {
        repo.deleteDataset(op);
        return repo.getDataset(op, -1);
    }

    public DatasetDB getDataset(DatasetOp op) {
        return repo.getDataset(op);
    }

    public Page<DatasetDB> pageDataset(DatasetPage page) {
        return repo.pageDataset(page);
    }

    public Object import0(String datasetId) {
        // Implementation for importing dataset
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaDB createQa(QAOp op) {
        DatasetQaDB qaDB = repo.addQa(op);

        if(qaDB.getId() != null) {
            TaskExecutor.submit(() -> counter.increase(qaDB.getDatasetShardingKey()));
        }

        if(!CollectionUtils.isEmpty(op.getReferences())) {
            repo.addQaReferences(qaDB.getItemId(), op.getDatasetId(), op.getReferences());
        }
        return qaDB;
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaDB updateQa(QAOp op) {
        repo.updateQa(op);
        return repo.getQa(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaDB deleteQa(QAOp op) {
        repo.deleteQa(op);
        return repo.getQa(op, -1);
    }

    public DatasetQaDB getQa(QAOp op) {
        return repo.getQa(op);
    }

    public Page<DatasetQaDB> pageQa(DatasetOps.QaPage op) {
        return repo.pageQa(op);
    }

    public List<DatasetQaDB> listQa(DatasetOps.QaPage op) {
        return repo.listQa(op);
    }

    public DatasetQaReferenceDB createQaReference(DatasetOps.QAReferenceOp op) {
        return repo.addQaReference(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaReferenceDB updateQaReference(DatasetOps.QAReferenceOp op) {
        repo.updateQaReference(op);
        return repo.getQaReference(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaReferenceDB deleteQaReference(DatasetOps.QAReferenceOp op) {
        repo.deleteQaReference(op);
        return repo.getQaReference(op, -1);
    }

    public DatasetQaReferenceDB getQaReference(DatasetOps.QAReferenceOp op) {
        return repo.getQaReference(op);
    }

    public Page<DatasetQaReferenceDB> pageQaReferences(DatasetOps.QaReferencePage op) {
        return repo.pageQaReferences(op);
    }

    public List<DatasetQaReferenceDB> listQaReferences(DatasetOps.QaReferencePage op) {
        return repo.listQaReferences(op);
    }

    @PostConstruct
    public void init() {
        // update counter every 5s.
        TaskExecutor.scheduleAtFixedRate(() -> counter.flush(), 5);
        // try sharding every 60s.
        TaskExecutor.scheduleAtFixedRate(() -> counter.trySharding(), 60);
    }
}
