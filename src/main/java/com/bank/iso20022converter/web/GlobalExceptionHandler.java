package com.bank.iso20022converter.web;

import com.bank.iso20022converter.core.exception.MalformedMessageException;
import com.bank.iso20022converter.core.exception.MappingException;
import com.bank.iso20022converter.core.exception.NoTransformerFoundException;
import com.bank.iso20022converter.core.exception.UnsupportedMessageTypeException;
import com.bank.iso20022converter.validation.CbprPlusViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnsupportedMessageTypeException.class)
    public ProblemDetail handleUnsupportedMessageType(UnsupportedMessageTypeException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        detail.setProperty("errorCode", "UNSUPPORTED_MESSAGE_TYPE");
        return detail;
    }

    @ExceptionHandler(MalformedMessageException.class)
    public ProblemDetail handleMalformedMessage(MalformedMessageException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        detail.setProperty("errorCode", "MALFORMED_MESSAGE");
        return detail;
    }

    @ExceptionHandler(NoTransformerFoundException.class)
    public ProblemDetail handleNoTransformerFound(NoTransformerFoundException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        detail.setProperty("errorCode", "NO_TRANSFORMER_FOUND");
        return detail;
    }

    @ExceptionHandler(MappingException.class)
    public ProblemDetail handleMappingException(MappingException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        detail.setProperty("errorCode", e.getErrorCode());
        detail.setProperty("violations", e.getViolations());
        return detail;
    }

    @ExceptionHandler(CbprPlusViolationException.class)
    public ProblemDetail handleCbprPlusViolation(CbprPlusViolationException e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        detail.setProperty("errorCode", CbprPlusViolationException.ERROR_CODE);
        detail.setProperty("violations", e.getViolations());
        return detail;
    }
}
