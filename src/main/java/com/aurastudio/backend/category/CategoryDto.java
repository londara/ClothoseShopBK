package com.aurastudio.backend.category;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** A product category of one shop. {@code id} stays internal; the API uses {@code code}. */
public record CategoryDto(@JsonIgnore UUID id, String code, String label, int sortOrder) {
}
