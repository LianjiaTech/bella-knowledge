package com.ke.bella.files.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
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
import com.ke.bella.files.db.tables.pojos.DatasetDocumentDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.protocol.DatasetOps.DatasetImportingProgress;
import com.ke.bella.files.protocol.DatasetOps.DatasetOp;
import com.ke.bella.files.protocol.DatasetOps.DatasetPage;
import com.ke.bella.files.protocol.DatasetOps.QAOp;
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.DatasetService;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.utils.JsonUtils;

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

    private DatasetDB checkDataset(String datasetId, DatasetOps.DatasetType expectedType) {
        return checkDataset(datasetId, expectedType, true);
    }

    private DatasetDB checkDataset(String datasetId, DatasetOps.DatasetType expectedType, Boolean withSpaceCode) {
        DatasetDB dataset;
        if(withSpaceCode) {
            dataset = ds.getDataset(DatasetOp.builder()
                    .datasetId(datasetId)
                    .build());
        } else {
            dataset = ds.getDatasetWithoutSpaceCode(DatasetOp.builder()
                    .datasetId(datasetId)
                    .build());
        }

        Assert.notNull(dataset, "dataset not found for dataset_id: " + datasetId);
        Assert.isTrue(expectedType.name().equals(dataset.getType()),
                String.format("dataset type mismatch: expected '%s', but got '%s'", expectedType, dataset.getType()));
        return dataset;
    }

    private DatasetQaDB checkQaExist(String datasetId, String itemId) {
        DatasetQaDB qa = ds.getQa(QAOp.builder()
                .datasetId(datasetId)
                .itemId(itemId)
                .build());

        Assert.notNull(qa, "qa not found for item_id: " + itemId);
        return qa;
    }

    private List<OpenAIFile> checkFilesExist(List<String> fileIds) {
        ListFileOps ops = new ListFileOps();
        ops.setFileIds(fileIds);
        List<OpenAIFile> existingFiles = fs.getFiles(ops);

        if(existingFiles.size() != fileIds.size()) {
            Set<String> existingFileIds = existingFiles.stream()
                    .map(OpenAIFile::getId)
                    .collect(Collectors.toSet());

            List<String> missingFileIds = fileIds.stream()
                    .filter(fileId -> !existingFileIds.contains(fileId))
                    .collect(Collectors.toList());

            Assert.isTrue(false, "files not found for file_ids: " + JsonUtils.toJson(missingFileIds));
        }

        return existingFiles;
    }

    @PostMapping("/create")
    public DatasetDB create(@RequestBody DatasetOp op) {
        Assert.hasText(op.getName(), "dataset name must not be empty");
        Assert.isTrue(Arrays.stream(DatasetOps.DatasetType.values())
                .map(DatasetOps.DatasetType::name)
                .collect(Collectors.toList())
                .contains(op.getType()), String.format("dataset type must be one of: %s", Arrays.toString(DatasetOps.DatasetType.values())));

        return ds.createDataset(op);
    }

    @PostMapping("/update")
    public DatasetDB update(@RequestBody DatasetOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isNull(op.getType(), "dataset type cannot be updated");
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
        Assert.isTrue(page.getOrder().equals("desc") || page.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + page.getOrder());
        Assert.isTrue(page.getOrderBy().equals("ctime") || page.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + page.getOrderBy());

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
            dataset = checkDataset(datasetId, DatasetOps.DatasetType.qa);
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

        FileService.InputStreamWithCharset inputStream = null;
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
                totalRows = ds.processExcelFile(inputStream.getInputStream(), dataset, batchSize, progressCallback);
            } else if(isCSV) {
                totalRows = ds.processCSVFile(inputStream.getInputStream(), inputStream.getCharset(), dataset, batchSize, progressCallback);
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
            if(inputStream != null && inputStream.getInputStream() != null) {
                try {
                    inputStream.getInputStream().close();
                } catch (Exception e) {
                    LOGGER.error("failed to close input stream", e);
                }
            }
        }
    }

    @GetMapping("/export")
    public FileUrl export(@RequestParam(name = "dataset_id") String datasetId,
            @RequestParam(name = "expires", defaultValue = "3600") Long expires) {
        Assert.hasText(datasetId, "dataset_id must not be empty");

        DatasetDB datasetDB = checkDataset(datasetId, DatasetOps.DatasetType.qa);

        if(datasetDB.getLatestExportTime().isAfter(datasetDB.getMtime())) {
            LOGGER.info("using cached export file for dataset_id: {}, latest_export_time: {}, mtime: {}",
                    datasetId, datasetDB.getLatestExportTime(), datasetDB.getMtime());
            String cachedFileId = Optional.ofNullable(datasetDB.getLatestExportFileId())
                    .orElseThrow(() -> new IllegalStateException("latest_export_file_id is null, but latest_export_time is after mtime"));
            String url = fs.getUrl(cachedFileId, expires);
            return FileUrl.builder().url(url).build();
        }

        String filename = datasetId + ".jsonl";
        File tempFile = null;

        try {
            tempFile = Files.createTempFile("dataset-export-" + datasetId, ".jsonl").toFile();

            try (FileOutputStream fos = new FileOutputStream(tempFile);
                    OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

                ds.streamQaWithReferences(datasetId, qaWithRefs -> writeQaData(writer, qaWithRefs));
            }

            OpenAIFile uploadedFile = fs.upload(
                    tempFile,
                    filename,
                    "datasets_export",
                    null,
                    "text/plain",
                    "text",
                    "jsonl",
                    StandardCharsets.UTF_8.name());

            DatasetOps.DatasetOp op = DatasetOps.DatasetOp.builder()
                    .datasetId(datasetId)
                    .latestExportTime(LocalDateTime.now())
                    .latestExportFileId(uploadedFile.getId())
                    .build();
            ds.updateDataset(op);

            String url = fs.getUrl(uploadedFile.getId(), expires);
            return FileUrl.builder().url(url).build();
        } catch (IOException e) {
            LOGGER.error("error creating or uploading export file, e: ", e);
            throw new IllegalStateException("error creating or uploading export file, e: " + e.getMessage());
        } finally {
            if(tempFile != null && tempFile.exists()) {
                try {
                    Files.delete(tempFile.toPath());
                } catch (IOException e) {
                    LOGGER.warn("failed to delete temp file: " + tempFile.getAbsolutePath(), e);
                }
            }
        }
    }

    @PostMapping("/qa/create")
    public DatasetQaDB create(@RequestBody QAOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getQuestion(), "question must not be empty");

        checkDataset(op.getDatasetId(), DatasetOps.DatasetType.qa);

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
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.pageQa(op);
    }

    @PostMapping("/qa/list")
    public List<DatasetQaDB> list(@RequestBody DatasetOps.QaPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.listQa(op);
    }

    @PostMapping("/qa/reference/create")
    public DatasetQaReferenceDB create(@RequestBody DatasetOps.QAReferenceOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getItemId(), "item_id must not be empty");
        Assert.hasText(op.getFileId(), "fileId must not be empty");

        checkDataset(op.getDatasetId(), DatasetOps.DatasetType.qa);

        checkQaExist(op.getDatasetId(), op.getItemId());

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
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.pageQaReferences(op);
    }

    @PostMapping("/qa/reference/list")
    public List<DatasetQaReferenceDB> list(@RequestBody DatasetOps.QaReferencePage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.listQaReferences(op);
    }

    @PostMapping("/documents/create")
    public List<DatasetDocumentDB> create(@RequestBody DatasetOps.DocumentCreateOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(!CollectionUtils.isEmpty(op.getFileIds()), "file_ids must not be empty. but got: " + JsonUtils.toJson(op.getFileIds()));

        checkDataset(op.getDatasetId(), DatasetOps.DatasetType.document);
        checkFilesExist(op.getFileIds());

        return ds.createDocuments(op);
    }

    @PostMapping("/documents/delete")
    public DatasetDocumentDB delete(@RequestBody DatasetOps.DocumentOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getFileId(), "file_id must not be empty");

        return ds.deleteDocument(op);
    }

    @PostMapping("/documents/get")
    public DatasetDocumentDB get(@RequestBody DatasetOps.DocumentOp op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.hasText(op.getFileId(), "file_id must not be empty");

        DatasetDocumentDB document = ds.getDocument(op);
        Assert.notNull(document, "document not found for dataset_id: " + op.getDatasetId() + ", file_id: " + op.getFileId());

        return document;
    }

    @PostMapping("/documents/page")
    public Page<DatasetDocumentDB> page(@RequestBody DatasetOps.DocumentPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.pageDocument(op);
    }

    @PostMapping("/documents/list")
    public List<DatasetDocumentDB> list(@RequestBody DatasetOps.DocumentPage op) {
        Assert.hasText(op.getDatasetId(), "dataset_id must not be empty");
        Assert.isTrue(op.getOrder().equals("desc") || op.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + op.getOrder());
        Assert.isTrue(op.getOrderBy().equals("ctime") || op.getOrderBy().equals("mtime"),
                "order_by must be 'ctime' or 'mtime', but got: " + op.getOrderBy());

        return ds.listDocument(op);
    }

    private void writeQaData(OutputStreamWriter writer, DatasetService.DatasetQaWithReferences qaWithRefs) {
        try {
            DatasetQaDB qa = qaWithRefs.getQa();
            List<DatasetQaReferenceDB> references = qaWithRefs.getReferences();

            Map<String, Object> qaData = new LinkedHashMap<>();
            qaData.put("question", qa.getQuestion());
            qaData.put("answer", qa.getAnswer());

            if(StringUtils.hasText(qa.getSimilarQ1())) {
                qaData.put("similar_q1", qa.getSimilarQ1());
            }
            if(StringUtils.hasText(qa.getSimilarQ2())) {
                qaData.put("similar_q2", qa.getSimilarQ2());
            }
            if(StringUtils.hasText(qa.getSimilarQ3())) {
                qaData.put("similar_q3", qa.getSimilarQ3());
            }

            if(!CollectionUtils.isEmpty(references)) {
                List<Map<String, Object>> refList = references.stream()
                        .map(ref -> {
                            Map<String, Object> refData = new LinkedHashMap<>();
                            refData.put("file_id", ref.getFileId());
                            refData.put("path", ref.getPath());
                            return refData;
                        })
                        .collect(Collectors.toList());
                qaData.put("references", refList);
            }

            writer.write(JsonUtils.toJson(qaData));
            writer.write("\n");
        } catch (IOException e) {
            throw new IllegalStateException("error writing QA data. e: ", e);
        }
    }
}
