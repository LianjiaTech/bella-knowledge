package com.ke.bella.files.service.lock;

import java.util.function.Supplier;

public interface FileUniquenessLock {

    boolean tryLock(String spaceCode, String ancestorId, String filename, long timeoutMs);

    void unlock(String spaceCode, String ancestorId, String filename);

    <T> T executeWithLock(String spaceCode, String ancestorId, String filename,
            long timeoutMs, Supplier<T> action);
}
