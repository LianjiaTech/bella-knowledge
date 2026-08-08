package com.ke.bella.files.service.lock;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

@RunWith(MockitoJUnitRunner.class)
public class RedisFileUniquenessLockTest {
    private static final long TIMEOUT_MS = 30000L;

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RReadWriteLock readWriteLock;
    @Mock
    private RLock readLock;
    @Mock
    private RLock writeLock;
    @Mock
    private RLock uniquenessLock;
    @InjectMocks
    private RedisFileUniquenessLock fileLock;

    @Before
    public void setup() {
        when(redissonClient.getReadWriteLock("file-api:file:move:sp-a")).thenReturn(readWriteLock);
        when(readWriteLock.readLock()).thenReturn(readLock);
        when(readWriteLock.writeLock()).thenReturn(writeLock);
    }

    @Test
    public void directoryMoveUsesSpaceWriteLock() throws Exception {
        when(writeLock.tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(writeLock.isHeldByCurrentThread()).thenReturn(true);

        String result = fileLock.executeWithMoveLock("sp-a", true, TIMEOUT_MS, () -> "moved");

        assertEquals("moved", result);
        verify(writeLock).tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        verify(writeLock).unlock();
        verify(readLock, never()).tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void fileMoveUsesSpaceReadLock() throws Exception {
        when(readLock.tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(readLock.isHeldByCurrentThread()).thenReturn(true);

        String result = fileLock.executeWithMoveLock("sp-a", false, TIMEOUT_MS, () -> "moved");

        assertEquals("moved", result);
        verify(readLock).tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        verify(readLock).unlock();
        verify(writeLock, never()).tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    @Test
    public void rootAncestorVariantsUseSameUniquenessLockKey() throws Exception {
        String key = "file-api:file:uniqueness:sp-a:null:name.txt";
        when(redissonClient.getLock(key)).thenReturn(uniquenessLock);
        when(uniquenessLock.tryLock(0, TIMEOUT_MS, TimeUnit.MILLISECONDS)).thenReturn(true);

        fileLock.tryLock("sp-a", null, "name.txt", TIMEOUT_MS);
        fileLock.tryLock("sp-a", "", "name.txt", TIMEOUT_MS);
        fileLock.tryLock("sp-a", "  ", "name.txt", TIMEOUT_MS);

        verify(redissonClient, times(3)).getLock(key);
        verify(uniquenessLock, times(3)).tryLock(0, TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
