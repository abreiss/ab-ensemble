package com.ensemble.wardrobe.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.ensemble.storage.InvalidImageException;
import com.ensemble.storage.PhotoNotFoundException;
import com.ensemble.tagging.web.TaggingController;
import com.ensemble.wardrobe.ItemNotFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Maps domain and request errors to HTTP responses for the wardrobe and tagging
 * APIs: unknown ids → 404, invalid input (validation, bad range, missing/invalid
 * photo, malformed JSON) → 400. Returns a small sanitized error body. The tag-preview
 * controller is covered here too, so its missing-part / non-image failures reuse the
 * same sanitized 400 shape.
 */
@RestControllerAdvice(assignableTypes = {WardrobeController.class, TaggingController.class})
public class ApiExceptionHandler {

	/** Small error body — no internals or stack traces leak to the client. */
	public record ErrorResponse(String error, String message) {
	}

	@ExceptionHandler(ItemNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(ItemNotFoundException ex) {
		return new ErrorResponse("not_found", ex.getMessage());
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
}
