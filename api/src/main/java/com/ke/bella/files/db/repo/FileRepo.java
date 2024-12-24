package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;
import static com.ke.bella.files.db.Tables.FILE_PROGRESS;

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.pojos.FileProgressDB;
import com.ke.bella.files.db.tables.records.FileProgressRecord;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileStatus;
import com.ke.bella.files.utils.CustomStringUtils;

@Component
public class FileRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    public FileRepo(DSLContext db) {
        this.db = db;
    }

    private static String getShardingKeyByFileId(String fileId) {
        String[] parts = fileId.split("-");
        if(parts.length != 3) {
            throw new FileNotFoundException(fileId);
        }
        Integer spaceCodeHash = Integer.valueOf(parts[parts.length - 1]);
        return getShardingKeyBySpaceCodeHash(spaceCodeHash);
    }

    private static String getShardingKeyBySpaceCode(String spaceCode) {
        Integer hashCode = CustomStringUtils.hashCode(spaceCode);
        return getShardingKeyBySpaceCodeHash(hashCode);
    }

    private static String getShardingKeyBySpaceCodeHash(Integer spaceCodeHash) {
        spaceCodeHash = Math.abs(spaceCodeHash);
        return Integer.toString(spaceCodeHash % 16);
    }

    private DSLContext db(String shardingKey) {
        return DSLContextHolder.get(shardingKey, db);
    }

    private String queryNewFileId(String fileId) {
        if(fileId.startsWith("file-")) {
            return fileId;
        }
        String newFileId = db.select(FILE_MAPPING.FILE_ID)
                .from(FILE_MAPPING)
                .where(FILE_MAPPING.FILE_ID_OLD.eq(fileId))
                .fetchOneInto(String.class);
        if(newFileId == null) {
            throw new FileNotFoundException(fileId);
        }
        return newFileId;
    }

    public FileDB queryFile(String fileId) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .fetchOneInto(FileDB.class);
    }

    public void addFile(FileDB fileDB) {
        String shardingKey = getShardingKeyBySpaceCode(fileDB.getSpaceCode());
        FileRecord rec = FILE.newRecord();
        rec.from(fileDB);
        fillCreatorInfo(rec);
        int insertedNum = db(shardingKey).insertInto(FILE)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert file failed, fileId: " + fileDB.getFileId());
        }
    }

    public void updateFile(FileOps op) {
        String fileId = queryNewFileId(op.getFileId());
        String shardingKey = getShardingKeyByFileId(fileId);
        FileRecord rec = FILE.newRecord();
        rec.setFileId(fileId);
        if(op.getStatus() != null) {
            rec.setStatus(op.getStatus().getValue());
        }
        if(op.getBroadcastStatus() != null) {
            rec.setBroadcastStatus(op.getBroadcastStatus().getValue());
        }
        fillUpdatorInfo(rec);
        int updatedNum = db(shardingKey).update(FILE)
                .set(rec)
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue())))
                .execute();
        if(updatedNum != 1) {
            throw new IllegalStateException("update file failed, fileId: " + fileId);
        }
    }

    public List<FileDB> listFile(
            String purpose,
            Integer limit,
            String order,
            String after,
            String spaceCode) {
        String shardingKey = getShardingKeyBySpaceCode(spaceCode);
        Condition condition = DSL.trueCondition()
                .and(FILE.SPACE_CODE.eq(spaceCode))
                .and(FILE.STATUS.eq(FileStatus.NOT_DELETED.getValue()));
        // 如果purpose为空则查询所有文件
        if(StringUtils.isNotEmpty(purpose)) {
            condition = condition.and(FILE.PURPOSE.eq(purpose));
        }
        if(StringUtils.isNotEmpty(after)) {
            after = queryNewFileId(after);
            LocalDateTime afterCtime = db(shardingKey).select(FILE.CTIME)
                    .from(FILE)
                    .where(FILE.FILE_ID.eq(after))
                    .fetchOneInto(LocalDateTime.class);
            condition = condition.and("asc".equalsIgnoreCase(order) ? FILE.CTIME.gt(afterCtime) : FILE.CTIME.lt(afterCtime));
        }
        return db(shardingKey).selectFrom(FILE)
                .where(condition)
                .orderBy("asc".equalsIgnoreCase(order) ? FILE.CTIME.asc() : FILE.CTIME.desc())
                .limit(limit)
                .fetchInto(FileDB.class);
    }

    public FileProgressDB queryProgress(
            String fileId,
            String progressName) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        return db(shardingKey).selectFrom(FILE_PROGRESS)
                .where(FILE_PROGRESS.FILE_ID.eq(fileId).and(FILE_PROGRESS.NAME.eq(progressName)))
                .fetchOneInto(FileProgressDB.class);
    }

    public void insertProgress(
            String fileId,
            String progressName,
            String status,
            String message,
            Integer percent) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        FileProgressRecord rec = FILE_PROGRESS.newRecord();
        rec.setFileId(fileId);
        rec.setName(progressName);
        rec.setStatus(status);
        rec.setMessage(message);
        rec.setPercent(percent);
        fillCreatorInfo(rec);
        int insertedNum = db(shardingKey).insertInto(FILE_PROGRESS)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert progress failed, fileId: " + fileId + ", progressName: " + progressName);
        }

    }

    public void updateProgress(
            String fileId,
            String progressName,
            String status,
            String message,
            Integer percent) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        FileProgressRecord rec = FILE_PROGRESS.newRecord();
        rec.setStatus(status);
        rec.setMessage(message);
        rec.setPercent(percent);
        fillUpdatorInfo(rec);
        int updatedNum = db(shardingKey).update(FILE_PROGRESS)
                .set(rec)
                .where(FILE_PROGRESS.FILE_ID.eq(fileId).and(FILE_PROGRESS.NAME.eq(progressName)))
                .execute();
        if(updatedNum != 1) {
            throw new IllegalStateException("update progress failed, fileId: " + fileId + ", progressName: " + progressName);
        }
    }
}
