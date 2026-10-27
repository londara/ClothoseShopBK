package com.aurastudio.backend.bootstrap;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aurastudio.backend.category.CategoryRepository;
import com.aurastudio.backend.common.Validators;
import com.aurastudio.backend.config.AuraProperties;
import com.aurastudio.backend.shop.ShopRepository;
import com.aurastudio.backend.shop.ShopSettings;
import com.aurastudio.backend.shop.ShopStatus;
import com.aurastudio.backend.shop.SlugPolicy;
import com.aurastudio.backend.shoptype.ShopTypeRepository;
import com.aurastudio.backend.user.UserRepository;
import com.aurastudio.backend.user.UserRole;

/**
 * First run only: when the {@code users} table is empty, create the first shop
 * (active) and its owner as PLATFORM ADMIN from {@code aura.bootstrap.*}
 * (ADMIN_EMAIL / ADMIN_PHONE / ADMIN_PASSWORD). Does nothing afterwards.
 */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

	private final AuraProperties.Bootstrap props;
	private final UserRepository users;
	private final ShopRepository shops;
	private final ShopTypeRepository shopTypes;
	private final CategoryRepository categories;
	private final PasswordEncoder passwordEncoder;

	public PlatformAdminBootstrap(AuraProperties properties, UserRepository users, ShopRepository shops,
			ShopTypeRepository shopTypes, CategoryRepository categories, PasswordEncoder passwordEncoder) {
		this.props = properties.bootstrap();
		this.users = users;
		this.shops = shops;
		this.shopTypes = shopTypes;
		this.categories = categories;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.anyUserExists()) {
			return;
		}
		String email = Validators.blankToNull(props.adminEmail());
		String phone = Validators.blankToNull(props.adminPhone());
		String password = props.adminPassword();
		if ((email == null && phone == null) || password == null || password.length() < 8) {
			log.warn("No users yet. Set ADMIN_EMAIL or ADMIN_PHONE and ADMIN_PASSWORD (≥ 8 chars) to create the "
					+ "platform admin on startup.");
			return;
		}
		var contact = Validators.contact(email, phone, "ADMIN_EMAIL or ADMIN_PHONE");
		SlugPolicy.check(props.shopSlug());
		var type = shopTypes.findByCode(props.shopType()).orElseThrow();

		UUID shopId = shops.insert(props.shopSlug(), type.id(), ShopStatus.ACTIVE);
		shops.insertSettings(shopId, new ShopSettings(props.shopName(), props.shopTelegram(), "", props.shopName(), "",
				ShopSettings.DEFAULT_HERO, "#f8f9fa", "អំពីយើង", ""));
		categories.copyFromType(shopId, type.id());
		users.insert(shopId, UserRole.OWNER, true, props.adminName(), "ម្ចាស់ហាង", contact.email(), contact.phone(),
				passwordEncoder.encode(password));
		log.info("Created platform admin ({}) and first shop /{}",
				contact.email() != null ? contact.email() : contact.phone(), props.shopSlug());
	}
}
