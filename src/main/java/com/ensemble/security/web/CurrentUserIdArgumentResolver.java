package com.ensemble.security.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves a {@link CurrentUserId}-annotated {@code String} parameter to the caller's
 * {@code userId} that {@link SessionAuthFilter} stored in the request attribute after
 * verifying the session token. Keeps controllers free of servlet-attribute lookups.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUserId.class)
			&& String.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		return webRequest.getAttribute(SessionAuthFilter.USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
	}
}
