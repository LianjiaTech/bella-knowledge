package com.ke.bella.files.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ke.bella.files.db.repo.FileEntryRepo;
import com.ke.bella.files.db.repo.FileEntryRepo.BackfillBatchResult;
import com.ke.bella.files.db.repo.FileEntryRepo.EntryConsistencyReport;

public final class FileEntryBackfillTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileEntryBackfillTool.class);

    private FileEntryBackfillTool() {
    }

    public static void main(String[] args) {
        ToolOptions options = ToolOptions.parse(args);
        if(options.help) {
            printUsage();
            return;
        }

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ToolConfiguration.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run(args)) {
            runBackfill(context.getBean(FileEntryRepo.class), options);
        }
    }

    private static void runBackfill(FileEntryRepo fileEntryRepo, ToolOptions options) {
        int processed = 0;
        long cursor = options.minId;
        while(cursor < options.maxId) {
            BackfillBatchResult batch = fileEntryRepo.backfillBatch(
                    options.spaceCode, cursor, options.maxId, options.batchSize);
            processed += batch.getProcessed();
            if(batch.getProcessed() == 0) {
                break;
            }
            LOGGER.info("file_entry backfill batch completed, spaceCode: {}, rangeStart: {}, nextRangeStart: {}, processed: {}",
                    options.spaceCode, cursor, batch.getNextMinId(), batch.getProcessed());
            cursor = batch.getNextMinId();
        }

        LOGGER.info("file_entry backfill completed, spaceCode: {}, range: [{}, {}), processed: {}",
                options.spaceCode, options.minId, options.maxId, processed);
        if(options.verify) {
            verify(fileEntryRepo, options.spaceCode);
        }
    }

    private static void verify(FileEntryRepo fileEntryRepo, String spaceCode) {
        EntryConsistencyReport report = fileEntryRepo.compareSpace(spaceCode);
        LOGGER.info("file_entry consistency check completed, spaceCode: {}, activeFiles: {}, activeEntries: {}, duplicates: {}, orphanParents: {}, consistent: {}",
                spaceCode, report.getActiveFileCount(), report.getActiveEntryCount(), report.getDuplicateFileCount(),
                report.getOrphanParentCount(), report.isConsistent());
        if(!report.isConsistent()) {
            throw new IllegalStateException("file_entry consistency check failed, spaceCode: " + spaceCode);
        }
    }

    private static void printUsage() {
        System.out.println("Usage: mvn -Pfile-entry-backfill-tool compile exec:java");
        System.out.println("  -Dexec.args=\"--space-code=<space> [--min-id=0] [--max-id=9223372036854775807] [--batch-size=1000] [--verify]\"");
        System.out.println("Database connection uses MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USER and MYSQL_PASSWORD.");
    }

    @Configuration
    @EnableTransactionManagement
    @Import(FileEntryRepo.class)
    @ImportAutoConfiguration({ DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JooqAutoConfiguration.class })
    static class ToolConfiguration {
    }

    static class ToolOptions {
        private final String spaceCode;
        private final long minId;
        private final long maxId;
        private final int batchSize;
        private final boolean verify;
        private final boolean help;

        private ToolOptions(String spaceCode, long minId, long maxId, int batchSize, boolean verify, boolean help) {
            this.spaceCode = spaceCode;
            this.minId = minId;
            this.maxId = maxId;
            this.batchSize = batchSize;
            this.verify = verify;
            this.help = help;
        }

        static ToolOptions parse(String[] args) {
            String spaceCode = null;
            long minId = 0L;
            long maxId = Long.MAX_VALUE;
            int batchSize = 1000;
            boolean verify = false;
            boolean help = false;
            for (String arg : args) {
                if("--help".equals(arg) || "-h".equals(arg)) {
                    help = true;
                } else if("--verify".equals(arg)) {
                    verify = true;
                } else if(arg.startsWith("--space-code=")) {
                    spaceCode = value(arg);
                } else if(arg.startsWith("--min-id=")) {
                    minId = parseLong("min-id", value(arg));
                } else if(arg.startsWith("--max-id=")) {
                    maxId = parseLong("max-id", value(arg));
                } else if(arg.startsWith("--batch-size=")) {
                    batchSize = parseInt("batch-size", value(arg));
                }
            }
            if(help) {
                return new ToolOptions(null, minId, maxId, batchSize, verify, true);
            }
            if(spaceCode == null || spaceCode.trim().isEmpty()) {
                throw new IllegalArgumentException("--space-code is required");
            }
            if(minId < 0 || maxId <= minId) {
                throw new IllegalArgumentException("backfill range must satisfy 0 <= min-id < max-id");
            }
            if(batchSize <= 0) {
                throw new IllegalArgumentException("batch-size must be positive");
            }
            return new ToolOptions(spaceCode, minId, maxId, batchSize, verify, false);
        }

        private static String value(String arg) {
            return arg.substring(arg.indexOf('=') + 1);
        }

        private static long parseLong(String name, String value) {
            try {
                return Long.parseLong(value);
            } catch(NumberFormatException e) {
                throw new IllegalArgumentException("--" + name + " must be a long integer", e);
            }
        }

        private static int parseInt(String name, String value) {
            try {
                return Integer.parseInt(value);
            } catch(NumberFormatException e) {
                throw new IllegalArgumentException("--" + name + " must be an integer", e);
            }
        }
    }
}
