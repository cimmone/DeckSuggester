package com.decksuggester;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest(InvalidRequestException exception) {
        return new ApiError(400, exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(DeckNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(DeckNotFoundException exception) {
        return new ApiError(404, exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(ArchidektException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiError archidektError(ArchidektException exception) {
        return new ApiError(502, exception.getMessage(), Instant.now());
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }
}
