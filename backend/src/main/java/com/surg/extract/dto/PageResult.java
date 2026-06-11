package com.surg.extract.dto;

import lombok.Data;

@Data
public class PageResult<T> {

    private List<T> records;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;

    public PageResult() {}

    public PageResult(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }
}
