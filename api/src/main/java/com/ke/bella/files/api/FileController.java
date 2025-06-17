package com.ke.bella.files.api;

import static com.ke.bella.files.service.FileService.ONE_DAY_STRING;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
        File tmpFile = null;
        try {
            MediaType mimeTypeSource = Optional.ofNullable(file.getContentType()).map(MediaType::parse).orElse(null);
            String type = "";
            String mimeType = "";
            if(mimeTypeSource != null) {
                type = FileUtils.getType(mimeTypeSource);
                mimeType = FileUtils.extraPureMediaType(mimeTypeSource);
            }

            String extension = FileUtils.getFileExtension(file.getOriginalFilename());
            String suffix = StringUtils.isEmpty(extension) ? "" : "." + extension;

            File tmpDir = new File(tmpFileDir);
            tmpFile = File.createTempFile("tmp", suffix, tmpDir);
            file.transferTo(tmpFile);
            OpenAIFile openaiFile = fileService.upload(tmpFile, file.getOriginalFilename(), purpose, metadata, mimeType, type, extension);

            if(getUrl) {
                String url = fileService.getUrl(openaiFile.getId(), expires);
                openaiFile.setUrl(url);
            }

            return openaiFile;
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
    public OpenAIFile updateFile(@RequestBody FileOps ops) {
        if(ops == null || StringUtils.isEmpty(ops.getFileId())) {
            throw new IllegalArgumentException("file_id is required, but not provided");
        }
        OpenAIFile file = fileService.getFile(ops.getFileId());
        if(file == null) {
            throw new FileNotFoundException(ops.getFileId());
        }
        return fileService.updateFile(ops);
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
        String url = fileService.getPreviewUrl(fileId, expires);
        return FileUrl
                .builder()
                .url(url)
                .build();
    }
}
