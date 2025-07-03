package com.ke.bella.files.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.util.TriConsumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.files.DatasetShardingCountUpdator;
import com.ke.bella.files.TaskExecutor;
import com.ke.bella.files.db.repo.DatasetRepo;
import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.DatasetDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaDB;
import com.ke.bella.files.db.tables.pojos.DatasetQaReferenceDB;
import com.ke.bella.files.protocol.DatasetOps;
import com.ke.bella.files.protocol.DatasetOps.DatasetImportingProgress;
import com.ke.bella.files.protocol.DatasetOps.DatasetOp;
import com.ke.bella.files.protocol.DatasetOps.DatasetPage;
import com.ke.bella.files.protocol.DatasetOps.QAOp;
import com.ke.bella.files.utils.JsonUtils;

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

        repo.increaseQaCount(qaDB.getDatasetId());

        if(!CollectionUtils.isEmpty(op.getReferences())) {
            repo.addQaReferences(qaDB.getItemId(), op.getDatasetId(), op.getReferences());
        }

        repo.updateDatasetItems(qaDB.getDatasetId());

        return qaDB;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DatasetQaDB> createQas(List<QAOp> ops) {
        List<DatasetQaDB> result = new ArrayList<>();
        for (QAOp op : ops) {
            DatasetQaDB qa = createQa(op);
            result.add(qa);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaDB updateQa(QAOp op) {
        repo.updateQa(op);
        repo.updateDatasetItems(op.getDatasetId());
        return repo.getQa(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaDB deleteQa(QAOp op) {
        repo.deleteQa(op);
        repo.decreaseQaCount(op.getDatasetId());
        repo.updateDatasetItems(op.getDatasetId());
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

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaReferenceDB createQaReference(DatasetOps.QAReferenceOp op) {
        DatasetQaReferenceDB result = repo.addQaReference(op);
        repo.updateDatasetItems(op.getDatasetId());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaReferenceDB updateQaReference(DatasetOps.QAReferenceOp op) {
        repo.updateQaReference(op);
        repo.updateDatasetItems(op.getDatasetId());
        return repo.getQaReference(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public DatasetQaReferenceDB deleteQaReference(DatasetOps.QAReferenceOp op) {
        repo.deleteQaReference(op);
        repo.updateDatasetItems(op.getDatasetId());
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

    /**
     * Process Excel file import with batch processing
     *
     * @param inputStream      CSV file input stream
     * @param dataset          dataset
     * @param batchSize        number of rows to process in each batch
     * @param progressCallback callback to report progress
     *
     * @return the total number of processed rows
     *
     * @throws IOException if there is an issue reading the file
     */
    public int processExcelFile(InputStream inputStream, DatasetDB dataset, int batchSize,
            TriConsumer<DatasetImportingProgress, Integer, String> progressCallback)
            throws IOException {
        List<QAOp> qaOps = new ArrayList<>();
        int totalRowsProcessedValided = 0;
        int totalRowsProcessed = 0;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            // Only process the first worksheet
            Sheet sheet = workbook.getSheetAt(0);
            if(sheet == null) {
                throw new IllegalStateException("Excel file does not contain a worksheet");
            }

            // Get header row
            Row headerRow = sheet.getRow(0);
            if(headerRow == null) {
                throw new IllegalStateException("Excel file does not contain a header row");
            }

            // Parse header
            int questionIndex = -1;
            int similarQ1Index = -1;
            int similarQ2Index = -1;
            int similarQ3Index = -1;
            int answerIndex = -1;
            int referencesIndex = -1;

            int lastCellNum = headerRow.getLastCellNum();
            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i);
                if(cell != null) {
                    String header = getCellValueAsString(cell).toLowerCase().trim();
                    switch (header) {
                    case "question":
                        questionIndex = i;
                        break;
                    case "similar_q1":
                        similarQ1Index = i;
                        break;
                    case "similar_q2":
                        similarQ2Index = i;
                        break;
                    case "similar_q3":
                        similarQ3Index = i;
                        break;
                    case "answer":
                        answerIndex = i;
                        break;
                    case "references":
                        referencesIndex = i;
                        break;
                    }
                }
            }

            // Ensure required headers exist
            Assert.isTrue(questionIndex >= 0, "Excel file must contain 'question' column");

            // Calculate total number of rows (excluding header)
            int totalRows = sheet.getLastRowNum();
            if(totalRows <= 0) {
                return 0;
            }

            // Process data rows in batches
            Iterator<Row> rowIterator = sheet.rowIterator();
            // Skip header row
            if(rowIterator.hasNext()) {
                rowIterator.next();
            }

            int currentBatchSize = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                totalRowsProcessed++;
                if(isEmptyRow(row)) {
                    continue; // Skip empty rows
                }

                QAOp.QAOpBuilder<?, ?> qaBuilder = QAOp.builder()
                        .datasetId(dataset.getDatasetId());

                // Set question field (required)
                String question = getCellValueAsString(row.getCell(questionIndex)).trim();

                if(question.isEmpty()) {
                    LOGGER.warn("row {} has empty question content, question column is required", row.getRowNum() + 1);
                }
                qaBuilder.question(question);

                // Set similar_q1 field (optional)
                if(similarQ1Index >= 0) {
                    Cell similarQ1Cell = row.getCell(similarQ1Index);
                    if(similarQ1Cell != null) {
                        String similarQ1 = getCellValueAsString(similarQ1Cell).trim();
                        if(!similarQ1.isEmpty()) {
                            qaBuilder.similarQ1(similarQ1);
                        }
                    }
                }

                // Set similar_q2 field (optional)
                if(similarQ2Index >= 0) {
                    Cell similarQ2Cell = row.getCell(similarQ2Index);
                    if(similarQ2Cell != null) {
                        String similarQ2 = getCellValueAsString(similarQ2Cell).trim();
                        if(!similarQ2.isEmpty()) {
                            qaBuilder.similarQ2(similarQ2);
                        }
                    }
                }

                // Set similar_q3 field (optional)
                if(similarQ3Index >= 0) {
                    Cell similarQ3Cell = row.getCell(similarQ3Index);
                    if(similarQ3Cell != null) {
                        String similarQ3 = getCellValueAsString(similarQ3Cell).trim();
                        if(!similarQ3.isEmpty()) {
                            qaBuilder.similarQ3(similarQ3);
                        }
                    }
                }

                // Set answer field (optional)
                if(answerIndex >= 0) {
                    Cell answerCell = row.getCell(answerIndex);
                    if(answerCell != null) {
                        String answer = getCellValueAsString(answerCell).trim();
                        if(!answer.isEmpty()) {
                            qaBuilder.answer(answer);
                        }
                    }
                }

                // Set references field (optional)
                if(referencesIndex >= 0) {
                    Cell referencesCell = row.getCell(referencesIndex);
                    if(referencesCell != null) {
                        String referencesStr = getCellValueAsString(referencesCell).trim();
                        if(!referencesStr.isEmpty()) {
                            List<DatasetOps.QAReferenceOp> qaReferenceOps = JsonUtils.fromJson(referencesStr,
                                    new TypeReference<List<DatasetOps.QAReferenceOp>>() {
                                    });

                            if(!CollectionUtils.isEmpty(qaReferenceOps)) {
                                for (DatasetOps.QAReferenceOp qaReferenceOp : qaReferenceOps) {
                                    if(StringUtils.isEmpty(qaReferenceOp.getFileId()) || StringUtils.isEmpty(qaReferenceOp.getPath())) {
                                        throw new IllegalArgumentException("references must contain file_id and path, but got: " + referencesStr);
                                    }
                                }
                                qaBuilder.references(qaReferenceOps);
                            }
                        }
                    }
                }

                QAOp qaOp = qaBuilder.build();

                if(qaOp != null && qaOp.getQuestion() != null && !qaOp.getQuestion().isEmpty()) {
                    qaOps.add(qaOp);
                    currentBatchSize++;
                    totalRowsProcessedValided++;

                    // When we reach batch size, process the batch and clear for
                    // next batch
                    if(currentBatchSize >= batchSize) {
                        createQas(qaOps);
                        if(progressCallback != null) {
                            int percentage = (int) (double) totalRowsProcessed * 100 / totalRows;
                            progressCallback.accept(DatasetImportingProgress.processing, percentage,
                                    String.format("processing data %d/%d (%d%%), valid rows: %d", totalRowsProcessed, totalRows, percentage,
                                            totalRowsProcessedValided));
                        }
                        qaOps.clear();
                        currentBatchSize = 0;
                    }
                }
            }

            // Process remaining batch
            if(!qaOps.isEmpty()) {
                createQas(qaOps);
                if(progressCallback != null) {
                    int percentage = (int) (double) totalRowsProcessed * 100 / totalRows;
                    progressCallback.accept(DatasetImportingProgress.processing, percentage,
                            String.format("processing data %d/%d (%d%%), valid rows: %d", totalRowsProcessed, totalRows, percentage,
                                    totalRowsProcessedValided));
                }
            }
        }

        return totalRowsProcessedValided;
    }

    /**
     * Process CSV file import with batch processing
     *
     * @param inputStream      CSV file input stream
     * @param dataset          dataset
     * @param batchSize        number of rows to process in each batch
     * @param progressCallback callback to report progress (current, total)
     *
     * @return the total number of processed rows
     *
     * @throws Exception processing exception
     */
    public int processCSVFile(InputStream inputStream, String charset, DatasetDB dataset, int batchSize,
            TriConsumer<DatasetImportingProgress, Integer, String> progressCallback)
            throws Exception {
        List<QAOp> qaOps = new ArrayList<>();
        int totalProcessedRows = 0;
        int currentBatchSize = 0;

        // Use Apache Commons CSV to parse CSV files, which correctly handles
        // commas within quotes
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream,
                        (StringUtils.isEmpty(charset) || !Charset.isSupported(charset)) ? StandardCharsets.UTF_8 : Charset.forName(charset)));
        CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim());

        // Get header row information
        Map<String, Integer> headerMap = csvParser.getHeaderMap();
        if(headerMap == null || headerMap.isEmpty()) {
            throw new IllegalStateException("csv file is empty or has no headers");
        }

        // Check if necessary columns exist
        boolean hasQuestionColumn = headerMap.containsKey("question");
        Assert.isTrue(hasQuestionColumn, "csv file must contain 'question' column");

        // Iterate through CSV records
        for (CSVRecord record : csvParser) {
            if(record.size() == 0) {
                continue; // Skip empty rows
            }

            String question = record.isMapped("question") ? record.get("question") : null;
            if(StringUtils.isEmpty(question)) {
                LOGGER.warn("row {} has empty question column, question column is required", record.getRecordNumber() + 1);
                continue;
            }

            QAOp.QAOpBuilder qaBuilder = QAOp.builder()
                    .datasetId(dataset.getDatasetId())
                    .question(question);

            if(record.isMapped("similar_q1")) {
                qaBuilder.similarQ1(record.get("similar_q1"));
            }
            if(record.isMapped("similar_q2")) {
                qaBuilder.similarQ2(record.get("similar_q2"));
            }
            if(record.isMapped("similar_q3")) {
                qaBuilder.similarQ3(record.get("similar_q3"));
            }
            if(record.isMapped("answer")) {
                qaBuilder.answer(record.get("answer"));
            }
            if(record.isMapped("references") && !record.get("references").isEmpty()) {
                String referencesStr = record.get("references");
                List<DatasetOps.QAReferenceOp> qaReferenceOps = JsonUtils.fromJson(referencesStr,
                        new TypeReference<List<DatasetOps.QAReferenceOp>>() {
                        });
                if(!CollectionUtils.isEmpty(qaReferenceOps)) {
                    for (DatasetOps.QAReferenceOp qaReferenceOp : qaReferenceOps) {
                        if(StringUtils.isEmpty(qaReferenceOp.getFileId()) || StringUtils.isEmpty(qaReferenceOp.getPath())) {
                            throw new IllegalArgumentException("references must contain file_id and path, but got: " + referencesStr);
                        }
                    }
                    qaBuilder.references(qaReferenceOps);
                }
            }

            QAOp qaOp = qaBuilder.build();
            qaOps.add(qaOp);
            currentBatchSize++;
            totalProcessedRows++;

            // When we reach batch size, process the batch and clear for
            // next batch
            if(currentBatchSize >= batchSize) {
                createQas(qaOps);
                // Report progress
                if(progressCallback != null) {
                    // Increment progress by 1% for each processed row, but
                    // maximum up to 99%
                    int progressPercentage = Math.min(totalProcessedRows, 99);
                    // Use processed row count as progress indicator
                    progressCallback.accept(DatasetImportingProgress.processing, progressPercentage,
                            String.format("processing data, processed %d rows (%d%%)",
                                    totalProcessedRows, progressPercentage));
                }
                qaOps.clear();
                currentBatchSize = 0;
            }
        }
        // Process any remaining items in the final batch
        if(!qaOps.isEmpty()) {
            createQas(qaOps);
        }

        if(progressCallback != null) {
            progressCallback.accept(DatasetImportingProgress.processing, 100,
                    String.format("processing data, processed %d rows (100%%)",
                            totalProcessedRows));
        }

        return totalProcessedRows;
    }

    /**
     * Check if Excel row is empty
     *
     * @param row Excel row
     *
     * @return whether the row is empty
     */
    private boolean isEmptyRow(Row row) {
        if(row == null) {
            return true;
        }
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if(cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get string value from cell
     *
     * @param cell Excel cell
     *
     * @return string value of the cell
     */
    private String getCellValueAsString(Cell cell) {
        if(cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
        case STRING:
            return cell.getStringCellValue();
        case NUMERIC:
            return String.valueOf(cell.getNumericCellValue());
        case BOOLEAN:
            return String.valueOf(cell.getBooleanCellValue());
        case FORMULA:
            try {
                return cell.getStringCellValue();
            } catch (Exception e) {
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception ex) {
                    return "";
                }
            }
        default:
            return "";
        }
    }

    @PostConstruct
    public void init() {
        // update counter every 5s.
        TaskExecutor.scheduleAtFixedRate(() -> counter.flush(), 5);
        // try sharding every 60s.
        TaskExecutor.scheduleAtFixedRate(() -> counter.trySharding(), 60);
    }
}
