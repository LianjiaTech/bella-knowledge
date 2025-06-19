package com.ke.bella.files.db.repo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class Page<T> {
    private int page;
    private int pageSize;
    private int total;
    private List<T> data;

    public static <T> Page<T> from(int page, int pageSize) {
        return new Page<T>().page(page).pageSize(pageSize);
    }

    public Page<T> page(Integer page) {
        this.page = page;
        return this;
    }

    public Page<T> pageSize(Integer pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public Page<T> total(Integer total) {
        this.total = total;
        return this;
    }

    public Page<T> list(List<T> list) {
        this.data = list;
        return this;
    }

    @JsonProperty("has_more")
    public boolean hasMore() {
        return page * pageSize < total;
    }

    @JsonProperty("limit")
    public int pageSize() {
        return pageSize;
    }
}
