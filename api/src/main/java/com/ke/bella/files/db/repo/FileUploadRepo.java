package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.Tables.FILE_UPLOAD;

import java.time.LocalDateTime;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import com.ke.bella.files.db.tables.pojos.FileUploadDB;
import com.ke.bella.files.utils.BellaContextHelper;

@Component
public class FileUploadRepo implements BaseRepo {
    @Resource
    private DSLContext db;

    public FileUploadDB insert(FileUploadDB row) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = BellaContextHelper.getOperatorUserId();
        row.setCuid(userId == null ? 0L : userId);
        row.setCuName(StringUtils.defaultString(BellaContextHelper.getOperatorUserName()));
        row.setCtime(now);
        row.setMtime(now);
        db.insertInto(FILE_UPLOAD)
                .set(FILE_UPLOAD.UPLOAD_ID, row.getUploadId()).set(FILE_UPLOAD.SPACE_CODE, row.getSpaceCode())
                .set(FILE_UPLOAD.AK_CODE, row.getAkCode()).set(FILE_UPLOAD.FILE_ID, row.getFileId())
                .set(FILE_UPLOAD.FILENAME, row.getFilename()).set(FILE_UPLOAD.EXTENSION, row.getExtension())
                .set(FILE_UPLOAD.PURPOSE, row.getPurpose()).set(FILE_UPLOAD.MIME_TYPE, row.getMimeType())
                .set(FILE_UPLOAD.TYPE, row.getType()).set(FILE_UPLOAD.CHARSET, row.getCharset())
                .set(FILE_UPLOAD.BUCKET, row.getBucket()).set(FILE_UPLOAD.PATH, row.getPath())
                .set(FILE_UPLOAD.DECLARED_BYTES, row.getDeclaredBytes()).set(FILE_UPLOAD.STORAGE_UPLOAD_ID, row.getStorageUploadId())
                .set(FILE_UPLOAD.ANCESTOR_ID, row.getAncestorId()).set(FILE_UPLOAD.METADATA, row.getMetadata())
                .set(FILE_UPLOAD.DESCRIPTION, row.getDescription()).set(FILE_UPLOAD.CITIES, row.getCities())
                .set(FILE_UPLOAD.TAGS, row.getTags()).set(FILE_UPLOAD.STATUS, row.getStatus())
                .set(FILE_UPLOAD.EXPIRES_AT, row.getExpiresAt()).set(FILE_UPLOAD.CUID, row.getCuid())
                .set(FILE_UPLOAD.CU_NAME, row.getCuName()).set(FILE_UPLOAD.CTIME, now).set(FILE_UPLOAD.MTIME, now)
                .execute();
        return queryByUploadId(row.getUploadId(), row.getSpaceCode());
    }

    public FileUploadDB queryByUploadId(String uploadId, String spaceCode) {
        return db.selectFrom(FILE_UPLOAD).where(FILE_UPLOAD.UPLOAD_ID.eq(uploadId).and(FILE_UPLOAD.SPACE_CODE.eq(spaceCode)))
                .fetchOneInto(FileUploadDB.class);
    }

    public FileUploadDB queryByUploadId(String uploadId) {
        return db.selectFrom(FILE_UPLOAD).where(FILE_UPLOAD.UPLOAD_ID.eq(uploadId)).fetchOneInto(FileUploadDB.class);
    }

    public boolean casStatus(String uploadId, String from, String to) {
        return db.update(FILE_UPLOAD).set(FILE_UPLOAD.STATUS, to).set(FILE_UPLOAD.MTIME, LocalDateTime.now())
                .where(FILE_UPLOAD.UPLOAD_ID.eq(uploadId).and(FILE_UPLOAD.STATUS.eq(from))).execute() > 0;
    }

    public void bindFileId(String uploadId, String fileId) {
        db.update(FILE_UPLOAD).set(FILE_UPLOAD.FILE_ID, fileId).set(FILE_UPLOAD.STATUS, "COMPLETED")
                .set(FILE_UPLOAD.MTIME, LocalDateTime.now()).where(FILE_UPLOAD.UPLOAD_ID.eq(uploadId)).execute();
    }
}
