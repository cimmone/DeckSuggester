package com.decksuggester;

import com.decksuggester.auth.AccountConflictException;
import com.decksuggester.auth.InvalidResetTokenException;
import com.decksuggester.cards.CardImageNotFoundException;
import com.decksuggester.decks.ArchidektException;
import com.decksuggester.decks.DeckNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(CardImageNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError imageNotFound(CardImageNotFoundException exception) {
        return new ApiError(404, exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(AccountConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError accountConflict(AccountConflictException exception) {
        return new ApiError(409, exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidResetToken(InvalidResetTokenException exception) {
        return new ApiError(400, exception.getMessage(), Instant.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("The request is invalid");
        return new ApiError(400, message, Instant.now());
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }
}
