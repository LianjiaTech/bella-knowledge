package com.ke.bella.files;

import static com.ke.bella.files.protocol.DatasetOps.DatasetType.document;
import static com.ke.bella.files.protocol.DatasetOps.DatasetType.qa;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.files.db.repo.DatasetRepo;
import com.ke.bella.files.db.tables.pojos.DatasetShardingDB;
import com.ke.bella.files.protocol.DatasetOps.DatasetType;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatasetShardingCountUpdator {

    @Resource
    DatasetRepo repo;

    // QA类型的计数缓存
    private final Cache<String, AtomicLong> qaDeltas = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build();

    // Document类型的计数缓存
    private final Cache<String, AtomicLong> documentDeltas = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build();

    public void increase(String datasetShardingKey, DatasetType type) {
        increase(datasetShardingKey, 1L, type);
    }

    public void increase(String datasetShardingKey, Long count, DatasetType type) {
        increase0(datasetShardingKey, count, type);
    }

    public void increase0(String datasetShardingKey, Long count, DatasetType type) {
        try {
            Cache<String, AtomicLong> targetCache = getCache(type.name());
            targetCache.get(datasetShardingKey, new Callable<AtomicLong>() {
                @Override
                public AtomicLong call() throws Exception {
                    return new AtomicLong(0);
                }
            }).addAndGet(count);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Cache<String, AtomicLong> getCache(String type) {
        return qa.name().equals(type) ? qaDeltas : documentDeltas;
    }

    public void flush() {
        flushByType(qa.name());
        flushByType(document.name());
    }

    private void flushByType(String type) {
        Cache<String, AtomicLong> targetCache = getCache(type);
        targetCache.asMap().forEach((k, d) -> {
            long v = d.get();
            if(v > 0) {
                LOGGER.info("update dataset {} count, sharding: {}, delta: {}", type, k, v);
                repo.increaseShardingCount(k, v, type);
                d.addAndGet(-v);
            }
        });
    }

    public void trySharding() {
        trySharding(qa.name());
        trySharding(document.name());
    }

    public void trySharding(String type) {
        try {
            DatasetShardingDB sharding = repo.queryLatestDatasetSharding(type);
            if(sharding.getCount().longValue() >= sharding.getMaxCount().longValue()) {

                LOGGER.info("creating new dataset {} sharding, last_key: {}", type, sharding.getKey());
                TaskExecutor.submit(() -> repo.newShardingTable(sharding.getKey(), type));
            }
        } catch (Throwable t) {
            LOGGER.error("trySharding error for type {}, t: ", type, t);
        }
    }
}
