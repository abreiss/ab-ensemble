package com.ensemble.security.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller method's {@code String} parameter to the authenticated caller's opaque
 * {@code userId}, resolved by {@link CurrentUserIdArgumentResolver} from the
 * {@link SessionAuthFilter#USER_ID_ATTRIBUTE} request attribute the gate filter sets on a
 * valid session token. On a protected route the filter guarantees the attribute is present
 * before the controller runs; on an (unusual) open route reached without one, the argument
 * resolves to {@code null}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
