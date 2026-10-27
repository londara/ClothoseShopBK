package com.aurastudio.backend.shop;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurastudio.backend.shoptype.SizeKind;

/** A shop joined with its type and name (most screens need all three). */
public record ShopRow(UUID id, String slug, ShopStatus status, String name, String typeCode, String typeLabel,
		SizeKind sizeKind, OffsetDateTime createdAt, OffsetDateTime approvedAt) {
}
