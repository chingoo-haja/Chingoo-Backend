package com.ldsilver.chingoohaja.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 공통 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "내부 서버 오류가 발생했습니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C002", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "허용되지 않은 HTTP 메서드입니다."),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C004", "접근이 거부되었습니다."),

    // 인증/인가 에러
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "유효하지 않은 리프레시 토큰입니다."),

    // 사용자 관련 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U003", "이미 사용 중인 닉네임입니다."),
    INVALID_USER_TYPE(HttpStatus.BAD_REQUEST, "U004", "유효하지 않은 사용자 타입입니다."),
    PROFILE_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "U005", "프로필 정보가 완성되지 않았습니다."),

    // 매칭 관련 에러
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "M001", "이미 매칭 대기열에 참가하고 있습니다."),
    QUEUE_NOT_FOUND(HttpStatus.NOT_FOUND, "M002", "매칭 대기열 정보를 찾을 수 없습니다."),
    MATCHING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "M003", "매칭에 실패했습니다."),
    QUEUE_EXPIRED(HttpStatus.BAD_REQUEST, "M004", "매칭 대기열이 만료되었습니다."),

    // 통화 관련 에러
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "통화 정보를 찾을 수 없습니다."),
    CALL_ALREADY_STARTED(HttpStatus.CONFLICT, "C002", "이미 시작된 통화입니다."),
    CALL_ALREADY_ENDED(HttpStatus.CONFLICT, "C003", "이미 종료된 통화입니다."),
    CALL_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "C004", "통화 참가자가 아닙니다."),
    CALL_SESSION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "통화 세션 생성에 실패했습니다."),

    // 카테고리 관련 에러
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "T002", "비활성화된 카테고리입니다."),

    // 평가 관련 에러
    EVALUATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "E001", "이미 평가를 완료했습니다."),
    EVALUATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "E002", "평가할 수 있는 권한이 없습니다."),
    SELF_EVALUATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "E003", "자기 자신을 평가할 수 없습니다."),

    // 친구 관련 에러
    FRIENDSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT, "F001", "이미 친구 관계입니다."),
    FRIENDSHIP_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, "F002", "이미 친구 요청을 보냈습니다."),
    FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "F003", "친구 관계를 찾을 수 없습니다."),
    SELF_FRIENDSHIP_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "F004", "자기 자신과는 친구가 될 수 없습니다."),

    // 보호자 관련 에러
    GUARDIAN_RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "보호자 관계를 찾을 수 없습니다."),
    GUARDIAN_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "G002", "보호자 권한이 없습니다."),
    INVALID_GUARDIAN_RELATIONSHIP(HttpStatus.BAD_REQUEST, "G003", "유효하지 않은 보호자 관계입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}