package com.aurastudio.backend.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.product.ProductDtos.ProductListItem;
import com.aurastudio.backend.product.ProductRepository;
import com.aurastudio.backend.product.ProductService;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Admin home numbers for my shop (docs/API_AND_DATABASE.md §8.1). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class DashboardController {

	private static final int LOW_STOCK = 3;

	private final ProductRepository products;
	private final ProductService productService;
	private final UserRepository users;

	public DashboardController(ProductRepository products, ProductService productService, UserRepository users) {
		this.products = products;
		this.productService = productService;
		this.users = users;
	}

	public record LowStock(UUID id, String name, int stock) {
	}

	public record Dashboard(long totalProducts, long totalStock, BigDecimal stockValue, List<LowStock> lowStock,
			List<ProductListItem> recentProducts, long pendingStaff) {
	}

	@GetMapping
	@Operation(summary = "Totals, low stock (≤ 3), 5 newest products, pending staff (owners)")
	public ApiResponse<Dashboard> get(@CurrentShop ShopContext ctx) {
		var stats = products.stats(ctx.shopId());
		var low = products.lowStock(ctx.shopId(), LOW_STOCK).stream()
				.map(p -> new LowStock(p.id(), p.name(), p.stock())).toList();
		var recent = productService.adminList(ctx, null, null, 1, 5).items();
		long pending = ctx.isOwner() ? users.countPending(ctx.shopId()) : 0;
		return ApiResponse.of(
				new Dashboard(stats.totalProducts(), stats.totalStock(), stats.stockValue(), low, recent, pending));
	}
}
