package com.ke.bella.files;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Resource;
import org.springframework.stereotype.Component;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ke.bella.files.db.repo.FileRepo;
import com.ke.bella.files.db.tables.pojos.FileShardingDB;
import com.ke.bella.files.enums.FileType;

/**
 * 文件分片计数更新器 参考DatasetShardingCountUpdator实现，管理file_temp和file_system的分片计数
 */
@Component
@Slf4j
public class FileShardingCountUpdator {

    @Resource
    FileRepo repo;

    // System类型的计数缓存
    private final Cache<String, AtomicLong> systemDeltas = CacheBuilder.newBuilder()
        .maximumSize(10)
        .build();

    // Temp类型的计数缓存
    private final Cache<String, AtomicLong> tempDeltas = CacheBuilder.newBuilder()
        .maximumSize(10)
        .build();

    /**
     * 增加单个文件计数
     */
    public void increase(String fileShardingKey, String type) {
        increase(fileShardingKey, 1L, type);
    }

    /**
     * 增加指定数量的文件计数
     */
    public void increase(String fileShardingKey, Long count, String type) {
        increase0(fileShardingKey, count, type);
    }

    private void increase0(String fileShardingKey, Long count, String type) {
        try {
            Cache<String, AtomicLong> targetCache = getCache(type);
            targetCache.get(fileShardingKey, new Callable<AtomicLong>() {
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
        return FileType.SYSTEM.getType().equals(type) ? systemDeltas : tempDeltas;
    }

    /**
     * 刷新所有类型的计数到数据库
     */
    public void flush() {
        flushByType(FileType.SYSTEM.getType());
        flushByType(FileType.TEMP.getType());
    }

    private void flushByType(String type) {
        Cache<String, AtomicLong> targetCache = getCache(type);
        targetCache.asMap().forEach((k, d) -> {
            long v = d.get();
            if(v > 0) {
                LOGGER.info("update file {} count, sharding: {}, delta: {}", type, k, v);
                repo.increaseFileShardingCount(k, v, type);
                d.addAndGet(-v);
            }
        });
    }

    /**
     * 尝试为所有类型创建新分片
     */
    public void trySharding() {
        trySharding(FileType.SYSTEM.getType());
        trySharding(FileType.TEMP.getType());
    }

    /**
     * 尝试为指定类型创建新分片
     */
    public void trySharding(String type) {
        try {
            FileShardingDB sharding = repo.queryLatestFileSharding(type);
            if(sharding != null && sharding.getCount().longValue() >= sharding.getMaxCount().longValue()) {
                LOGGER.info("creating new file {} sharding, last_key: {}", type, sharding.getKey());
                TaskExecutor.submit(() -> repo.newFileShardingTable(sharding.getKey(), type));
            }
        } catch (Throwable t) {
            LOGGER.error("trySharding error for file type {}, t: ", type, t);
        }
    }
}
