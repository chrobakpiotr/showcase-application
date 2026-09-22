package com.cp.ecommerce.adapter.web.exception;

import java.net.URI;

import com.cp.ecommerce.foundation.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRejectableException;
import com.cp.ecommerce.foundation.exception.ShipmentConflictException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.micrometer.tracing.Tracer;

import static org.springframework.http.HttpStatus.CONFLICT;

class ReturnAndShipmentExceptionHandlerSupport {

    private static final URI TYPE_BUSINESS_RULE_VIOLATION = URI.create("urn:problem-type:business-rule-violation");
    private static final URI TYPE_RETURN_QUANTITY_CONFLICT = URI.create("urn:problem-type:return-quantity-conflict");
    private static final URI TYPE_RETURN_REQUEST_NOT_APPROVABLE = URI.create("urn:problem-type:return-request-not-approvable");
    private static final URI TYPE_RETURN_REQUEST_NOT_REJECTABLE = URI.create("urn:problem-type:return-request-not-rejectable");
    private static final URI TYPE_RETURN_REQUEST_NOT_REFUNDABLE = URI.create("urn:problem-type:return-request-not-refundable");

    private final ProblemDetailFactory problemDetailFactory;

    ReturnAndShipmentExceptionHandlerSupport(final ObjectProvider<Tracer> tracerProvider) {

        this.problemDetailFactory = new ProblemDetailFactory(tracerProvider);
    }

    protected final ProblemDetail problemDetail(
            final Exception exception,
            final HttpStatusCode status,
            final URI type,
            final String title,
            final String detail) {

        return problemDetailFactory.create(exception, status, type, title, detail);
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(ReturnQuantityConflictException.class)
    public ProblemDetail returnQuantityConflictException(final ReturnQuantityConflictException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_RETURN_QUANTITY_CONFLICT,
                "Return Quantity Conflict",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(ReturnRequestNotApprovableException.class)
    public ProblemDetail returnRequestNotApprovableException(final ReturnRequestNotApprovableException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_RETURN_REQUEST_NOT_APPROVABLE,
                "Return Request Not Approvable",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(ReturnRequestNotRejectableException.class)
    public ProblemDetail returnRequestNotRejectableException(final ReturnRequestNotRejectableException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_RETURN_REQUEST_NOT_REJECTABLE,
                "Return Request Not Rejectable",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(ReturnRequestNotRefundableException.class)
    public ProblemDetail returnRequestNotRefundableException(final ReturnRequestNotRefundableException exception) {

        return problemDetail(
                exception,
                CONFLICT,
                TYPE_RETURN_REQUEST_NOT_REFUNDABLE,
                "Return Request Not Refundable",
                exception.getMessage());
    }

    @ResponseStatus(CONFLICT)
    @ExceptionHandler(ShipmentConflictException.class)
    public ProblemDetail shipmentConflictException(final ShipmentConflictException exception) {

        return problemDetail(exception, CONFLICT, TYPE_BUSINESS_RULE_VIOLATION, "Shipment Conflict", exception.getMessage());
    }
}
