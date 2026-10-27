package com.aurastudio.backend.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.aurastudio.backend.tenant.ShopContextResolver;

/** Registers {@code @CurrentShop ShopContext} injection for controllers. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final ShopContextResolver shopContextResolver;

	public WebConfig(ShopContextResolver shopContextResolver) {
		this.shopContextResolver = shopContextResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(shopContextResolver);
	}
}
