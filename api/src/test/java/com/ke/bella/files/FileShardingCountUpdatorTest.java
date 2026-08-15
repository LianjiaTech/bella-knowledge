package com.ke.bella.files;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.ke.bella.files.db.repo.FileRepo;

public class FileShardingCountUpdatorTest {
    @Test
    public void keepsDeltaWhenDatabaseUpdateMissesTarget() {
        FileRepo repo = mock(FileRepo.class);
        when(repo.increaseFileShardingCount("temp", 1, "temp")).thenReturn(0);
        FileShardingCountUpdator updator = new FileShardingCountUpdator();
        updator.repo = repo;

        updator.increase("temp", "temp");
        updator.flush();
        updator.flush();

        verify(repo, org.mockito.Mockito.times(2)).increaseFileShardingCount("temp", 1, "temp");
    }
}
