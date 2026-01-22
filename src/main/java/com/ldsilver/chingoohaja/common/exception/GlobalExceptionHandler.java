package com.ldsilver.chingoohaja.common.exception;

import com.ldsilver.chingoohaja.dto.common.ErrorResponse;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;



@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // *******순서 유념해서 추가하기*********

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("CustomException: {}", e.getMessage(), e);
        final ErrorCode errorCode = e.getErrorCode();
        final ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    /**
     * 비관적 락 타임아웃 예외 처리 (가장 구체적인 락 예외)
     */
    @ExceptionHandler(LockTimeoutException.class)
    protected ResponseEntity<ErrorResponse> handleLockTimeoutException(
            LockTimeoutException e,
            HttpServletRequest request) {

        log.error("락 타임아웃 발생 - URI: {}, Method: {}, Message: {}",
                request.getRequestURI(), request.getMethod(), e.getMessage());

        final ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "요청이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
        );
        return new ResponseEntity<>(response, HttpStatus.REQUEST_TIMEOUT);
    }

    /**
     * 비관적 락 획득 실패 예외 처리
     */
    @ExceptionHandler(PessimisticLockException.class)
    protected ResponseEntity<ErrorResponse> handlePessimisticLockException(
            PessimisticLockException e,
            HttpServletRequest request) {

        log.error("비관적 락 획득 실패 - URI: {}, Method: {}",
                request.getRequestURI(), request.getMethod(), e);

        final ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "다른 요청 처리 중입니다. 잠시 후 다시 시도해주세요."
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * 낙관적 락 충돌 예외 처리
     */
    @ExceptionHandler(OptimisticLockException.class)
    protected ResponseEntity<ErrorResponse> handleOptimisticLockException(
            OptimisticLockException e,
            HttpServletRequest request) {

        log.warn("낙관적 락 충돌 - URI: {}, Method: {}",
                request.getRequestURI(), request.getMethod(), e);

        final ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "동시에 수정된 데이터입니다. 다시 시도해주세요."
        );
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    /**
     * Bean Validation 예외 처리 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException: {}", e.getMessage());
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Bean Validation 예외 처리 (@ModelAttribute)
     */
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        log.error("BindException: {}", e.getMessage());
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 파라미터 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException: {}", e.getMessage());
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 지원하지 않는 HTTP 메서드 예외 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        final ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * 접근 거부 예외 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.error("AccessDeniedException: {}", e.getMessage());
        final ErrorResponse response = ErrorResponse.of(ErrorCode.HANDLE_ACCESS_DENIED);
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // NullPointerException 명시적 처리 추가
    @ExceptionHandler(NullPointerException.class)
    protected ResponseEntity<ErrorResponse> handleNullPointerException(
            NullPointerException e,
            HttpServletRequest request) {

        log.error("NullPointerException - URI: {}, Method: {}, Message: {}",
                request.getRequestURI(),
                request.getMethod(),
                e.getMessage(),
                e);

        // userDetails null로 인한 NPE 처리
        if (e.getMessage() != null && e.getMessage().contains("CustomUserDetails")) {
            log.error("인증 정보(userDetails) null로 인한 NPE");
            final ErrorResponse response = ErrorResponse.of(ErrorCode.UNAUTHORIZED);
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        // 기타 NPE는 서버 오류로 처리
        final ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 일반적인 예외 처리 (최후 방어선)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request) {

        log.error("Unhandled Exception - URI: {}, Method: {}, Type: {}, Message: {}",
                request.getRequestURI(),
                request.getMethod(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                e);

        final ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
