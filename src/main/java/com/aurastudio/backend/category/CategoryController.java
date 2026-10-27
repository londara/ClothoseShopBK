package com.aurastudio.backend.category;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Product categories of my shop (docs/API_AND_DATABASE.md §8.7–8.8). */
@RestController
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class CategoryController {

	private static final int MAX_CATEGORIES = 20;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final CategoryRepository categories;
	private final CacheEvictions evictions;

	public CategoryController(CategoryRepository categories, CacheEvictions evictions) {
		this.categories = categories;
		this.evictions = evictions;
	}

	public record NewCategory(@Schema(example = "អាវយឺត") String label) {
	}

	@GetMapping
	@Operation(summary = "Categories of my shop, in display order")
	public ApiResponse<List<CategoryDto>> list(@CurrentShop ShopContext ctx) {
		return ApiResponse.of(categories.list(ctx.shopId()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	@Operation(summary = "Add a category (\"បន្ថែមប្រភេទថ្មី\")",
			description = "Inserted just before \"លក់ដាច់បំផុត\" (best sellers). Duplicate label → 409; max 20 per shop.")
	public ApiResponse<CategoryDto> add(@CurrentShop ShopContext ctx, @RequestBody NewCategory body) {
		String label = Validators.text(body == null ? null : body.label(), "label", 40);
		if (label.isEmpty()) {
			throw ApiException.validation("label", Messages.CATEGORY_NAME_REQUIRED);
		}
		List<CategoryDto> current = categories.list(ctx.shopId());
		String finalLabel = label;
		if (current.stream().anyMatch(c -> c.label().equals(finalLabel))) {
			throw ApiException.conflict("label", Messages.CATEGORY_TAKEN);
		}
		if (current.size() >= MAX_CATEGORIES) {
			throw ApiException.validation("label", Messages.CATEGORIES_TOO_MANY);
		}
		int sort = current.stream().filter(c -> !CategoryRepository.BEST_SELLERS.equals(c.code()))
				.mapToInt(CategoryDto::sortOrder).max().orElse(0) + 1;
		byte[] bytes = new byte[3];
		RANDOM.nextBytes(bytes);
		String code = "c-" + HexFormat.of().formatHex(bytes);
		categories.insert(ctx.shopId(), code, label, sort);
		categories.moveBestSellersLast(ctx.shopId());
		evictions.shopChanged(ctx.shopId());
		var created = categories.list(ctx.shopId()).stream().filter(c -> c.code().equals(code)).findFirst()
				.orElseThrow();
		return ApiResponse.of(created, Messages.categoryAdded(label));
	}
}
