package com.aurastudio.backend.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.product.ProductDtos.FormOptions;
import com.aurastudio.backend.product.ProductDtos.ProductDto;
import com.aurastudio.backend.product.ProductDtos.ProductListItem;
import com.aurastudio.backend.product.ProductDtos.ProductRequest;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Products of the signed-in user's shop (staff, owner, platform admin). */
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminProductController {

	private final ProductService products;

	public AdminProductController(ProductService products) {
		this.products = products;
	}

	@GetMapping
	@Operation(summary = "Product table of my shop", description = "q searches name and slug; 10 per page by default")
	public ApiResponse<List<ProductListItem>> list(@CurrentShop ShopContext ctx,
			@RequestParam(required = false) String q, @RequestParam(required = false) String category,
			@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
		var result = products.adminList(ctx, q, category, page, pageSize);
		return ApiResponse.page(result.items(), result.meta());
	}

	/** {@code { "data": product, "options": { categories, sizeKind, sizes, defaultSizes } }} (§8.3). */
	public record ProductDetailResponse(ProductDto data, FormOptions options) {
	}

	@GetMapping("/{id}")
	@Operation(summary = "One product of my shop + form options (categories, size system)")
	public ProductDetailResponse get(@CurrentShop ShopContext ctx, @PathVariable UUID id) {
		var result = products.adminGet(ctx, id);
		return new ProductDetailResponse(result.product(), result.options());
	}

	@GetMapping("/options")
	@Operation(summary = "Form options for a NEW product (categories + size system of my shop)")
	public ApiResponse<FormOptions> options(@CurrentShop ShopContext ctx) {
		return ApiResponse.of(products.formOptions(ctx));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a product in my shop")
	public ApiResponse<ProductDto> create(@CurrentShop ShopContext ctx, @RequestBody ProductRequest request) {
		return ApiResponse.of(products.create(ctx, request), Messages.PRODUCT_CREATED);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a product of my shop (404 if it belongs to another shop)")
	public ApiResponse<ProductDto> update(@CurrentShop ShopContext ctx, @PathVariable UUID id,
			@RequestBody ProductRequest request) {
		return ApiResponse.of(products.update(ctx, id, request), Messages.PRODUCT_UPDATED);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a product of my shop")
	public ResponseEntity<Void> delete(@CurrentShop ShopContext ctx, @PathVariable UUID id) {
		products.delete(ctx, id);
		return ResponseEntity.noContent().build();
	}
}
