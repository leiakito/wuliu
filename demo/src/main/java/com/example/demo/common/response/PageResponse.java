package com.example.demo.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lombok.Data;

@Data
public class PageResponse<T> {

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResponse<T> from(IPage<T> page) {
        PageResponse<T> resp = new PageResponse<>();
        resp.setTotal(page.getTotal());
        resp.setPage(page.getCurrent());
        resp.setSize(page.getSize());
        resp.setRecords(page.getRecords());
        return resp;
    }
}
