package com.aurastudio.backend.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Pagination info returned in {@code meta}; {@code categoryCounts} only on storefront lists. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageMeta(int page, int pageSize, long total, int pageCount, Map<String, Long> categoryCounts) {

	public static PageMeta of(int page, int pageSize, long total) {
		return new PageMeta(page, pageSize, total, pageCount(total, pageSize), null);
	}

	public PageMeta withCategoryCounts(Map<String, Long> counts) {
		return new PageMeta(page, pageSize, total, pageCount, counts);
	}

	public static int pageCount(long total, int pageSize) {
		return (int) Math.max(1, (total + pageSize - 1) / pageSize);
	}
}
