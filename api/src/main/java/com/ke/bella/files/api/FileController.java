package com.ke.bella.files.api;

import static com.ke.bella.files.service.FileService.ONE_DAY_STRING;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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
import com.ke.bella.files.protocol.FileCropOps;
import com.ke.bella.files.protocol.FileCropOps.FileCropOp;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileException.ProgressNotFoundException;
import com.ke.bella.files.protocol.FileOps;
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.ListFileOps;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.FileService;
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

    @Autowired
    FileService fileService;
    @Value("${bella.file-api.file.tmp-file-dir}")
    private String tmpFileDir;

    @PostMapping
    public OpenAIFile upload(
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires) throws IOException {
        TmpFileInfo tmpFileInfo = null;
        try {
            tmpFileInfo = createTempFile(file);

            OpenAIFile openaiFile = fileService.upload(tmpFileInfo.getTmpFile(), file.getOriginalFilename(), purpose, metadata,
                    tmpFileInfo.getMimeType(), tmpFileInfo.getType(), tmpFileInfo.getExtension(), tmpFileInfo.getCharset());

            if(getUrl) {
                String url = fileService.getUrl(openaiFile.getId(), expires);
                openaiFile.setUrl(url);
            }

            return openaiFile;
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

        List<OpenAIFile> files = fileService.list(purpose, limit + 1, order, after);
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

    @PutMapping
    public OpenAIFile updateFile(
            @RequestPart(value = "file") MultipartFile file0,
            @RequestParam(value = "file_id") String fileId) throws IOException {
        if(StringUtils.isEmpty(fileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }

        TmpFileInfo tmpFileInfo = null;
        try {
            tmpFileInfo = createTempFile(file0);

            String fileKey = fileService.updateRealFile(fileId, file0.getOriginalFilename(), tmpFileInfo.getTmpFile(), tmpFileInfo.getMimeType(),
                    tmpFileInfo.getCharset());

            FileOps ops = FileOps.builder()
                    .fileId(fileId)
                    .filename(file0.getOriginalFilename())
                    .mimeType(tmpFileInfo.getMimeType())
                    .type(tmpFileInfo.getType())
                    .extension(tmpFileInfo.getExtension())
                    .path(fileKey)
                    .build();

            return fileService.updateFile(ops, true);
        } finally {
            if(tmpFileInfo != null) {
                tmpFileInfo.close();
            }
        }
    }

    @PostMapping("/dom-tree")
    public OpenAIFile uploadDomTree(
            @RequestParam(value = "file_id") String fileId,
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires) throws IOException {
        if(StringUtils.isEmpty(fileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        OpenAIFile uploaded = upload(file, "dom_tree", metadata, getUrl, expires);

        FileOps bindOp = FileOps.builder()
                .fileId(fileId)
                .domTreeFileId(uploaded.getId())
                .build();

        fileService.updateFile(bindOp);

        return uploaded;
    }

    @GetMapping("/{file_id}/dom-tree/content")
    public void retrieveDomTreeContent(
            HttpServletResponse response,
            @PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        if("dom_tree".equals(file.getPurpose())) {
            retrieveContentRedirect(response, file.getId());
        } else if(!StringUtils.isEmpty(file.getDomTreeFileId())) {
            retrieveContentRedirect(response, file.getDomTreeFileId());
        } else {
            throw new IllegalArgumentException(
                    String.format("the file does not have a legal dom file. file_id = %s", fileId));
        }
    }

    @GetMapping("/{file_id}/dom-tree/url")
    public FileUrl getDomTreeUrl(
            @PathVariable("file_id") String fileId,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) Long expires) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        if("dom_tree".equals(file.getPurpose())) {
            return getUrl(fileId, expires);
        } else if(!StringUtils.isEmpty(file.getDomTreeFileId())) {
            return getUrl(file.getDomTreeFileId(), expires);
        } else {
            throw new IllegalArgumentException(
                    String.format("the file does not have a legal dom file. file_id = %s", fileId));
        }
    }

    @PostMapping("/pdf")
    public OpenAIFile uploadPdf(
            @RequestParam(value = "file_id") String fileId,
            @RequestPart(value = "file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestParam(value = "get_url", required = false, defaultValue = "false") boolean getUrl,
            @RequestParam(value = "expires", required = false, defaultValue = ONE_DAY_STRING) long expires) throws IOException {
        if(StringUtils.isEmpty(fileId)) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }

        OpenAIFile uploaded = upload(file, "pdf", metadata, getUrl, expires);

        FileOps bindOp = FileOps.builder()
                .fileId(fileId)
                .pdfFileId(uploaded.getId())
                .build();

        fileService.updateFile(bindOp);

        return uploaded;
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
}
