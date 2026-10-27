package com.aurastudio.backend.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.aurastudio.backend.category.CategoryDto;
import com.aurastudio.backend.shoptype.SizeKind.SizeOption;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** Product bodies (docs/API_AND_DATABASE.md §6.5, §8.2–8.5). */
public final class ProductDtos {

	private ProductDtos() {
	}

	public record ColorDto(@Schema(example = "ខ្មៅ") String name, @Schema(example = "#111111") String hex) {
	}

	/** Create / update body (PUT replaces images, sizes, colours and categories). */
	public record ProductRequest(
			@Schema(example = "អាវយឺតកប្បាស") String name,
			@Schema(example = "អាវយឺតកប្បាសសុទ្ធ ទន់ស្រួលពាក់") String description,
			@Schema(example = "25") BigDecimal price,
			@Schema(example = "10") Integer stock,
			@Schema(example = "កប្បាសសុទ្ធ") String badge,
			@Schema(example = "neutral", allowableValues = { "neutral", "gold" }) String badgeTone,
			@Schema(example = "ស្ទូឌីយោមីឡាន") String atelier,
			@Schema(example = "[\"women\"]") List<String> categories,
			@Schema(description = "Order = display order, [0] = main image. https://… or /uploads/{file} of your shop")
			List<String> images,
			@Schema(description = "Only for shop types with a size system", example = "[\"S\",\"M\",\"L\"]") List<String> sizes,
			List<ColorDto> colors) {
	}

	/** Full product (admin detail, storefront list and detail). */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ProductDto(UUID id, String slug, String name, String description, BigDecimal price, int stock,
			String badge, String badgeTone, String atelier, List<String> categories, List<String> images,
			List<String> sizes, List<ColorDto> colors, List<RelatedDto> related) {

		public ProductDto withRelated(List<RelatedDto> related) {
			return new ProductDto(id, slug, name, description, price, stock, badge, badgeTone, atelier, categories,
					images, sizes, colors, related);
		}
	}

	/** "Complete the look" card. */
	public record RelatedDto(String slug, String name, BigDecimal price, String image, String atelier, String badge) {
	}

	/** Admin table row. */
	public record ProductListItem(UUID id, String slug, String name, BigDecimal price, int stock, String badge,
			List<String> categories, String image) {
	}

	/** What the admin form needs: the shop's categories and size system. */
	public record FormOptions(List<CategoryDto> categories, String typeLabel, String sizeKind, List<SizeOption> sizes,
			List<String> defaultSizes) {
	}

	public record ProductWithOptions(ProductDto product, FormOptions options) {
	}
}
