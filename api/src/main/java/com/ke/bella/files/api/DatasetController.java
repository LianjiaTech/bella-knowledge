package com.ke.bella.files.api;

import java.io.InputStream;
import java.util.List;

import javax.annotation.Resource;

import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.configuration.Configs;
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.protocol.DatasetOps.DatasetImportingProgress;
import com.ke.bella.files.protocol.DatasetOps.DatasetOp;
import com.ke.bella.files.protocol.DatasetOps.DatasetPage;
import com.ke.bella.files.protocol.DatasetOps.QAOp;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.DatasetService;
import com.ke.bella.files.service.FileService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1/datasets")
@Slf4j
public class DatasetController {

    private static final String DATASET_IMPORT_PROGRESS = "datasets_import";

    @Resource
    DatasetService ds;
    @Resource
    FileService fs;

    private DatasetDB checkDatasetExist(String datasetId) {
        DatasetDB dataset = ds.getDataset(DatasetOp.builder()
                .datasetId(datasetId)
                .build());

        Assert.notNull(dataset, "dataset not found for dataset_id: " + datasetId);
        return dataset;
    }

    private DatasetQaDB checkQaExist(String itemId) {
        DatasetQaDB qa = ds.getQa(QAOp.builder()
                .itemId(itemId)
                .build());

        Assert.notNull(qa, "qa not found for item_id: " + itemId);
        return qa;
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
    public DatasetDB import0(
            @RequestParam(name = "file_id", required = false) String fileId,
            @RequestParam(name = "dataset_id", required = false) String datasetId,
            @RequestParam(name = "dataset_name", required = false) String datasetName,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "remark", required = false) String remark) {
        Assert.isTrue(fileId != null, "file_id must be provided");

        // step1: check dataset and init if necessary
        DatasetDB dataset = null;
        if(StringUtils.isEmpty(datasetId)) {
            Assert.hasText(datasetName, "dataset_name must not be empty when dataset_id is not provided");

            dataset = create(DatasetOp.builder()
                    .name(datasetName)
                    .type(DatasetOps.DatasetType.qa.name())
                    .remark(remark)
                    .build());
        } else {
            dataset = checkDatasetExist(datasetId);
        }

        // step2: import from file using another thread
        // todo: file broadcasting
        DatasetDB finalDataset = dataset;
        TaskExecutor.submit(() -> {
            try {

                TriConsumer<DatasetImportingProgress, Integer, String> progressCallback = (status, percent,
                        message) -> fs.updateProgress(UpdateProgressRequestData.builder()
                                .status(status.name())
                                .percent(percent)
                                .message(message)
                                .build(), fileId, DATASET_IMPORT_PROGRESS);

                processImport(fileId, finalDataset, progressCallback);
            } catch (Exception e) {
                LOGGER.error("failed to process import for file_id: {}, dataset_id: {}, e: ", fileId, finalDataset.getDatasetId(), e);
            }
        });

        return dataset;
    }

    private DatasetDB processImport(String fileId, DatasetDB dataset, TriConsumer<DatasetImportingProgress, Integer, String> progressCallback) {
        progressCallback.accept(DatasetImportingProgress.preprocessing, 0, DatasetImportingProgress.preprocessing.getDescription());

        InputStream inputStream = null;
        try {
            inputStream = fs.getFileInputStream(fileId);

            String fileName = fs.getFile(fileId).getFilename();
            Assert.hasText(fileName, "file name must not be empty. file_id: " + fileId);

            boolean isExcel = (fileName.toLowerCase().endsWith(".xlsx") || fileName.toLowerCase().endsWith(".xls"));
            boolean isCSV = fileName.toLowerCase().endsWith(".csv");

            Assert.isTrue(isExcel || isCSV, "unsupported file type. only Excel (.xlsx, .xls) and CSV (.csv) files are supported.");

            // setting batch size
            final int batchSize = Configs.DATASETS_IMPORT_BATCH_SIZE;
            int totalRows = 0;

            progressCallback.accept(DatasetImportingProgress.processing, 0, "开始批量处理数据");

            if(isExcel) {
                totalRows = ds.processExcelFile(inputStream, dataset, batchSize, progressCallback);
            } else if(isCSV) {
                totalRows = ds.processCSVFile(inputStream, dataset, batchSize, progressCallback);
            }

            if(totalRows == 0) {
                progressCallback.accept(DatasetImportingProgress.failed, 0, "no valid qa data found");
                throw new IllegalStateException("no valid qa data found in the file");
            }

            progressCallback.accept(DatasetImportingProgress.completed, 100,
                    String.format("dataset import completed，total: %d", totalRows));
            return dataset;
        } catch (Exception e) {
            LOGGER.error("failed to import dataset, e: ", e);
            progressCallback.accept(DatasetImportingProgress.failed, 0, "failed to import dataset, e: " + e.getMessage());
            throw new IllegalStateException("failed to import dataset, " + e.getMessage());
        } finally {
            if(inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    LOGGER.error("failed to close input stream", e);
                }
            }
        }
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
    public List<DatasetQaDB> list(@RequestBody DatasetOps.QaPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        return ds.listQa(op);
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
    public List<DatasetQaReferenceDB> list(@RequestBody DatasetOps.QaReferencePage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");

        return ds.listQaReferences(op);
    }

}
