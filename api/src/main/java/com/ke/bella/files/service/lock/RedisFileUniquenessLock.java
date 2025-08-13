package com.ke.bella.files.service.lock;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RedisFileUniquenessLock implements FileUniquenessLock {

    private static final String LOCK_PREFIX = "file-api:file:uniqueness:";

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public boolean tryLock(String spaceCode, String ancestorId, String filename, long timeoutMs) {
        String key = buildLockKey(spaceCode, ancestorId, filename);
        RLock lock = redissonClient.getLock(key);

        try {
            boolean success = lock.tryLock(0, timeoutMs, TimeUnit.MILLISECONDS);
            if(success) {
                LOGGER.debug("Successfully acquired file uniqueness lock: {}, filename: {}", key, filename);
                return true;
            } else {
                LOGGER.debug("Failed to acquire file uniqueness lock: {}, filename: {}", key, filename);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while acquiring file uniqueness lock: {}, filename: {}", key, filename, e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Error acquiring file uniqueness lock: {}, filename: {}", key, filename, e);
            return false;
        }
    }

    @Override
    public void unlock(String spaceCode, String ancestorId, String filename) {
        String key = buildLockKey(spaceCode, ancestorId, filename);
        RLock lock = redissonClient.getLock(key);

        try {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.debug("Successfully released file uniqueness lock: {}, filename: {}", key, filename);
            } else {
                LOGGER.warn("Lock not held by current thread for key: {}, filename: {}", key, filename);
            }
        } catch (Exception e) {
            LOGGER.error("Error releasing file uniqueness lock: {}, filename: {}", key, filename, e);
        }
    }

    @Override
    public <T> T executeWithLock(String spaceCode, String ancestorId, String filename,
            long timeoutMs, Supplier<T> action) {
        String key = buildLockKey(spaceCode, ancestorId, filename);
        RLock lock = redissonClient.getLock(key);

        try {
            boolean lockAcquired = lock.tryLock(0, timeoutMs, TimeUnit.MILLISECONDS);
            if(!lockAcquired) {
                LOGGER.warn("File operation conflict detected for file uniqueness lock: {}", key);
                throw new IllegalStateException(
                        String.format("File '%s' is being operated by another user, please try again later", filename));
            }

            LOGGER.debug("Successfully acquired lock for executeWithLock: {}, filename: {}", key, filename);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while acquiring lock for executeWithLock: {}, filename: {}", key, filename, e);
            throw new IllegalStateException("Lock acquisition interrupted", e);
        } catch (Exception e) {
            LOGGER.error("Error in executeWithLock for key: {}, filename: {}", key, filename, e);
            throw e;
        } finally {
            if(lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.debug("Released lock in executeWithLock finally block: {}, filename: {}", key, filename);
            }
        }
    }

    private String buildLockKey(String spaceCode, String ancestorId, String filename) {
        try {
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.name());
            return LOCK_PREFIX + spaceCode + ":" + ancestorId + ":" + encodedFilename;
        } catch (Exception e) {
            LOGGER.error("Error encoding lock key parameters", e);
            throw new IllegalStateException("Failed to build lock key", e);
        }
    }
}
