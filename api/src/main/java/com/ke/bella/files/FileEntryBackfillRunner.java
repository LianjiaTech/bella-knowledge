package com.ke.bella.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ke.bella.files.db.repo.FileEntryRepo;
import com.ke.bella.files.db.repo.FileEntryRepo.BackfillBatchResult;
import com.ke.bella.files.db.repo.FileEntryRepo.EntryConsistencyReport;

@Component
@ConditionalOnProperty(name = "bella.file-api.file-entry.backfill.enabled", havingValue = "true")
public class FileEntryBackfillRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileEntryBackfillRunner.class);

    private final FileEntryRepo fileEntryRepo;

    @Value("${bella.file-api.file-entry.backfill.space-code}")
    private String spaceCode;

    @Value("${bella.file-api.file-entry.backfill.min-id:0}")
    private long minId;

    @Value("${bella.file-api.file-entry.backfill.max-id:9223372036854775807}")
    private long maxId;

    @Value("${bella.file-api.file-entry.backfill.batch-size:1000}")
    private int batchSize;

    public FileEntryBackfillRunner(FileEntryRepo fileEntryRepo) {
        this.fileEntryRepo = fileEntryRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if(batchSize <= 0) {
            throw new IllegalArgumentException("file_entry backfill batch-size must be positive");
        }
        int processed = 0;
        long cursor = minId;
        while(cursor < maxId) {
            BackfillBatchResult batch = fileEntryRepo.backfillBatch(spaceCode, cursor, maxId, batchSize);
            processed += batch.getProcessed();
            if(batch.getProcessed() == 0) {
                break;
            }
            LOGGER.info("file_entry backfill batch completed, spaceCode: {}, rangeStart: {}, nextRangeStart: {}, processed: {}",
                    spaceCode, cursor, batch.getNextMinId(), batch.getProcessed());
            cursor = batch.getNextMinId();
        }
        EntryConsistencyReport report = fileEntryRepo.compareSpace(spaceCode);
        LOGGER.info("file_entry backfill completed, spaceCode: {}, range: [{}, {}), processed: {}, activeFiles: {}, activeEntries: {}, duplicates: {}, orphanParents: {}, consistent: {}",
                spaceCode, minId, maxId, processed, report.getActiveFileCount(), report.getActiveEntryCount(),
                report.getDuplicateFileCount(), report.getOrphanParentCount(), report.isConsistent());
        if(!report.isConsistent()) {
            throw new IllegalStateException("file_entry backfill consistency check failed, spaceCode: " + spaceCode);
        }
    }
}
