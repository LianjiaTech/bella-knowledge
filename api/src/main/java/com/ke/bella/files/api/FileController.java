package com.ke.bella.files.api;

import static com.ke.bella.files.service.FileService.ONE_DAY_STRING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.protocol.DomTreeOps.DomTreeUploadOp;
import com.ke.bella.files.protocol.FileCropOps;
import com.ke.bella.files.protocol.FileCropOps.FileCropOp;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileException.ProgressNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileSystemOps.MkdirOp;
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.Scope;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.service.lock.FileUniquenessLock;
import com.ke.bella.files.utils.BellaContextHelper;
import com.ke.bella.files.utils.JsonUtils;
import com.ke.bella.openapi.utils.FileUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;

@FileAPI
@RestController
@RequestMapping("/v1/files")
@Slf4j
public class FileController {

    private static final long FILE_LOCK_TIMEOUT_MS = 30000L;

    private static final int MAX_DIRECTORY_NAME_LENGTH = 255;

    private static final Pattern WINDOWS_INVALID_CHARS = Pattern.compile("[<>:\"|?*\\\\]|[\\x00-\\x1f]");

    private static final Pattern UNIX_INVALID_CHARS = Pattern.compile("[\\x00/]");

    @Autowired
    FileService fileService;
    @Autowired
    FileUniquenessLock fl;
    @Value("${bella.file-api.file.tmp-file-dir}")
    private String tmpFileDir;

    @PostMapping
    public OpenAIFile upload(
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires,
            @RequestParam(value = "ancestor_id", required = false) String ancestorId,
            @RequestParam(value = "overwrite", required = false, defaultValue = "false") boolean overwrite) throws IOException {
        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        String filename = file.getOriginalFilename();

        TmpFileInfo tmpFileInfo = null;
        try {
            tmpFileInfo = createTempFile(file);
            final TmpFileInfo finalTmpFileInfo = tmpFileInfo;

            return fl.executeWithLock(spaceCode, ancestorId, filename, FILE_LOCK_TIMEOUT_MS, () -> {
                if(fileService.exists(spaceCode, ancestorId, filename)) {
                    if(overwrite) {
                        OpenAIFile existingFile = fileService.getFile(spaceCode, ancestorId, filename);
                        if(existingFile != null) {
                            return updateFile(existingFile.getId(), finalTmpFileInfo.getTmpFile(), filename,
                                    finalTmpFileInfo.getType(), finalTmpFileInfo.getMimeType(),
                                    finalTmpFileInfo.getExtension(), finalTmpFileInfo.getCharset(), metadata);
                        }
                    } else {
                        // fixme: may lead to unnecessary create temp file
                        throw new IllegalArgumentException(
                                String.format("File '%s' already exists in current directory, ancestor_id: '%s'", filename, ancestorId));
                    }
                }

                return fileService.uploadWithUrl(finalTmpFileInfo.getTmpFile(), finalTmpFileInfo.getType(), finalTmpFileInfo.getMimeType(),
                        finalTmpFileInfo.getExtension(), finalTmpFileInfo.getCharset(), purpose, metadata, getUrl, expires, ancestorId, filename);
            });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("File upload failed, filename: {}, error: {}", filename, e.getMessage(), e);
            throw new IllegalStateException("File upload failed", e);
        } finally {
            if(tmpFileInfo != null) {
                tmpFileInfo.close();
            }
        }
    }

    private TmpFileInfo createTempFile(MultipartFile file) throws IOException {
        MediaType mimeTypeSource = Optional.ofNullable(file.getContentType()).map(MediaType::parse).orElse(null);
        String type = "";
        String mimeType = "";
        String charset = "";
        if(mimeTypeSource != null) {
            type = FileUtils.getType(mimeTypeSource);
            mimeType = FileUtils.extraPureMediaType(mimeTypeSource);
            charset = Optional.ofNullable(mimeTypeSource.charset()).map(Charset::name).orElse(null);
        }

        String extension = FileUtils.getFileExtension(file.getOriginalFilename());
        String suffix = StringUtils.isEmpty(extension) ? "" : "." + extension;

        File tmpDir = new File(tmpFileDir);
        File tmpFile = File.createTempFile("tmp", suffix, tmpDir);
        file.transferTo(tmpFile);

        return new TmpFileInfo(tmpFile, type, mimeType, extension, charset);
    }

    private TmpFileInfo createTempFileFromObject(Object content, String extension) throws IOException {
        String type = "json";
        String mimeType = "application/json";
        String charset = StandardCharsets.UTF_8.name();

        String suffix = StringUtils.isEmpty(extension) ? "" : "." + extension;

        File tmpDir = new File(tmpFileDir);
        File tmpFile = File.createTempFile("tmp", suffix, tmpDir);

        JsonUtils.writeToFile(content, tmpFile);

        return new TmpFileInfo(tmpFile, type, mimeType, extension, charset);
    }

    private OpenAIFile uploadDomTree(String sourceFileId, String filename, String spaceCode, TmpFileInfo tmpFileInfo) {
        if(fileService.exists(spaceCode, null, filename)) {
            OpenAIFile existingFile = fileService.getFile(spaceCode, null, filename);

            return updateFile(existingFile.getId(), tmpFileInfo.getTmpFile(), filename,
                    "json", "application/json",
                    "json", tmpFileInfo.getCharset(), null);
        } else {
            OpenAIFile uploaded = fileService.upload(tmpFileInfo.getTmpFile(), filename, "dom_tree", null,
                    tmpFileInfo.getMimeType(), tmpFileInfo.getType(), tmpFileInfo.getExtension(),
                    tmpFileInfo.getCharset());

            FileOps bindOp = FileOps.builder()
                    .fileId(sourceFileId)
                    .domTreeFileId(uploaded.getId())
                    .build();

            fileService.updateFile(bindOp, true, Scope.DOM_TREE);

            return uploaded;
        }
    }

    @Data
    @AllArgsConstructor
    private static class TmpFileInfo implements AutoCloseable {
        public final File tmpFile;
        public final String type;
        public final String mimeType;
        public final String extension;
        public final String charset;

        public void close() {
            if(tmpFile != null) {
                boolean deleteSuccess = tmpFile.delete();
                if(!deleteSuccess) {
                    tmpFile.deleteOnExit();
                }
            }
        }
    }

    @GetMapping
    public OpenapiListResponse<OpenAIFile> list(
            @RequestParam(value = "ancestor_id", required = false) String ancestorId,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "limit", required = false, defaultValue = "10000") Integer limit,
            @RequestParam(value = "order", required = false, defaultValue = "desc") String order,
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires) {
        if(limit < 1) {
            throw new IllegalArgumentException(limit + " is less than the minimum of 1");
        }
        if(limit > 10000) {
            throw new IllegalArgumentException(limit + " is greater than the maximum of 10000");
        }
        if(!order.equals("desc") && !order.equals("asc")) {
            throw new IllegalArgumentException(order + "is not one of ['asc', 'desc']");
        }

        // 如果after为空，说明用户没有指定after，此时查询所有数据
        // 如果传了一个无效after，才抛出错误
        if(!StringUtils.isEmpty(after) && fileService.getFile(after) == null) {
            throw new IllegalArgumentException("Invalid after: " + after);
        }

        List<OpenAIFile> files = fileService.list(purpose, limit + 1, order, after, ancestorId);
        OpenapiListResponse<OpenAIFile> res = new OpenapiListResponse<>();
        if(files.size() > limit) {
            files = files.subList(0, limit);
            res.setHasMore(true);
            res.setLastId(files.get(limit - 1).getId());
        } else if(!files.isEmpty()) {
            res.setLastId(files.get(files.size() - 1).getId());
        }

        if(getUrl) {
            for (OpenAIFile file : files) {
                String url = fileService.getUrl(file.getId(), expires);
                file.setUrl(url);
            }
        }

        res.setData(files);
        return res;
    }

    /**
     * 验证文件/目录名称的合法性（常见操作系统限制）
     * 当前校验规则包括：
     * 1. 基础校验：不能为null或空字符串
     * 2. 首尾空格：不能以空格开头或结尾（Windows兼容）
     * 3. 长度限制：最大255字符（大多数文件系统限制）
     * 4. 特殊目录名：禁止完全等于 "." 或 ".."（Unix/Linux路径遍历）
     * 5. 后缀限制：不能以点号或空格结尾（Windows限制）
     * 7. Windows非法字符：< > : " | ? * \ 和控制字符(0x00-0x1F)
     * 8. Unix/Linux非法字符：空字符(0x00)和斜杠(/)
     * 9. 控制字符：除制表符外的所有ISO控制字符（安全考虑）
     * 注意：文件名中间可以包含空格、点号等字符，只是不能在特定位置出现
     *
     * @param name 待验证的文件/目录名称
     *
     * @throws IllegalArgumentException 当名称不符合规范时抛出异常
     */
    private void validateDirectoryName(String name) {
        Assert.notNull(name, "Directory name cannot be null");

        String trimmedName = name.trim();
        Assert.hasText(trimmedName, "Directory name cannot be empty");
        Assert.isTrue(trimmedName.equals(name), "Directory name cannot start or end with whitespace");
        Assert.isTrue(trimmedName.length() <= MAX_DIRECTORY_NAME_LENGTH,
                String.format("Directory name too long: %d characters (max %d)",
                        trimmedName.length(), MAX_DIRECTORY_NAME_LENGTH));

        Assert.isTrue(!".".equals(trimmedName) && !"..".equals(trimmedName),
                "Directory name cannot be '.' or '..'");

        char lastChar = trimmedName.charAt(trimmedName.length() - 1);
        Assert.isTrue(lastChar != '.' && lastChar != ' ',
                "Directory name cannot end with '.' or space");

        Assert.isTrue(!WINDOWS_INVALID_CHARS.matcher(trimmedName).find() &&
                !UNIX_INVALID_CHARS.matcher(trimmedName).find(),
                "Directory name contains invalid characters");

        for (int i = 0; i < trimmedName.length(); i++) {
            char c = trimmedName.charAt(i);
            Assert.isTrue(!Character.isISOControl(c) || c == '\t',
                    String.format("Directory name contains control character at position %d", i));
        }

    }

    @GetMapping("/{file_id}")
    public OpenAIFile get(@PathVariable("file_id") String fileId,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        if(getUrl) {
            String url = fileService.getUrl(file.getId(), expires);
            file.setUrl(url);
        }
        return file;
    }

    @DeleteMapping("/{file_id}")
    public OpenAIFile delete(@PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        fileService.delete(fileId);
        return OpenAIFile.builder()
                .id(fileId)
                .deleted(true)
                .build();
    }

    @PostMapping("/{file_id}/rename")
    public OpenAIFile rename(
            @PathVariable("file_id") String fileId,
            @RequestParam(value = "filename") String filename) {

        if(StringUtils.isEmpty(fileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        if(StringUtils.isEmpty(filename)) {
            throw new IllegalArgumentException("filename is required, but not provided");
        }

        OpenAIFile existingFile = fileService.getFile(fileId);
        if(existingFile == null) {
            throw new FileNotFoundException(fileId);
        }

        if(filename.equals(existingFile.getFilename())) {
            return existingFile;
        }

        String spaceCode = BellaContextHelper.getOperateSpaceCode();
        String ancestorId = fileService.getDirectAncestorId(fileId);

        try {
            return fl.executeWithLock(spaceCode, ancestorId, filename, FILE_LOCK_TIMEOUT_MS, () -> {
                if(fileService.exists(spaceCode, ancestorId, filename)) {
                    String location = ancestorId == null ? "root directory" : "ancestor_id: '" + ancestorId + "'";
                    throw new IllegalArgumentException(
                            String.format("File '%s' already exists in current directory, %s", filename, location));
                }

                FileOps ops = FileOps.builder()
                        .fileId(fileId)
                        .filename(filename)
                        .build();
                return fileService.updateFile(ops, true, Scope.FILENAME);
            });
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("File rename failed, file_id: {}, new_filename: {}, error: {}", fileId, filename, e.getMessage(), e);
            throw new IllegalStateException("File rename failed", e);
        }
    }

    @PutMapping
    public OpenAIFile updateFileContent(
            @RequestPart(value = "file", required = false) MultipartFile file0,
            @RequestParam(value = "file_id") String fileId) {
        if(StringUtils.isEmpty(fileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }
        if(file0 == null) {
            throw new IllegalArgumentException("file is required, but not provided");
        }

        OpenAIFile existingFile = fileService.getFile(fileId);
        if(existingFile == null) {
            throw new FileNotFoundException(fileId);
        }

        TmpFileInfo tmpFileInfo = null;
        try {
            tmpFileInfo = createTempFile(file0);
            return updateFile(fileId, tmpFileInfo.getTmpFile(), existingFile.getFilename(),
                    tmpFileInfo.getType(), tmpFileInfo.getMimeType(),
                    tmpFileInfo.getExtension(), tmpFileInfo.getCharset(), existingFile.getMetadata());

        } catch (Exception e) {
            LOGGER.error("File update failed, file_id: {}, error: {}", fileId, e.getMessage(), e);
            throw new IllegalStateException("File update failed", e);
        } finally {
            if(tmpFileInfo != null) {
                tmpFileInfo.close();
            }
        }
    }

    private OpenAIFile updateFile(String fileId, File file0, String filename, String type, String mimeType, String extension, String charset,
            String metadata) {

        String fileKey = fileService.updateRealFile(fileId, filename, file0, mimeType, charset);

        FileOps ops = FileOps.builder()
                .fileId(fileId)
                .filename(filename)
                .mimeType(mimeType)
                .metadata(metadata)
                .bytes(file0.length())
                .type(type)
                .extension(extension)
                .path(fileKey)
                .build();

        return fileService.updateFile(ops, true, Scope.CONTENT);
    }

    @PostMapping("/dom-tree")
    public OpenAIFile uploadDomTree(
            @RequestParam(value = "file_id") String sourceFileId,
            @RequestPart(value = "file") MultipartFile file) {
        if(StringUtils.isEmpty(sourceFileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        String filename = String.format("dom_tree_%s.json", sourceFileId);
        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        return fl.executeWithLock(spaceCode, null, filename, FILE_LOCK_TIMEOUT_MS, () -> {

            TmpFileInfo tmpFileInfo = null;
            try {

                tmpFileInfo = createTempFile(file);

                return uploadDomTree(sourceFileId, filename, spaceCode, tmpFileInfo);

            } catch (Exception e) {
                LOGGER.error("Dom tree upload failed, file_id: {}, error: {}", sourceFileId, e.getMessage(), e);
                throw new IllegalStateException("Dom tree upload failed", e);
            } finally {
                if(tmpFileInfo != null) {
                    tmpFileInfo.close();
                }
            }
        });
    }

    @PostMapping("/dom-tree/json")
    public OpenAIFile uploadDomTreeJson(@RequestBody DomTreeUploadOp domTreeUploadOp) {
        if(domTreeUploadOp == null) {
            throw new IllegalArgumentException("request body is required, but not provided");
        }
        if(StringUtils.isEmpty(domTreeUploadOp.getFileId())) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }
        if(domTreeUploadOp.getDomTree() == null) {
            throw new IllegalArgumentException("dom_tree_content is required, but not provided");
        }

        String sourceFileId = domTreeUploadOp.getFileId();
        String filename = String.format("dom_tree_%s.json", sourceFileId);
        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        return fl.executeWithLock(spaceCode, null, filename, FILE_LOCK_TIMEOUT_MS, () -> {
            TmpFileInfo tmpFileInfo = null;
            try {
                tmpFileInfo = createTempFileFromObject(domTreeUploadOp.getDomTree(), "json");

                return uploadDomTree(sourceFileId, filename, spaceCode, tmpFileInfo);

            } catch (Exception e) {
                LOGGER.error("Dom tree json upload failed, file_id: {}, error: {}", sourceFileId, e.getMessage(), e);
                throw new IllegalStateException("Dom tree json upload failed", e);
            } finally {
                if(tmpFileInfo != null) {
                    tmpFileInfo.close();
                }
            }
        });
    }

    @GetMapping("/{file_id}/dom-tree/content")
    public void retrieveDomTreeContent(
            HttpServletResponse response,
            @PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }

        String targetFileId;
        if("dom_tree".equals(file.getPurpose())) {
            targetFileId = file.getId();
        } else if(!StringUtils.isEmpty(file.getDomTreeFileId())) {
            targetFileId = file.getDomTreeFileId();
        } else {
            throw new IllegalArgumentException(
                    String.format("the file does not have a legal dom file. file_id = %s", fileId));
        }

        String redirectUrl = fileService.getUrl(targetFileId);
        response.setHeader(HttpHeaders.LOCATION, redirectUrl);
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @GetMapping("/{file_id}/dom-tree/url")
    public FileUrl getDomTreeUrl(
            @PathVariable("file_id") String fileId,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) Long expires) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }

        String targetFileId;
        if("dom_tree".equals(file.getPurpose())) {
            targetFileId = file.getId();
        } else if(!StringUtils.isEmpty(file.getDomTreeFileId())) {
            targetFileId = file.getDomTreeFileId();
        } else {
            throw new IllegalArgumentException(
                    String.format("the file does not have a legal dom file. file_id = %s", fileId));
        }

        String url = fileService.getUrl(targetFileId, expires);
        return FileUrl.builder()
                .url(url)
                .build();
    }

    @PostMapping("/pdf")
    public OpenAIFile uploadPdf(
            @RequestParam(value = "file_id") String sourceFileId,
            @RequestPart(value = "file") MultipartFile file) {
        if(StringUtils.isEmpty(sourceFileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        String filename = String.format("pdf_%s.pdf", sourceFileId);
        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        return fl.executeWithLock(spaceCode, null, filename, FILE_LOCK_TIMEOUT_MS, () -> {
            TmpFileInfo tmpFileInfo = null;

            try {
                tmpFileInfo = createTempFile(file);
                if(fileService.exists(spaceCode, null, filename)) {
                    OpenAIFile existingFile = fileService.getFile(spaceCode, null, filename);

                    return updateFile(existingFile.getId(), tmpFileInfo.getTmpFile(), filename,
                            "pdf", "application/pdf",
                            "pdf", tmpFileInfo.getCharset(), null);
                } else {
                    OpenAIFile uploaded = fileService.upload(tmpFileInfo.getTmpFile(), filename, "pdf", null,
                            tmpFileInfo.getMimeType(), tmpFileInfo.getType(), tmpFileInfo.getExtension(),
                            tmpFileInfo.getCharset());

                    FileOps bindOp = FileOps.builder()
                            .fileId(sourceFileId)
                            .pdfFileId(uploaded.getId())
                            .build();

                    fileService.updateFile(bindOp, true, Scope.PDF);

                    return uploaded;
                }

            } catch (Exception e) {
                LOGGER.error("PDF upload failed, file_id: {}, error: {}", sourceFileId, e.getMessage(), e);
                throw new IllegalStateException("PDF upload failed", e);
            } finally {
                if(tmpFileInfo != null) {
                    tmpFileInfo.close();
                }
            }
        });
    }

    @GetMapping("/{file_id}/content")
    public void retrieveContentRedirect(
            HttpServletResponse response,
            @PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        String redirectUrl = fileService.getUrl(fileId);
        response.setHeader(HttpHeaders.LOCATION, redirectUrl);
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @GetMapping("/{file_id}/url")
    public FileUrl getUrl(
            @PathVariable("file_id") String fileId,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) Long expires) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        Assert.isTrue(!file.getIsDir(), String.format("file is a directory, not a file. file_id = %s", fileId));
        String url = fileService.getUrl(fileId, expires);
        return FileUrl.builder()
                .url(url)
                .build();
    }

    @PostMapping("{file_id}/progress/{progress_name}")
    public Progress updateProgress(
            @RequestBody UpdateProgressRequestData data,
            @PathVariable("file_id") String fileId,
            @PathVariable("progress_name") String progressName) {
        if(fileService.getFile(fileId) == null) {
            throw new IllegalArgumentException("Invalid fileId: " + fileId);
        }
        fileService.updateProgress(data, fileId, progressName);
        return fileService.getProgress(fileId, progressName);
    }

    @GetMapping("/{file_id}/progress")
    public Progress getProgress(
            @PathVariable("file_id") String fileId,
            @RequestParam("progress_name") String progressName) {
        if(fileService.getFile(fileId) == null) {
            throw new IllegalArgumentException("Invalid fileId: " + fileId);
        }
        Progress res = fileService.getProgress(fileId, progressName);
        if(res == null) {
            throw new ProgressNotFoundException(fileId, progressName);
        }
        return res;
    }

    @PostMapping("/list")
    public List<OpenAIFile> getFiles(@RequestBody ListFileOps ops) {
        if(ops == null) {
            throw new IllegalArgumentException("Invalid request body");
        }
        if(CollectionUtils.isEmpty(ops.getFileIds()) || ops.getFileIds().size() > 1000) {
            throw new IllegalArgumentException("the size of file_ids must be between 1 and 1000");
        }

        List<OpenAIFile> files = fileService.getFiles(ops);
        if(ops.isGetUrl()) {
            for (OpenAIFile file : files) {
                String url = fileService.getUrl(file.getId(), ops.getExpires());
                file.setUrl(url);
            }
        }
        return files;
    }

    @GetMapping("/{file_id}/preview_url")
    public FileUrl getPreviewUrl(
            @PathVariable("file_id") String fileId,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) Long expires) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        Assert.isTrue(!file.getIsDir(), String.format("file is a directory, not a file. file_id = %s", fileId));

        String url;
        if(!StringUtils.isEmpty(file.getPdfFileId())) {
            url = fileService.getUrl(file.getPdfFileId(), expires);
            // fixme: doc、docx 历史数据不存在转换回流的pdf，需要刷数后才能下掉
        } else if("doc".equals(file.getExtension()) || "docx".equals(file.getExtension())) {
            url = fileService.getPreviewUrl(file.getId(), expires);
        } else {
            url = fileService.getUrl(fileId, expires);
        }
        return FileUrl
                .builder()
                .url(url)
                .build();
    }

    /**
     * PDF渲染DPI设置
     * 150 DPI: 性能与质量的最佳平衡点，适合大多数文档处理场景
     */
    private static final float PDF_RENDER_DPI = 150f;

    /**
     * 大裁剪区域阈值 (PDF用户单位)
     * 超过此阈值的裁剪区域视为大区域，使用较低DPI以节省内存
     */
    private static final double LARGE_CROP_AREA_THRESHOLD = 50000.0;

    /**
     * 大区域使用的低DPI设置
     */
    private static final float LOW_DPI_FOR_LARGE_AREAS = 100f;

    /**
     * PDF标准用户单位转换比例
     * 根据PDF ISO 32000标准：1用户单位 = 1/72英寸
     */
    private static final float PDF_USER_UNITS_PER_INCH = 72f;

    @PostMapping("/crop-image")
    public FileCropOps.FileCropResponse cropPdf(
            @RequestBody FileCropOp cropRequest) throws IOException {
        // 校验参数
        String fileId = cropRequest.getFileId();
        List<Double> bbox = cropRequest.getBbox();
        Integer pageNumber = cropRequest.getPage();
        Assert.notNull(fileId, "file_id is required");
        Assert.notNull(cropRequest.getBbox(), "bbox is required");
        Assert.isTrue(bbox.size() == 4, "bbox must have exactly 4 values: [x1, y1, x2, y2]");

        // 校验文件存在
        OpenAIFile file = fileService.getFile(fileId);;
        Assert.notNull(file, String.format("file not found. file_id = %s", fileId));
        Assert.isTrue(!file.getIsDir(), String.format("file is a directory, not a file. file_id = %s", fileId));

        // 确认要处理的pdf文件
        String pdfFileId = fileId;
        if(!("application/pdf".equals(file.getMimeType()) || "pdf".equals(file.getType()) || StringUtils.isNotEmpty(file.getPdfFileId()))) {
            throw new IllegalArgumentException(
                    String.format("file is not a PDF or does not have an binding PDF. file_id = %s", fileId));
        } else if("pdf".equals(file.getType())) {
            pdfFileId = fileId;
        } else if(!StringUtils.isEmpty(file.getPdfFileId())) {
            pdfFileId = file.getPdfFileId();
        }

        double x1 = bbox.get(0);
        double y1 = bbox.get(1);
        double x2 = bbox.get(2);
        double y2 = bbox.get(3);

        FileService.InputStreamWithCharset streamWithCharset = fileService.getFileInputStream(pdfFileId);
        BufferedImage pageImage = null;
        BufferedImage croppedImage = null;
        ByteArrayOutputStream baos = null;

        try (InputStream inputStream = streamWithCharset.getInputStream();
                PDDocument document = PDDocument.load(inputStream)) {

            Assert.isTrue(pageNumber >= 1 && pageNumber <= document.getNumberOfPages(), String.format("invalid page number: %d, valid range: 1 ~ %d",
                    pageNumber, document.getNumberOfPages()));

            // 获取PDF页面信息 (转换为0基索引)
            PDPage page = document.getPage(pageNumber - 1);
            PDRectangle mediaBox = page.getMediaBox();
            float pdfPageWidth = mediaBox.getWidth();   // PDF用户单位
            float pdfPageHeight = mediaBox.getHeight(); // PDF用户单位

            // 智能DPI选择：根据裁剪区域大小优化内存使用
            double cropArea = (x2 - x1) * (y2 - y1);
            float selectedDpi = (cropArea > LARGE_CROP_AREA_THRESHOLD) ? LOW_DPI_FOR_LARGE_AREAS : PDF_RENDER_DPI;

            if(cropArea > LARGE_CROP_AREA_THRESHOLD) {
                LOGGER.warn("large crop area detected ({} units²), using reduced DPI {} for memory efficiency",
                        cropArea, selectedDpi);
            }

            // 渲染PDF页面为图片 (使用0基索引)
            PDFRenderer renderer = new PDFRenderer(document);
            pageImage = renderer.renderImageWithDPI(pageNumber - 1, selectedDpi);

            // 计算实际缩放比例 (更权威的方法)
            float actualScaleX = (float) pageImage.getWidth() / pdfPageWidth;
            float actualScaleY = (float) pageImage.getHeight() / pdfPageHeight;

            // 验证缩放比例一致性 (检测异常情况)
            float expectedScale = selectedDpi / PDF_USER_UNITS_PER_INCH;
            if(Math.abs(actualScaleX - expectedScale) > 0.1f || Math.abs(actualScaleY - expectedScale) > 0.1f) {
                LOGGER.warn("pdf scaling inconsistency detected. Expected: {}, Actual X: {}, Y: {}",
                        expectedScale, actualScaleX, actualScaleY);
            }

            // 使用实际缩放比例转换坐标 (避免假设，使用测量值)
            int x = (int) Math.round(x1 * actualScaleX);
            int y = (int) Math.round(y1 * actualScaleY);
            int width = (int) Math.round((x2 - x1) * actualScaleX);
            int height = (int) Math.round((y2 - y1) * actualScaleY);

            // 额外验证：确保坐标在PDF页面范围内
            if(x1 < 0 || y1 < 0 || x2 > pdfPageWidth || y2 > pdfPageHeight) {
                LOGGER.warn("crop coordinates exceed PDF page bounds. Page size: {}x{}, Crop: [{},{},{},{}]",
                        pdfPageWidth, pdfPageHeight, x1, y1, x2, y2);
            }

            // 自动修正超出边界的坐标
            x = Math.max(0, Math.min(x, pageImage.getWidth() - 1));
            y = Math.max(0, Math.min(y, pageImage.getHeight() - 1));
            width = Math.min(width, pageImage.getWidth() - x);
            height = Math.min(height, pageImage.getHeight() - y);

            Assert.isTrue(width > 0 && height > 0,
                    String.format("crop area outside image bounds. Image size: %dx%d, Final crop: x=%d, y=%d, width=%d, height=%d",
                            pageImage.getWidth(), pageImage.getHeight(), x, y, width, height));

            // 裁剪图片
            croppedImage = pageImage.getSubimage(x, y, width, height);

            // 转换为base64
            baos = new ByteArrayOutputStream();
            ImageIO.write(croppedImage, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            // 返回标准Data URI格式
            String dataUri = "data:image/png;base64," + base64Image;
            return new FileCropOps.FileCropResponse(dataUri);

        } finally {
            // 显式释放内存资源
            if(baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    LOGGER.warn("failed to close ByteArrayOutputStream", e);
                }
            }
            if(pageImage != null) {
                pageImage.flush(); // 释放图像内存
            }
            if(croppedImage != null) {
                croppedImage.flush(); // 释放裁剪图像内存
            }
        }
    }

    @PostMapping("/mkdir")
    public OpenAIFile mkdir(@RequestBody MkdirOp op) {
        Assert.notNull(op, "invalid request body");
        Assert.hasText(op.getName(), "name is required");

        validateDirectoryName(op.getName());

        String spaceCode = BellaContextHelper.getOperateSpaceCode();

        return fl.executeWithLock(spaceCode, op.getAncestorId(), op.getName(), FILE_LOCK_TIMEOUT_MS, () -> {
            if(fileService.exists(spaceCode, op.getAncestorId(), op.getName())) {
                throw new IllegalArgumentException(
                        String.format("Directory '%s' already exists in current directory, ancestor_id: '%s'", op.getName(), op.getAncestorId()));
            }

            return fileService.mkdir(op.getName(), op.getAncestorId());
        });
    }

    @GetMapping("/find")
    public OpenapiListResponse<OpenAIFile> find(
            @RequestParam(value = "ancestor_id", required = false) String ancestorId) {
        List<OpenAIFile> files = fileService.findFiles(ancestorId);

        OpenapiListResponse<OpenAIFile> res = new OpenapiListResponse<>();
        res.setData(files);
        res.setHasMore(false);
        if(!files.isEmpty()) {
            res.setLastId(files.get(files.size() - 1).getId());
        }
        return res;
    }

    @GetMapping("/{file_id}/info")
    public OpenAIFile info(@PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        return fileService.info(fileId);
    }
}
