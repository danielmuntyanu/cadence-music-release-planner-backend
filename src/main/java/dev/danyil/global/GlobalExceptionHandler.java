package dev.danyil.global;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import dev.danyil.artists.exceptions.ArtistAlreadyExistsException;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.artists.exceptions.ArtistOwnershipException;
import dev.danyil.artists.exceptions.DuplicateSocialPlatformException;
import dev.danyil.releases.exceptions.ReleaseAlreadyExistsException;
import dev.danyil.releases.exceptions.ReleaseException;
import dev.danyil.releases.exceptions.ReleaseNotFountException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.security.exceptions.JwtNoExistException;
import dev.danyil.users.exceptions.CurrentUserNotFoundException;
import dev.danyil.users.exceptions.ProfileNotFoundException;
import dev.danyil.users.exceptions.UserAlreadyExistsException;
import dev.danyil.users.exceptions.UserNotFoundException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@RestControllerAdvice
public class GlobalExceptionHandler {


    // AUTHENTICATION

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Validation failed: " + e.getFieldError().getDefaultMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(
            BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(JwtNoExistException.class)
    public ResponseEntity<Map<String, String>> handleJwtNoExistException(JwtNoExistException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication failed: " + exception.getMessage()));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, String>> handleExpiredJwtException(ExpiredJwtException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication failed: " + exception.getMessage()));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, String>> handleJwtException(JwtException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication failed. " + exception.getMessage()));
    }

    @ExceptionHandler(CurrentUserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCurrentUserNotFoundException(CurrentUserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("username", e.getUsername(), "message", e.getMessage()));
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProfileNotFoundException(ProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("id", e.getId().toString(), "message", e.getMessage()));
    }

    // USERS

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleUserAlreadyExistsException(UserAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("field", e.getField(), "message", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFoundException(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("id" , e.getId().toString(), "message", e.getMessage()));
    }

    // ARTISTS

    @ExceptionHandler(ArtistAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleArtistAlreadyExistsException(ArtistAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("name", e.getName(), "message", e.getMessage()));
    }

    @ExceptionHandler(ArtistNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleArtistNotFoundException(ArtistNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("id", e.getId().toString(), "message", e.getMessage()));
    }

    @ExceptionHandler(DuplicateSocialPlatformException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSocialPlatformException(DuplicateSocialPlatformException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("platform", e.getPlatform().name(), "message", e.getMessage()));
    }

    @ExceptionHandler(ArtistOwnershipException.class)
    public ResponseEntity<Map<String, String>> handleArtistOwnershipException(ArtistOwnershipException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", e.getMessage()));
    }

    // RELEASES

    @ExceptionHandler(ReleaseException.class)
    public ResponseEntity<Map<String, String>> handleReleaseException(ReleaseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }
    
    @ExceptionHandler(ReleaseAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleReleaseAlreadyExistsException(ReleaseAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("name", e.getName(), "message", e.getMessage()));
    }

    @ExceptionHandler(ReleaseNotFountException.class)
    public ResponseEntity<Map<String, String>> handleReleaseNotFountException(ReleaseNotFountException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("id", e.getId().toString(), "message", e.getMessage()));
    }

    @ExceptionHandler(ReleaseOwnershipException.class)
    public ResponseEntity<Map<String, String>> handleReleaseOwnershipException(ReleaseOwnershipException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", e.getMessage()));
    }

    // COMMON 

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    // GENERIC

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", e.getMessage()));
    }

}
