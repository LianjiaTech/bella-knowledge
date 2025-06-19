package com.ke.bella.files.api;

import javax.annotation.Resource;

import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.protocol.DatasetOps.DatasetOp;
import com.ke.bella.files.protocol.DatasetOps.DatasetPage;
import com.ke.bella.files.protocol.DatasetOps.QAOp;
import com.ke.bella.files.service.DatasetService;

import lombok.extern.slf4j.Slf4j;

@FileAPI
@RestController
@RequestMapping("/v1/datasets")
@Slf4j
public class DatasetController {

    @Resource
    DatasetService ds;

    @PostMapping("/create")
    public DatasetDB create(@RequestBody DatasetOp op) {
        Assert.hasText(op.getName(), "dataset name must not be empty");
        Assert.isTrue(DatasetOps.DatasetType.qa.name().equals(op.getType()),
                String.format("dataset type only support 'qa' currently, but got: %s", op.getType()));

        return ds.createDataset(op);
    }

    @PostMapping("/update")
    public DatasetDB update(@RequestBody DatasetOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.isTrue(DatasetOps.DatasetType.qa.name().equals(op.getType()),
                String.format("dataset type only support 'qa' currently, but got: %s", op.getType()));

        return ds.updateDataset(op);
    }

    @PostMapping("/delete")
    public DatasetDB delete(@RequestBody DatasetOp op) {
        return ds.deleteDataset(op);
    }

    @PostMapping("/get")
    public DatasetDB get(@RequestBody DatasetOp op) {
        return ds.getDataset(op);
    }

    @GetMapping("/page")
    public Page<DatasetDB> page(DatasetPage page) {
        return ds.pageDataset(page);
    }

    @PostMapping("/import")
    public Object import0(
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(name = "dataset_id", required = false) String datasetId) {
        return null;
    }

    @GetMapping("/export")
    public void export(@RequestParam(name = "dataset_id") String datasetId) {
        // todo
    }

    @PostMapping("/qa/create")
    public DatasetQaDB create(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getQuestion(), "question must not be empty");

        return ds.createQa(op);
    }

    @PostMapping("/qa/update")
    public DatasetQaDB update(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getItemId(), "itemId must not be empty");

        return ds.updateQa(op);
    }

    @PostMapping("/qa/delete")
    public DatasetQaDB delete(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getItemId(), "itemId must not be empty");

        return ds.deleteQa(op);
    }

    @PostMapping("/qa/get")
    public DatasetQaDB get(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getItemId(), "itemId must not be empty");

        return ds.getQa(op);
    }

    @PostMapping("/qa/page")
    public Page<DatasetQaDB> page(@RequestBody DatasetOps.QaPage op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");

        return ds.pageQa(op);
    }

    @PostMapping("/qa/reference/create")
    public DatasetQaReferenceDB create(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getItemId(), "itemId must not be empty");
        Assert.hasText(op.getFileId(), "fileId must not be empty");

        return ds.createQaReference(op);
    }

    @PostMapping("/qa/reference/update")
    public DatasetQaReferenceDB update(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        return ds.updateQaReference(op);
    }

    @PostMapping("/qa/reference/delete")
    public DatasetQaReferenceDB delete(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        return ds.deleteQaReference(op);
    }

    @PostMapping("/qa/reference/get")
    public DatasetQaReferenceDB get(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        return ds.getQaReference(op);
    }

    @PostMapping("/qa/reference/page")
    public Page<DatasetQaReferenceDB> page(@RequestBody DatasetOps.QaReferencePage op) {
        Assert.hasText(op.getDatasetId(), "datasetId must not be empty");

        return ds.pageQaReferences(op);
    }

}
