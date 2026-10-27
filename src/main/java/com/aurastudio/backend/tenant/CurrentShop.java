package com.aurastudio.backend.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the caller's {@link ShopContext} into a controller method:
 * {@code public X list(@CurrentShop ShopContext ctx)}. Resolved by
 * {@link ShopContextResolver}; hidden from Swagger.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentShop {
}
