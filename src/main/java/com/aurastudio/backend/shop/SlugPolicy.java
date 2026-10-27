package com.aurastudio.backend.shop;

import java.util.Set;
import java.util.regex.Pattern;

import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.Messages;

/** Shop link rules (docs/API_AND_DATABASE.md §12). */
public final class SlugPolicy {

	private static final Pattern FORMAT = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
	private static final Set<String> RESERVED = Set.of("admin", "api", "uploads", "start", "login", "register", "shops",
			"catalogue", "products", "settings", "static", "public", "assets", "images", "favicon.ico", "robots.txt",
			"sitemap.xml", "_next", "aura");

	private SlugPolicy() {
	}

	public static void check(String slug) {
		if (!FORMAT.matcher(slug).matches()) {
			throw ApiException.validation("slug", Messages.SLUG_FORMAT);
		}
		if (slug.length() < 3 || slug.length() > 40) {
			throw ApiException.validation("slug", Messages.SLUG_LENGTH);
		}
		if (RESERVED.contains(slug)) {
			throw ApiException.validation("slug", Messages.SLUG_RESERVED);
		}
	}
}
