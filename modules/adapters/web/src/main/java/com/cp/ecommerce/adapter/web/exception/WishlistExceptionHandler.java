package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;
import java.util.UUID;

import com.cp.ecommerce.foundation.exception.WishlistConflictException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Dedicated exception handler for wishlist-specific web errors.
 */
@RestControllerAdvice(annotations = Component.class)
@Slf4j
public class WishlistExceptionHandler {

    private static final String ERROR_ID_PROPERTY = "errorId";

    private static final URI TYPE_WISHLIST_CONFLICT = URI.create("urn:problem-type:wishlist-conflict");

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(WishlistConflictException.class)
    public ProblemDetail wishlistConflictException(final WishlistConflictException exception) {

        final String errorId = UUID.randomUUID().toString();
        log.error("{} [{}]: {}", exception.getClass().getSimpleName(), errorId, exception.getMessage());

        final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setType(TYPE_WISHLIST_CONFLICT);
        problemDetail.setTitle("Wishlist Conflict");
        problemDetail.setProperty(ERROR_ID_PROPERTY, errorId);
        return problemDetail;
    }

}
