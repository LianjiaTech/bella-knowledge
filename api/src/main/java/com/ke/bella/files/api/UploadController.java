package com.ke.bella.files.api;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ke.bella.files.annotations.FileAPI;
import com.ke.bella.files.protocol.OpenapiListResponse;
import com.ke.bella.files.protocol.Upload;
import com.ke.bella.files.protocol.UploadOps.CompleteUploadOp;
import com.ke.bella.files.protocol.UploadOps.CreateUploadOp;
import com.ke.bella.files.protocol.UploadPart;
import com.ke.bella.files.service.UploadService;
import com.ke.bella.files.service.storage.StoragePart;

@FileAPI
@RestController
@RequestMapping("/v1/uploads")
public class UploadController {
    @Autowired
    private UploadService uploadService;

    @PostMapping
    public Upload create(@RequestBody CreateUploadOp op) {
        return uploadService.create(op);
    }

    @PostMapping("/{upload_id}/parts")
    public UploadPart uploadPart(@PathVariable("upload_id") String uploadId,
            @RequestParam("data") MultipartFile data,
            @RequestParam("part_number") int partNumber) throws IOException {
        return uploadService.uploadPart(uploadId, partNumber, data.getInputStream(), data.getSize());
    }

    @PostMapping("/{upload_id}/complete")
    public Upload complete(@PathVariable("upload_id") String uploadId,
            @RequestBody(required = false) CompleteUploadOp op) {
        return uploadService.complete(uploadId, op);
    }

    @PostMapping("/{upload_id}/cancel")
    public Upload cancel(@PathVariable("upload_id") String uploadId) {
        return uploadService.cancel(uploadId);
    }

    @GetMapping("/{upload_id}/parts")
    public OpenapiListResponse<UploadPart> listParts(@PathVariable("upload_id") String uploadId) {
        List<UploadPart> parts = uploadService.listParts(uploadId).stream().map(this::toUploadPart).collect(Collectors.toList());
        return new OpenapiListResponse<>(parts, "list", null, false);
    }

    private UploadPart toUploadPart(StoragePart part) {
        return UploadPart.builder().partNumber(part.getPartNumber()).size(part.getSize()).etag(part.getEtag()).build();
    }
}
