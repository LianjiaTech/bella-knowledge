package com.ke.bella.files.db.tables;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import com.ke.bella.files.db.DefaultSchema;
import com.ke.bella.files.db.tables.records.FileUploadRecord;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class FileUpload extends TableImpl<FileUploadRecord> {
    private static final long serialVersionUID = 1L;
    public static final FileUpload FILE_UPLOAD = new FileUpload();

    public final TableField<FileUploadRecord, Long> ID = createField(DSL.name("id"), SQLDataType.BIGINT.nullable(false).identity(true), this, "");
    public final TableField<FileUploadRecord, String> UPLOAD_ID = createField(DSL.name("upload_id"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> SPACE_CODE = createField(DSL.name("space_code"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> AK_CODE = createField(DSL.name("ak_code"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> FILE_ID = createField(DSL.name("file_id"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> FILENAME = createField(DSL.name("filename"), SQLDataType.VARCHAR(512).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> EXTENSION = createField(DSL.name("extension"), SQLDataType.VARCHAR(32).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> PURPOSE = createField(DSL.name("purpose"), SQLDataType.VARCHAR(32).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> MIME_TYPE = createField(DSL.name("mime_type"), SQLDataType.VARCHAR(128).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> TYPE = createField(DSL.name("type"), SQLDataType.VARCHAR(32).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> CHARSET = createField(DSL.name("charset"), SQLDataType.VARCHAR(32).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> BUCKET = createField(DSL.name("bucket"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> PATH = createField(DSL.name("path"), SQLDataType.VARCHAR(512).nullable(false), this, "");
    public final TableField<FileUploadRecord, Long> DECLARED_BYTES = createField(DSL.name("declared_bytes"), SQLDataType.BIGINT.nullable(false), this, "");
    public final TableField<FileUploadRecord, String> STORAGE_UPLOAD_ID = createField(DSL.name("storage_upload_id"), SQLDataType.VARCHAR(256).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> ANCESTOR_ID = createField(DSL.name("ancestor_id"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> METADATA = createField(DSL.name("metadata"), SQLDataType.CLOB, this, "");
    public final TableField<FileUploadRecord, String> DESCRIPTION = createField(DSL.name("description"), SQLDataType.VARCHAR(256).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> CITIES = createField(DSL.name("cities"), SQLDataType.VARCHAR(512).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> TAGS = createField(DSL.name("tags"), SQLDataType.VARCHAR(512).nullable(false), this, "");
    public final TableField<FileUploadRecord, String> STATUS = createField(DSL.name("status"), SQLDataType.VARCHAR(16).nullable(false), this, "");
    public final TableField<FileUploadRecord, LocalDateTime> EXPIRES_AT = createField(DSL.name("expires_at"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");
    public final TableField<FileUploadRecord, Long> CUID = createField(DSL.name("cuid"), SQLDataType.BIGINT.nullable(false), this, "");
    public final TableField<FileUploadRecord, String> CU_NAME = createField(DSL.name("cu_name"), SQLDataType.VARCHAR(64).nullable(false), this, "");
    public final TableField<FileUploadRecord, LocalDateTime> CTIME = createField(DSL.name("ctime"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");
    public final TableField<FileUploadRecord, LocalDateTime> MTIME = createField(DSL.name("mtime"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "");

    private FileUpload(Name alias, Table<FileUploadRecord> aliased) {
        super(alias, null, aliased, null, DSL.comment("分片上传会话表（低频，不分表）"), TableOptions.table());
    }

    public FileUpload() {
        this(DSL.name("file_upload"), null);
    }

    public FileUpload(String alias) {
        this(DSL.name(alias), FILE_UPLOAD);
    }

    @Override
    public Class<FileUploadRecord> getRecordType() {
        return FileUploadRecord.class;
    }

    @Override
    public Schema getSchema() {
        return DefaultSchema.DEFAULT_SCHEMA;
    }

    @Override
    public FileUpload as(String alias) {
        return new FileUpload(DSL.name(alias), this);
    }

    @Override
    public FileUpload as(Name alias) {
        return new FileUpload(alias, this);
    }

    @Override
    public FileUpload rename(String name) {
        return new FileUpload(DSL.name(name), null);
    }

    @Override
    public FileUpload rename(Name name) {
        return new FileUpload(name, null);
    }
}
