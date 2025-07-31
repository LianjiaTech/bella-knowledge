package com.ke.bella.files.api;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.tables.pojos.TagDB;
import com.ke.bella.files.protocol.TagOps;
import com.ke.bella.files.service.TagService;
import com.ke.bella.files.utils.BellaContextHelper;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1/tags")
@Slf4j
public class TagController {

    @Resource
    TagService ts;

    @PostMapping("/create")
    public TagDB create(@RequestBody TagOps.TagOp op) {
        Assert.hasText(op.getName(), "tag name must not be empty");
        TagDB tagDB = ts.getTag(TagOps.TagOp.builder()
                .name(op.getName())
                .build());

        Assert.isNull(tagDB, "tag already exists for name: " + op.getName() +
                ", space_code: " + BellaContextHelper.getOperateSpaceCode());

        return ts.createTag(op);
    }

//    @PostMapping("/update")
//    public TagDB update(@RequestBody TagOps.TagOp op) {
//        // todo
//        return null;
//    }
//
//    @PostMapping("/delete")
//    @Transactional(rollbackFor = Exception.class)
//    public TagDB delete(@RequestBody TagOps.TagOp op) {
//        // todo
//        return null;
//    }

    @PostMapping("/get")
    public TagDB get(@RequestBody TagOps.TagOp op) {
        Assert.hasText(op.getName(), "tag name must not be empty");
        return ts.getTag(op);
    }

    @PostMapping("/page")
    public Page<TagDB> page(@RequestBody TagOps.TagPage page) {
        Assert.isTrue(page.getOrder().equals("desc") || page.getOrder().equals("asc"),
                "order must be 'desc' or 'asc', but got: " + page.getOrder());
        Assert.isTrue(page.getOrderBy().equals("ctime") || page.getOrderBy().equals("mtime") || page.getOrderBy().equals("count"),
                "order_by must be 'ctime', 'mtime' or 'count', but got: " + page.getOrderBy());
        return ts.pageTag(page);
    }

    @PostMapping("/list")
    public List<TagDB> list(@RequestBody TagOps.TagPage page) {
        return ts.listTag(page);
    }
}
