package com.ke.bella.files;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.files.db.repo.DatasetRepo;
import com.ke.bella.files.db.tables.pojos.DatasetShardingDB;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatasetShardingCountUpdator {

    @Resource
    DatasetRepo repo;

    private final Cache<String, AtomicLong> deltas = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build();

    public void increase(String datasetShardingKey) {
        increase(datasetShardingKey, 1L);
    }

    public void increase(String datasetShardingKey, Long count) {
        try {
            deltas.get(datasetShardingKey, new Callable<AtomicLong>() {
                @Override
                public AtomicLong call() throws Exception {
                    return new AtomicLong(0);
                }
            }).addAndGet(count);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void flush() {
        deltas.asMap().forEach((k, d) -> {
            long v = d.get();
            if(v > 0) {
                LOGGER.info("update dataset qa count, sharding: {}, delta: {}", k, v);
                repo.increaseShardingCount(k, v);
                d.addAndGet(-v);
            }
        });
    }

    public void trySharding() {
        try {
            DatasetShardingDB sharding = repo.queryLatestDatasetSharding();
            if(sharding.getCount().longValue() >= sharding.getMaxCount().longValue()) {

                LOGGER.info("creating new dataset sharding, last_key: {}", sharding.getKey());
                TaskExecutor.submit(() -> repo.newShardingTable(sharding.getKey()));
            }
        } catch (Throwable t) {
            LOGGER.error("trySharding error, t: ", t);
        }
    }
}
