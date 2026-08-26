package com.theskysid.echobackend.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * One place that turns an exception into the {"error": "..."} body the frontend
 * reads (see authService's response interceptor).
 *
 * The services throw plain RuntimeException for a bad request and
 * ResponseStatusException when the status matters (403 for a non-member), so
 * controllers can return their DTO directly instead of each wrapping the same
 * try/catch. Spring already answers 401 for an unauthenticated request — see
 * SecurityConfig's anyRequest().authenticated() — so nothing here handles that.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> onStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", String.valueOf(e.getReason())));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> onRuntime(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
