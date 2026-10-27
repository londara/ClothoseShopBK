package com.aurastudio.backend.tenant;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.aurastudio.backend.auth.jwt.JwtService;
import com.aurastudio.backend.common.ApiException;
import com.aurastudio.backend.common.ErrorCode;
import com.aurastudio.backend.common.Messages;
import com.aurastudio.backend.shop.ShopStatus;
import com.aurastudio.backend.user.UserStatus;

/**
 * Request pipeline step of §4.1: the JWT was already verified by Spring
 * Security; here we load the user (cached briefly, see {@link TenantLookup}) and check that
 * <ul>
 * <li>the user still exists and is active,</li>
 * <li>the token's {@code shop} claim is still the user's shop,</li>
 * <li>the shop is not suspended (platform admins are never blocked).</li>
 * </ul>
 * The resulting {@link ShopContext} carries the shop id used by every query.
 */
@Component
public class ShopContextResolver implements HandlerMethodArgumentResolver {

	private final TenantLookup tenants;

	public ShopContextResolver(TenantLookup tenants) {
		this.tenants = tenants;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentShop.class)
				&& parameter.getParameterType().equals(ShopContext.class);
	}

	@Override
	public ShopContext resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
		}
		var jwt = auth.getToken();
		UUID userId;
		UUID tokenShop;
		try {
			userId = UUID.fromString(jwt.getSubject());
			tokenShop = UUID.fromString(jwt.getClaimAsString(JwtService.CLAIM_SHOP));
		}
		catch (RuntimeException e) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
		}

		var user = tenants.find(userId);
		if (user == null) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
		}
		if (user.status() == UserStatus.DISABLED) {
			throw new ApiException(ErrorCode.ACCOUNT_DISABLED, Messages.ACCOUNT_DISABLED);
		}
		if (user.status() != UserStatus.ACTIVE) {
			throw new ApiException(ErrorCode.ACCOUNT_PENDING, Messages.ACCOUNT_PENDING);
		}
		if (!user.shopId().equals(tokenShop)) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
		}
		if (!user.platformAdmin()) {
			if (user.shopStatus() == null) {
				throw new ApiException(ErrorCode.TOKEN_INVALID, Messages.TOKEN_INVALID);
			}
			if (user.shopStatus() == ShopStatus.SUSPENDED) {
				throw new ApiException(ErrorCode.SHOP_SUSPENDED, Messages.SHOP_SUSPENDED);
			}
		}
		return new ShopContext(user.id(), user.shopId(), user.role(), user.platformAdmin());
	}
}
