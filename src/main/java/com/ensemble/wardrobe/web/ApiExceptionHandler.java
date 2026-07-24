package com.ensemble.wardrobe.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.ensemble.outfit.InvalidOutfitException;
import com.ensemble.outfit.OutfitNotFoundException;
import com.ensemble.outfit.web.OutfitController;
import com.ensemble.security.InvalidCredentialsException;
import com.ensemble.security.InvalidPasscodeException;
import com.ensemble.security.SessionUserNotFoundException;
import com.ensemble.security.web.AuthController;
import com.ensemble.storage.InvalidImageException;
import com.ensemble.storage.PhotoNotFoundException;
import com.ensemble.stylist.StylistUnavailableException;
import com.ensemble.stylist.web.StyleController;
import com.ensemble.tagging.web.TaggingController;
import com.ensemble.usage.DailyCapExceededException;
import com.ensemble.user.DuplicateUsernameException;
import com.ensemble.user.web.AccountController;
import com.ensemble.user.web.MeController;
import com.ensemble.wardrobe.ItemNotFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Maps domain and request errors to HTTP responses for the wardrobe, tagging,
 * stylist, auth, and account APIs: unknown ids → 404, invalid input (validation, bad range,
 * missing/invalid photo, malformed JSON) → 400, an unavailable/ungroundable
 * stylist → 503, a wrong/blank passcode or bad login → 401, a duplicate sign-up username → 409.
 * Returns a small sanitized error body. The tag-preview, style, auth, account, me, and outfit
 * controllers are covered here too, so their failures reuse the same sanitized error shape.
 */
@RestControllerAdvice(assignableTypes = {
	WardrobeController.class,
	TaggingController.class,
	StyleController.class,
	AuthController.class,
	OutfitController.class,
	MeController.class,
	AccountController.class
})
public class ApiExceptionHandler {

	/** Small error body — no internals or stack traces leak to the client. */
	public record ErrorResponse(String error, String message) {
	}

	@ExceptionHandler(ItemNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(ItemNotFoundException ex) {
		return new ErrorResponse("not_found", ex.getMessage());
	}

	/** An operation targeted an {@code outfitId} that does not exist. */
	@ExceptionHandler(OutfitNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleOutfitNotFound(OutfitNotFoundException ex) {
		return new ErrorResponse("not_found", ex.getMessage());
	}

	/**
	 * The save-time grounding guard rejected the whole save (empty items, bad
	 * source, or an unknown item id). The exception message is user-safe — it echoes
	 * only client-supplied values, never internals — so it is passed through to drive
	 * a friendly client message, mirroring the stylist-unavailable handler.
	 */
	@ExceptionHandler(InvalidOutfitException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidOutfit(InvalidOutfitException ex) {
		return new ErrorResponse("bad_request", ex.getMessage());
	}

	/**
	 * A record exists but its photo file is missing (inconsistent state). Degrade to a
	 * clean 404 with a generic message rather than a 500 that leaks the internal key.
	 */
	@ExceptionHandler(PhotoNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handlePhotoNotFound(PhotoNotFoundException ex) {
		return new ErrorResponse("not_found", "not found");
	}

	/**
	 * All bad-input paths return the same generic message. The raw exception text
	 * (field-binding detail, JSON parser internals) is deliberately not echoed to
	 * the client; the status + {@code bad_request} code carry the contract.
	 */
	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		BindException.class,
		ConstraintViolationException.class,
		MissingServletRequestPartException.class,
		HttpMessageNotReadableException.class,
		InvalidImageException.class
	})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleBadRequest(Exception ex) {
		return new ErrorResponse("bad_request", "invalid request");
	}

	/**
	 * The stylist could not return a grounded outfit — an upstream Claude
	 * error/timeout or an ungroundable pick (zero valid ids after the one retry).
	 * The exception message is already user-safe (no internals), so it is echoed to
	 * drive a friendly client message; a hallucinated id is never rendered.
	 */
	@ExceptionHandler(StylistUnavailableException.class)
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public ErrorResponse handleStylistUnavailable(StylistUnavailableException ex) {
		return new ErrorResponse("stylist_unavailable", ex.getMessage());
	}

	/** A wrong, blank, or missing passcode. The message never confirms/denies why. */
	@ExceptionHandler(InvalidPasscodeException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidPasscode(InvalidPasscodeException ex) {
		return new ErrorResponse("unauthorized", "invalid passcode");
	}

	/**
	 * Login failed — unknown username or wrong password. The same generic message is returned
	 * for both so the response never reveals whether a username is registered (no enumeration).
	 */
	@ExceptionHandler(InvalidCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidCredentials(InvalidCredentialsException ex) {
		return new ErrorResponse("unauthorized", "invalid username or password");
	}

	/**
	 * A cryptographically valid session token whose account no longer exists (orphaned token).
	 * Not a login failure, so it returns the gate's generic {@code 401 "authentication required"}
	 * — telling the client to re-authenticate — rather than the login "invalid username or password",
	 * which would misdescribe a request that carried a valid token and no credentials.
	 */
	@ExceptionHandler(SessionUserNotFoundException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleSessionUserNotFound(SessionUserNotFoundException ex) {
		return new ErrorResponse("unauthorized", "authentication required");
	}

	/**
	 * Sign-up hit a username that is already registered (the atomic {@code attribute_not_exists}
	 * conditional put failed). The message is a fixed, user-safe string — it echoes no internals.
	 */
	@ExceptionHandler(DuplicateUsernameException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleDuplicateUsername(DuplicateUsernameException ex) {
		return new ErrorResponse("conflict", "username already registered");
	}

	/** The global daily call cap has been exceeded; Claude was not called for this request. */
	@ExceptionHandler(DailyCapExceededException.class)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public ErrorResponse handleDailyCapExceeded(DailyCapExceededException ex) {
		return new ErrorResponse("daily_cap_exceeded", ex.getMessage());
	}
}
