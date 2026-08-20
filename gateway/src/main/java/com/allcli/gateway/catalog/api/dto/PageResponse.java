package com.allcli.gateway.catalog.api.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 分页信封（目录列表用）。 */
@Getter
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private int page;

    private int pageSize;

    private long total;

    private List<T> items;

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> p) {
        return PageResponse.<T>builder()
                .page(p.getNumber())
                .pageSize(p.getSize())
                .total(p.getTotalElements())
                .items(p.getContent())
                .build();
    }
}
