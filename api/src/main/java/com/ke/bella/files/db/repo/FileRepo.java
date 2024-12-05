package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE;
import static com.ke.bella.files.db.Tables.FILE_MAPPING;

import java.time.ZoneId;

import javax.annotation.Resource;

import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import com.ke.bella.files.db.tables.pojos.FileDB;
import com.ke.bella.files.db.tables.records.FileRecord;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.utils.StringUtils;

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
        Integer hashCode = StringUtils.hashCode(spaceCode);
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
        return db.select(FILE_MAPPING.FILE_ID)
                .from(FILE_MAPPING)
                .where(FILE_MAPPING.FILE_ID_OLD.eq(fileId))
                .fetchOneInto(String.class);
    }

    private OpenAIFile transferToOpenAIFile(FileDB fileDB) {
        OpenAIFile file = new OpenAIFile();
        file.setId(fileDB.getFileId());
        file.setBytes(fileDB.getBytes());
        file.setCreateAt(fileDB.getCtime()
                .toInstant(ZoneId.systemDefault().getRules().getOffset(fileDB.getCtime()))
                .toEpochMilli());
        file.setFilename(fileDB.getFilename());
        file.setPurpose(fileDB.getPurpose());
        return file;
    }

    public FileDB queryFile(String fileId) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        return db(shardingKey).selectFrom(FILE)
                .where(FILE.FILE_ID.eq(fileId).and(FILE.STATUS.eq(0)))
                .fetchOneInto(FileDB.class);
    }

    public OpenAIFile queryOpenAIFile(String fileId) {
        FileDB fileDB = queryFile(fileId);
        return fileDB == null ? null : transferToOpenAIFile(fileDB);
    }

    public void addFile(FileDB fileDB) {
        String shardingKey = getShardingKeyBySpaceCode(fileDB.getSpaceCode());
        FileRecord rec = FILE.newRecord();
        rec.from(fileDB);
        fillCreatorInfo(rec);
        Integer insertedNum = db(shardingKey).insertInto(FILE)
                .set(rec)
                .execute();
        if(insertedNum != 1) {
            throw new IllegalStateException("insert file failed, fileId: " + fileDB.getFileId());
        }
    }

    public void updateFileStatus(String fileId, Long status) {
        fileId = queryNewFileId(fileId);
        String shardingKey = getShardingKeyByFileId(fileId);
        FileRecord rec = FILE.newRecord();
        rec.setFileId(fileId);
        rec.setBroadcastStatus(status);
        fillUpdatorInfo(rec);
        Integer updatedNum = db(shardingKey).update(FILE)
                .set(rec)
                .where(FILE.FILE_ID.eq(fileId))
                .execute();
        if(updatedNum != 1) {
            throw new IllegalStateException("update file status failed, fileId: " + fileId);
        }
    }
}
