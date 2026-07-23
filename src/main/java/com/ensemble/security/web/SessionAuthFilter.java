package com.ensemble.security.web;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ensemble.security.SessionTokenService;
import com.ensemble.wardrobe.web.ApiExceptionHandler.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gates every request it sees behind a valid session token, except the token-free entry points
 * {@code POST /api/auth} (login), {@code POST /api/accounts} (sign-up), and
 * {@code GET /api/health}. Registered (see {@code com.ensemble.security.SecurityConfig})
 * via a {@code FilterRegistrationBean} scoped to {@code /api/*} rather than as a
 * {@code @Component} bean, so it is applied to the real servlet container and full
 * {@code @SpringBootTest} contexts but is <strong>not</strong> auto-added to narrow
 * {@code @WebMvcTest} slices (which build a controller-only MVC context and never see
 * beans outside their own {@code @Configuration}) — the pre-existing specs 01-06 slice
 * tests need no token.
 *
 * <p>Accepts the token via the {@code X-Ensemble-Session} header or, for media
 * {@code <img>} GETs that cannot set headers, a {@code token} query parameter. A missing
 * or invalid token short-circuits with a sanitized {@code 401} — the request never
 * reaches a controller, so no data access or Claude call happens.
 *
 * <p>On a valid token the resolved {@code userId} (from
 * {@link SessionTokenService#verify}) is exposed as the {@link #USER_ID_ATTRIBUTE} request
 * attribute, so downstream controllers read the caller's identity via the
 * {@link CurrentUserId} argument resolver without touching servlet internals.
 */
public class SessionAuthFilter extends OncePerRequestFilter {

	/** Request attribute holding the authenticated caller's opaque {@code userId}. */
	public static final String USER_ID_ATTRIBUTE = "ensemble.userId";

	static final String TOKEN_HEADER = "X-Ensemble-Session";
	static final String TOKEN_PARAM = "token";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final SessionTokenService tokenService;

	public SessionAuthFilter(SessionTokenService tokenService) {
		this.tokenService = tokenService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isOpen(request)) {
			chain.doFilter(request, response);
			return;
		}
		String token = request.getHeader(TOKEN_HEADER);
		if (token == null || token.isBlank()) {
			token = request.getParameter(TOKEN_PARAM);
		}
		Optional<String> userId = (token == null) ? Optional.empty() : tokenService.verify(token);
		if (userId.isPresent()) {
			request.setAttribute(USER_ID_ATTRIBUTE, userId.get());
			chain.doFilter(request, response);
			return;
		}
		writeUnauthorized(response);
	}

	/**
	 * Uses {@link HttpServletRequest#getRequestURI()} rather than {@code getServletPath()}:
	 * with the app's default (root) {@code DispatcherServlet} mapping, {@code getServletPath()}
	 * returns an empty string and only the request URI carries the path.
	 */
	private boolean isOpen(HttpServletRequest request) {
		String path = request.getRequestURI();
		if ("/api/health".equals(path)) {
			return "GET".equalsIgnoreCase(request.getMethod());
		}
		// Login and invite-only sign-up are the two token-free entry points: a caller has no
		// session yet when acquiring one. Both are POST-only.
		if ("/api/auth".equals(path) || "/api/accounts".equals(path)) {
			return "POST".equalsIgnoreCase(request.getMethod());
		}
		return false;
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		OBJECT_MAPPER.writeValue(response.getWriter(), new ErrorResponse("unauthorized", "authentication required"));
	}
}
