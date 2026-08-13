package com.ke.bella.files.db.tables.records;

import java.time.LocalDateTime;

import org.jooq.impl.UpdatableRecordImpl;

import com.ke.bella.files.db.tables.FileUpload;

@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class FileUploadRecord extends UpdatableRecordImpl<FileUploadRecord> {
    private static final long serialVersionUID = 1L;

    public FileUploadRecord() {
        super(FileUpload.FILE_UPLOAD);
    }

    public Long getId() { return (Long) get(0); }
    public void setId(Long value) { set(0, value); }
    public String getUploadId() { return (String) get(1); }
    public void setUploadId(String value) { set(1, value); }
    public String getSpaceCode() { return (String) get(2); }
    public void setSpaceCode(String value) { set(2, value); }
    public String getAkCode() { return (String) get(3); }
    public void setAkCode(String value) { set(3, value); }
    public String getFileId() { return (String) get(4); }
    public void setFileId(String value) { set(4, value); }
    public String getFilename() { return (String) get(5); }
    public void setFilename(String value) { set(5, value); }
    public String getExtension() { return (String) get(6); }
    public void setExtension(String value) { set(6, value); }
    public String getPurpose() { return (String) get(7); }
    public void setPurpose(String value) { set(7, value); }
    public String getMimeType() { return (String) get(8); }
    public void setMimeType(String value) { set(8, value); }
    public String getType() { return (String) get(9); }
    public void setType(String value) { set(9, value); }
    public String getCharset() { return (String) get(10); }
    public void setCharset(String value) { set(10, value); }
    public String getBucket() { return (String) get(11); }
    public void setBucket(String value) { set(11, value); }
    public String getPath() { return (String) get(12); }
    public void setPath(String value) { set(12, value); }
    public Long getDeclaredBytes() { return (Long) get(13); }
    public void setDeclaredBytes(Long value) { set(13, value); }
    public String getStorageUploadId() { return (String) get(14); }
    public void setStorageUploadId(String value) { set(14, value); }
    public String getAncestorId() { return (String) get(15); }
    public void setAncestorId(String value) { set(15, value); }
    public String getMetadata() { return (String) get(16); }
    public void setMetadata(String value) { set(16, value); }
    public String getDescription() { return (String) get(17); }
    public void setDescription(String value) { set(17, value); }
    public String getCities() { return (String) get(18); }
    public void setCities(String value) { set(18, value); }
    public String getTags() { return (String) get(19); }
    public void setTags(String value) { set(19, value); }
    public String getStatus() { return (String) get(20); }
    public void setStatus(String value) { set(20, value); }
    public LocalDateTime getExpiresAt() { return (LocalDateTime) get(21); }
    public void setExpiresAt(LocalDateTime value) { set(21, value); }
    public Long getCuid() { return (Long) get(22); }
    public void setCuid(Long value) { set(22, value); }
    public String getCuName() { return (String) get(23); }
    public void setCuName(String value) { set(23, value); }
    public LocalDateTime getCtime() { return (LocalDateTime) get(24); }
    public void setCtime(LocalDateTime value) { set(24, value); }
    public LocalDateTime getMtime() { return (LocalDateTime) get(25); }
    public void setMtime(LocalDateTime value) { set(25, value); }
}
