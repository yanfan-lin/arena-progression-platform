package com.yanfan.arena.platform.api;

import org.springframework.data.domain.Page;

import java.util.List;

// Represent one API response page with pagination totals
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages)
{
    // Copy the content so that the response is immutable
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(
                source.getContent(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }

}
