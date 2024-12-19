package com.ke.bella.files.api;

import static com.ke.bella.files.configuration.Configs.MAX_SIZE_IN_MB;
import static com.ke.bella.files.service.FileService.ONE_DAY_STRING;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.protocol.FileException.FileNotFoundException;
import com.ke.bella.files.protocol.FileException.FileTooLargeException;
import com.ke.bella.files.protocol.FileException.ProgressNotFoundException;
import com.ke.bella.files.protocol.FileUrl;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Progress;
import com.ke.bella.files.protocol.UpdateProgressRequestData;
import com.ke.bella.files.service.FileService;
import com.ke.bella.files.utils.FileExtensionDetectUtil;

import lombok.extern.slf4j.Slf4j;

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
            @RequestParam(value = "metadata", required = false) String metadata) throws IOException {

        long maxSizeInBytes = MAX_SIZE_IN_MB * 1024 * 1024; // 512MB in bytes
        long fileSize = file.getSize();
        if(fileSize > maxSizeInBytes) {
            throw new FileTooLargeException(fileSize, file.getOriginalFilename());
        }
        File tmpFile = null;
        try {
            String extension = FileExtensionDetectUtil.detectExtension(file.getOriginalFilename());
            String suffix = StringUtils.isEmpty(extension) ? "" : "." + extension;
            File tmpDir = new File(tmpFileDir);
            tmpFile = File.createTempFile("tmp", suffix, tmpDir);
            file.transferTo(tmpFile);
            return fileService.upload(tmpFile, file.getOriginalFilename(), purpose, metadata, extension);
        } finally {
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
            @RequestParam(value = "after", required = false) String after) {
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
        res.setData(files);
        return res;
    }

    @GetMapping("/{file_id}")
    public OpenAIFile get(@PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
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

    @GetMapping("/{file_id}/content")
    public void retrieveContentRedirect(
            HttpServletResponse response,
            @PathVariable("file_id") String fileId) {
        OpenAIFile file = fileService.getFile(fileId);
        if(file == null) {
            throw new FileNotFoundException(fileId);
        }
        FileUrl fileUrl = fileService.getUrl(fileId);
        String redirectUrl = fileUrl.getS3Url();
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
        return fileService.getUrl(fileId, expires);
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
}
