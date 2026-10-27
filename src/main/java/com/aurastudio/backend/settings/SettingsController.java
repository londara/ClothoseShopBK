package com.aurastudio.backend.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurastudio.backend.cache.CacheEvictions;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ApiResponse;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.OpenApiConfig;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopSettings;
import com.aurastudio.backend.tenant.CurrentShop;
import com.aurastudio.backend.tenant.ShopContext;
import com.aurastudio.backend.upload.UploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Storefront settings of my shop — owners only (docs/API_AND_DATABASE.md §8.9–8.10). */
@RestController
@RequestMapping("/api/v1/admin/settings")
@Tag(name = "4. Shop admin")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class SettingsController {

	private final ShopRepository shops;
	private final UploadService uploads;
	private final CacheEvictions evictions;

	public SettingsController(ShopRepository shops, UploadService uploads, CacheEvictions evictions) {
		this.shops = shops;
		this.uploads = uploads;
		this.evictions = evictions;
	}

	@GetMapping
	@Operation(summary = "Settings of my shop (owner)")
	public ApiResponse<ShopSettings> get(@CurrentShop ShopContext ctx) {
		ctx.requireOwner();
		return ApiResponse.of(shops.getSettings(ctx.shopId()));
	}

	@PutMapping
	@Operation(summary = "Save settings of my shop (owner)",
			description = "Empty heroTitle → shop name, empty aboutTitle → អំពីយើង, '@' removed from telegram.")
	public ApiResponse<ShopSettings> save(@CurrentShop ShopContext ctx, @RequestBody ShopSettings body) {
		ctx.requireOwner();
		if (body == null) {
			throw ApiException.validation(Messages.BAD_REQUEST);
		}
		ShopSettings current = shops.getSettings(ctx.shopId());
		String shopName = Validators.text(body.shopName(), "shopName", 80);
		if (shopName.isEmpty()) {
			throw ApiException.validation("shopName", Messages.SHOP_NAME_REQUIRED);
		}
		String background = Validators.text(body.backgroundColor()).toLowerCase();
		if (!Validators.isHexColor(background)) {
			throw ApiException.validation("backgroundColor", Messages.BACKGROUND_INVALID);
		}
		String heroImage = Validators.text(body.heroImage());
		String file = Validators.uploadFileName(heroImage);
		boolean heroOk = file != null ? uploads.belongsToShop(ctx.shopId(), file) : Validators.isRemoteUrl(heroImage);
		if (!heroOk) {
			throw ApiException.validation("heroImage", Messages.HERO_IMAGE_INVALID);
		}
		String telegram = Validators.text(body.telegram()).replaceFirst("^@", "");
		if (telegram.isEmpty()) {
			telegram = current.telegram();
		}
		if (!telegram.matches("^[A-Za-z0-9_]{5,32}$")) {
			throw ApiException.validation("telegram", Messages.TELEGRAM_INVALID);
		}
		String heroTitle = Validators.text(body.heroTitle(), "heroTitle", 120);
		String announcement = Validators.text(body.announcement(), "announcement", 200);
		String heroSubtitle = Validators.text(body.heroSubtitle(), "heroSubtitle", 300);
		String aboutMe = Validators.multiline(body.aboutMe(), "aboutMe", 5000);
		String aboutTitle = Validators.text(body.aboutTitle(), "aboutTitle", 80);
		var saved = new ShopSettings(shopName, telegram, announcement,
				heroTitle.isEmpty() ? shopName : heroTitle, heroSubtitle, heroImage, background,
				aboutTitle.isEmpty() ? "អំពីយើង" : aboutTitle, aboutMe);
		shops.updateSettings(ctx.shopId(), saved);
		evictions.shopChanged(ctx.shopId());
		return ApiResponse.of(saved, Messages.SETTINGS_SAVED);
	}
}
