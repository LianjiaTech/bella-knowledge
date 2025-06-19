package com.ke.bella.files.api;

import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@FileAPI
@RestController
@RequestMapping("/v1/datasets")
@Slf4j
public class DatasetController {

    @Resource
    DatasetService ds;

    private void checkDatasetExist(String datasetId) {
        DatasetDB dataset = ds.getDataset(DatasetOp.builder()
                .datasetId(datasetId)
                .build());

        Assert.notNull(dataset, "dataset not found for dataset_id: " + datasetId);
    }

    private void checkQaExist(String itemId) {
        DatasetQaDB qa = ds.getQa(QAOp.builder()
                .itemId(itemId)
                .build());

        Assert.notNull(qa, "qa not found for item_id: " + itemId);
    }

    @PostMapping("/create")
    public DatasetDB create(@RequestBody DatasetOp op) {
        Assert.hasText(op.getName(), "dataset name must not be empty");
        Assert.isTrue(DatasetOps.DatasetType.qa.name().equals(op.getType()),
                String.format("dataset type only support 'qa' currently, but got: %s", op.getType()));

        return ds.createDataset(op);
    }

    @PostMapping("/update")
    public DatasetDB update(@RequestBody DatasetOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(DatasetOps.DatasetType.qa.name().equals(op.getType()),
                String.format("dataset type only support 'qa' currently, but got: %s", op.getType()));

        return ds.updateDataset(op);
    }

    @PostMapping("/delete")
    public DatasetDB delete(@RequestBody DatasetOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        return ds.deleteDataset(op);
    }

    @PostMapping("/get")
    public DatasetDB get(@RequestBody DatasetOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        DatasetDB dataset = ds.getDataset(op);

        Assert.notNull(dataset, "dataset not found for dataset_id: " + op.getDatasetId());

        return dataset;
    }

    @PostMapping("/page")
    public Page<DatasetDB> page(@RequestBody DatasetPage page) {
        return ds.pageDataset(page);
    }

    @PostMapping("/import")
    public Object import0(
            @RequestParam(name = "file_id", required = false) String fileId,
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(name = "dataset_id", required = false) String datasetId,
            @RequestParam(name = "dataset_name", required = false) String datasetName,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "remark", required = false) String remark) {
        return null;
    }

    @GetMapping("/export")
    public void export(@RequestParam(name = "dataset_id") String datasetId) {
        // todo
    }

    @PostMapping("/qa/create")
    public DatasetQaDB create(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getQuestion(), "question must not be empty");

        checkDatasetExist(op.getDatasetId());

        return ds.createQa(op);
    }

    @PostMapping("/qa/update")
    public DatasetQaDB update(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getItemId(), "item_id must not be empty");

        return ds.updateQa(op);
    }

    @PostMapping("/qa/delete")
    public DatasetQaDB delete(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getItemId(), "item_id must not be empty");

        return ds.deleteQa(op);
    }

    @PostMapping("/qa/get")
    public DatasetQaDB get(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getItemId(), "item_id must not be empty");

        DatasetQaDB qa = ds.getQa(op);
        Assert.notNull(qa, "qa not found for dataset_id: " + op.getDatasetId() + ", item_id: " + op.getItemId());

        return qa;
    }

    @PostMapping("/qa/page")
    public Page<DatasetQaDB> page(@RequestBody DatasetOps.QaPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        return ds.pageQa(op);
    }

    @PostMapping("/qa/list")
    public ListResponse<List<DatasetQaDB>> list(@RequestBody DatasetOps.QaPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        List<DatasetQaDB> datasetQaDBS = ds.listQa(op);

        return ListResponse.<List<DatasetQaDB>>builder()
                .data(datasetQaDBS)
                .total(CollectionUtils.isEmpty(datasetQaDBS) ? 0 : datasetQaDBS.size())
                .build();
    }

    @PostMapping("/qa/reference/create")
    public DatasetQaReferenceDB create(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getItemId(), "item_id must not be empty");
        Assert.hasText(op.getFileId(), "fileId must not be empty");

        checkDatasetExist(op.getDatasetId());

        checkQaExist(op.getItemId());

        return ds.createQaReference(op);
    }

    @PostMapping("/qa/reference/update")
    public DatasetQaReferenceDB update(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        return ds.updateQaReference(op);
    }

    @PostMapping("/qa/reference/delete")
    public DatasetQaReferenceDB delete(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        return ds.deleteQaReference(op);
    }

    @PostMapping("/qa/reference/get")
    public DatasetQaReferenceDB get(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getReferenceId(), "referenceId must not be empty");

        DatasetQaReferenceDB qaReference = ds.getQaReference(op);

        Assert.notNull(qaReference, "qa reference not found for dataset_id: " + op.getDatasetId() + ", reference_id: " + op.getReferenceId());

        return qaReference;
    }

    @PostMapping("/qa/reference/page")
    public Page<DatasetQaReferenceDB> page(@RequestBody DatasetOps.QaReferencePage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        return ds.pageQaReferences(op);
    }

    @PostMapping("/qa/reference/list")
    public ListResponse<List<DatasetQaReferenceDB>> list(@RequestBody DatasetOps.QaReferencePage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        List<DatasetQaReferenceDB> referenceDBS = ds.listQaReferences(op);

        return ListResponse.<List<DatasetQaReferenceDB>>builder()
                .data(referenceDBS)
                .total(CollectionUtils.isEmpty(referenceDBS) ? 0 : referenceDBS.size())
                .build();
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @SuperBuilder(toBuilder = true)
    public static class ListResponse<T> {
        private T data;
        private Integer total;
    }

}
