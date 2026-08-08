package com.ke.bella.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ke.bella.files.db.repo.FileEntryRepo;
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

    public FileEntryBackfillRunner(FileEntryRepo fileEntryRepo) {
        this.fileEntryRepo = fileEntryRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        int processed = fileEntryRepo.backfillRange(spaceCode, minId, maxId);
        EntryConsistencyReport report = fileEntryRepo.compareSpace(spaceCode);
        LOGGER.info("file_entry backfill completed, spaceCode: {}, range: [{}, {}), processed: {}, activeFiles: {}, activeEntries: {}, duplicates: {}, orphanParents: {}, consistent: {}",
                spaceCode, minId, maxId, processed, report.getActiveFileCount(), report.getActiveEntryCount(),
                report.getDuplicateFileCount(), report.getOrphanParentCount(), report.isConsistent());
        if(!report.isConsistent()) {
            throw new IllegalStateException("file_entry backfill consistency check failed, spaceCode: " + spaceCode);
        }
    }
}
