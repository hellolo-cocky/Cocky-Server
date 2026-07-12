package com.cocky.cockyserver.global.exception;

import com.cocky.cockyserver.domain.auth.exception.OAuthCodeInvalidException;
import com.cocky.cockyserver.domain.auth.exception.OAuthServerException;
import com.cocky.cockyserver.domain.auth.exception.RefreshTokenExpiredException;
import com.cocky.cockyserver.domain.auth.exception.RefreshTokenInvalidException;
import com.cocky.cockyserver.domain.auth.exception.SignupNotAllowedException;
import com.cocky.cockyserver.domain.problem.exception.ProblemNotFoundException;
import com.cocky.cockyserver.domain.ranking.exception.RankingNotFoundException;
import com.cocky.cockyserver.domain.round.exception.RoundNotFoundException;
import com.cocky.cockyserver.domain.submission.exception.LanguageMismatchException;
import com.cocky.cockyserver.domain.submission.exception.RoundClosedException;
import com.cocky.cockyserver.domain.submission.exception.TestCaseNotConfiguredException;
import com.cocky.cockyserver.domain.submission.judge.JudgeExecutionException;
import com.cocky.cockyserver.global.security.AuthErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** JwtAuthenticationEntryPoint의 401 응답과 동일한 {code, message} 형태로 에러를 내려준다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("요청 값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", message));
    }

    @ExceptionHandler(OAuthCodeInvalidException.class)
    public ResponseEntity<ErrorResponse> handleOAuthCodeInvalid(OAuthCodeInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("OAUTH_CODE_INVALID", e.getMessage()));
    }

    @ExceptionHandler(SignupNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleSignupNotAllowed(SignupNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("SIGNUP_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler(OAuthServerException.class)
    public ResponseEntity<ErrorResponse> handleOAuthServerError(OAuthServerException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("OAUTH_SERVER_ERROR", e.getMessage()));
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenExpired(RefreshTokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(AuthErrorCode.TOKEN_EXPIRED.name(), AuthErrorCode.TOKEN_EXPIRED.message()));
    }

    @ExceptionHandler(RefreshTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handleRefreshTokenInvalid(RefreshTokenInvalidException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(AuthErrorCode.TOKEN_INVALID.name(), AuthErrorCode.TOKEN_INVALID.message()));
    }

    @ExceptionHandler(RoundNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoundNotFound(RoundNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ROUND_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProblemNotFound(ProblemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PROBLEM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(LanguageMismatchException.class)
    public ResponseEntity<ErrorResponse> handleLanguageMismatch(LanguageMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("LANGUAGE_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(RoundClosedException.class)
    public ResponseEntity<ErrorResponse> handleRoundClosed(RoundClosedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ROUND_CLOSED", e.getMessage()));
    }

    @ExceptionHandler(JudgeExecutionException.class)
    public ResponseEntity<ErrorResponse> handleJudgeExecutionFailed(JudgeExecutionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("JUDGE_EXECUTION_FAILED", e.getMessage()));
    }

    @ExceptionHandler(TestCaseNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleTestCaseNotConfigured(TestCaseNotConfiguredException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("TEST_CASE_NOT_CONFIGURED", e.getMessage()));
    }

    @ExceptionHandler(RankingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRankingNotFound(RankingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RANKING_NOT_FOUND", e.getMessage()));
    }
}