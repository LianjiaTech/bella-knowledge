package com.ke.bella.files.service;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.files.db.repo.Page;
import com.ke.bella.files.db.repo.TagRepo;
import com.ke.bella.files.db.tables.pojos.TagDB;
import com.ke.bella.files.protocol.TagOps;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TagService {

    @Resource
    TagRepo repo;

    public TagDB createTag(TagOps.TagOp op) {
        return repo.addTag(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public TagDB updateTag(TagOps.TagOp op) {
        repo.updateTag(op);
        return repo.getTag(op);
    }

    @Transactional(rollbackFor = Exception.class)
    public TagDB deleteTag(TagOps.TagOp op) {
        repo.deleteTag(op);
        return repo.getTag(op, -1);
    }

    public TagDB getTag(TagOps.TagOp op) {
        return repo.getTag(op);
    }

    public Page<TagDB> pageTag(TagOps.TagPage page) {
        return repo.pageTag(page);
    }

    public List<TagDB> listTag(TagOps.TagPage page) {
        return repo.listTag(page);
    }
}
