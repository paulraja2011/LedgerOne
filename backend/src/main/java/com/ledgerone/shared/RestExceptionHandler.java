package com.ledgerone.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handle(ApiException ex) {
        return problem(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", msg);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handle(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The record was changed by someone else. Reload and try again.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setProperty("code", code);
        return ResponseEntity.status(status).body(pd);
    }
}
