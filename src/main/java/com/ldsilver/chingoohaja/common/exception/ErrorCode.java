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
    IS_NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A006", "리프레시 토큰이 아닙니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A007", "데이터베이스에서 리프레시 토큰을 찾을 수 없습니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "A008", "유효하지 않은 액세스 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND_IN_COOKIE(HttpStatus.UNAUTHORIZED, "A009", "쿠키에서 리프레시 토큰을 찾을 수 없습니다."),
    COOKIE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "A010", "쿠키를 지원하지 않는 환경입니다."),

    // JWT 관련 에러
    JWT_INVALID(HttpStatus.UNAUTHORIZED, "J001", "유효하지 않은 JWT 토큰입니다."),
    JWT_EXPIRED(HttpStatus.UNAUTHORIZED, "J002", "만료된 JWT 토큰입니다."),
    JWT_MALFORMED(HttpStatus.UNAUTHORIZED, "J003", "잘못된 형식의 JWT 토큰입니다."),
    JWT_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "J004", "JWT 토큰 서명이 유효하지 않습니다."),
    JWT_UNSUPPORTED(HttpStatus.UNAUTHORIZED, "J005", "지원되지 않는 JWT 토큰입니다."),
    JWT_CLAIMS_EMPTY(HttpStatus.UNAUTHORIZED, "J006", "JWT 토큰의 클레임이 비어있습니다."),
    JWT_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "J007", "JWT 토큰을 찾을 수 없습니다."),

    // OAuth 관련 에러 (기존 코드에 추가)
    OAUTH_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "O001", "지원하지 않는 OAuth 공급자입니다: %s"),
    OAUTH_TOKEN_EXCHANGE_FAILED(HttpStatus.BAD_REQUEST, "O002", "OAuth 토큰 교환에 실패했습니다."),
    OAUTH_USER_INFO_FETCH_FAILED(HttpStatus.BAD_REQUEST, "O003", "OAuth 사용자 정보 조회에 실패했습니다."),
    OAUTH_INVALID_STATE(HttpStatus.BAD_REQUEST, "O004", "유효하지 않은 OAuth State 파라미터입니다."),
    OAUTH_INVALID_CODE(HttpStatus.BAD_REQUEST, "O005", "유효하지 않은 OAuth 인가 코드입니다."),
    OAUTH_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "O006", "OAuth 설정 오류입니다."),
    OAUTH_PKCE_GENERATION_FAILED(HttpStatus.BAD_REQUEST, "O007", "PKCE 코드 챌린지 생성에 실패했습니다."),

    // 사용자 관련 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U003", "이미 사용 중인 닉네임입니다."),
    INVALID_USER_TYPE(HttpStatus.BAD_REQUEST, "U004", "유효하지 않은 사용자 타입입니다."),
    PROFILE_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "U005", "프로필 정보가 완성되지 않았습니다."),
    USER_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "U006", "사용자 생성에 실패했습니다."),
    IMAGE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "U007", "이미지 저장 중 오류가 발생했습니다."),
    IMAGE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "U008", "이미지 파일이 비어있습니다."),
    IMAGE_FILE_OVERSIZED(HttpStatus.BAD_REQUEST, "U009", "이미지 파일 크기는 5MB를 초과할 수 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "U010", "지원되지 않는 이미지 형식입니다. (JPEG, PNG, WebP만 허용)"),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "U011", "유효하지 않은 파일명입니다."),
    NO_PROFILE_CHANGE(HttpStatus.BAD_REQUEST, "U012", "수정을 원하는 사용자 프로필 데이터가 없습니다."),
    INVALID_IMAGE_URL_LENGTH(HttpStatus.BAD_REQUEST, "U013", "사용자 프로필 이미지 URL 길이가 초과되었습니다."),
    USER_ID_TOO_LARGE(HttpStatus.BAD_REQUEST, "U014", "사용자 ID가 Agora UID 범위를 초과했습니다: %s"),

    // 닉네임 관련 에러
    NICKNAME_WORDS_NOT_LOADED(HttpStatus.INTERNAL_SERVER_ERROR, "N001", "닉네임 생성을 위한 단어가 로드되지 않았습니다."),
    NICKNAME_ADJECTIVES_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "N002", "형용사 목록이 비어있습니다."),
    NICKNAME_NOUNS_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "N003", "명사 목록이 비어있습니다."),
    NICKNAME_RESOURCE_LOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N004", "닉네임 리소스 파일 로드에 실패했습니다."),
    NICKNAME_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N005", "닉네임 생성에 실패했습니다."),
    NICKNAME_ALL_COMBINATIONS_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "N006", "사용 가능한 모든 닉네임 조합이 소진되었습니다."),

    // 파일 관련 에러
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "FS001", "파일 크기가 허용된 최대 크기를 초과했습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "FS002", "지원되지 않는 파일 형식입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FS003", "파일 업로드에 실패했습니다."),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FS004", "파일 읽기에 실패했습니다."),

    // 매칭 관련 에러
    ALREADY_IN_QUEUE(HttpStatus.CONFLICT, "M001", "이미 매칭 대기열에 참가하고 있습니다."),
    QUEUE_NOT_FOUND(HttpStatus.NOT_FOUND, "M002", "매칭 대기열 정보를 찾을 수 없습니다."),
    MATCHING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "M003", "매칭에 실패했습니다."),
    QUEUE_EXPIRED(HttpStatus.BAD_REQUEST, "M004", "매칭 대기열이 만료되었습니다."),
    MATCHING_QUEUE_FULL(HttpStatus.INTERNAL_SERVER_ERROR, "M005", "매칭 대기열이 가득 찼습니다"),
    MATCHING_REDIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "M006", "Redis 매칭 서비스 연결에 실패했습니다."),
    STATUS_NOT_WAITING(HttpStatus.BAD_REQUEST, "M007", "대기 중인 상태에서만 취소할 수 있습니다. 현재 상태: %s"),
    START_MATCHING_FAILED(HttpStatus.BAD_REQUEST, "M008", "대기 중인 상태에서만 매칭을 시작할 수 있습니다. 현재 상태: %s"),
    MATCHING_EXPIRED_FAILED(HttpStatus.BAD_REQUEST, "M009", "대기 중인 상태에서만 만료할 수 있습니다. 현재 상태: %s"),

    // 통화 관련 에러
    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "L001", "통화 정보를 찾을 수 없습니다."),
    CALL_ALREADY_STARTED(HttpStatus.CONFLICT, "L002", "이미 시작된 통화입니다."),
    CALL_ALREADY_ENDED(HttpStatus.CONFLICT, "L003", "이미 종료된 통화입니다."),
    CALL_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "L004", "통화 참가자가 아닙니다."),
    CALL_SESSION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "L005", "통화 세션 생성에 실패했습니다."),
    CALL_START_FAILED(HttpStatus.BAD_REQUEST, "L006", "통화를 시작할 수 없는 상태입니다."),
    UID_NOT_MINUS(HttpStatus.BAD_REQUEST, "L007", "UID는 음수일 수 없습니다."),
    ROLE_REQUIRED(HttpStatus.BAD_REQUEST, "L008", "Role은 필수입니다."),
    INVALID_EXPIRED_TIME(HttpStatus.BAD_REQUEST, "L009", "만료시간(초)는 0보다 커야 합니다."),
    CHANNEL_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "L010", "채널명은 필수입니다."),
    CHANNEL_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "L011", "채널명은 UTF‑8 기준 64바이트를 초과할 수 없습니다."),
    CHANNEL_NAME_INVALID(HttpStatus.BAD_REQUEST, "L012", "채널명 형식이 유효하지 않습니다."),
    CALL_NOT_IN_PROGRESS(HttpStatus.BAD_REQUEST, "L013", "진행 중인 통화가 아닙니다."),
    RECORDING_NOT_STARTED(HttpStatus.BAD_REQUEST, "L014", "녹음이 시작되지 않았습니다."),
    RECORDING_ALREADY_STARTED(HttpStatus.CONFLICT, "L015", "이미 녹음이 시작되었습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "L016", "세션을 찾을 수 없습니다."),
    SESSION_ALREADY_JOINED(HttpStatus.CONFLICT, "L017", "이미 참가 중이거나 참가할 수 없는 상태입니다."),
    SESSION_NOT_JOINED(HttpStatus.BAD_REQUEST, "L018", "참가하지 않은 세션입니다."),
    TOKEN_REFRESH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "L019", "토큰 갱신에 실패했습니다."),
    CALL_REQUIRED(HttpStatus.BAD_REQUEST, "L020", "Call은 필수입니다."),
    USER_REQUIRED(HttpStatus.BAD_REQUEST, "L021", "User는 필수입니다."),
    AGORA_UID_INVALID(HttpStatus.BAD_REQUEST, "L022", "Agora UID는 0 이상 정수여야 합니다."),
    RTC_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "L023", "RTC Token은 필수입니다."),
    QUALITY_RANGE_INVALID(HttpStatus.BAD_REQUEST, "L024", "연결 품질은 1-6 범위여야 합니다."),
    BIT_RATE_TOO_SMALL(HttpStatus.BAD_REQUEST, "L025", "비트레이트는 0 이상이어야 합니다."),
    PACKET_LOSS_RANGE_INVALID(HttpStatus.BAD_REQUEST, "L026", "패킷 손실률은 0-100% 범위여야 합니다."),
    CALL_USER_NOT_EQUAL(HttpStatus.BAD_REQUEST, "L027", "통화 참가자는 서로 다른 사용자여야 합니다."),
    AGORA_RESOURCE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "L028", "Agora Resource ID는 필수입니다."),
    AGORA_SID_REQUIRED(HttpStatus.BAD_REQUEST, "L029", "Agora SID는 필수입니다."),
    INVALID_RESOURCE_ID(HttpStatus.INTERNAL_SERVER_ERROR, "L030", "Resource ID를 획득할 수 없습니다."),
    AGORA_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "L031", "Agora API 인증에 실패했습니다."),
    RECORDING_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "L032", "녹음 리소스를 찾을 수 없습니다."),
    AGORA_REQUEST_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "L033", "API 호출 한도를 초과했습니다."),
    AGORA_REQUEST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "L034", "Agora API 호출에 실패했습니다."),
    RECORDING_START_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "L035", "Recording 시작에 실패했습니다."),
    RECORDING_STOP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "L036", "Recording 중지 응답을 받지 못했습니다."),


    // 카테고리 관련 에러
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "카테고리를 찾을 수 없습니다."),
    CATEGORY_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "T002", "비활성화된 카테고리입니다."),
    CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "T003", "이미 존재하는 카테고리 이름입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "T004", "사용 중인 카테고리는 삭제할 수 없습니다."),
    INVALID_CATEGORY_TYPE(HttpStatus.BAD_REQUEST, "T005", "유효하지 않은 카테고리 타입입니다."),

    // 평가 관련 에러
    EVALUATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "E001", "이미 평가를 완료했습니다."),
    EVALUATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "E002", "평가할 수 있는 권한이 없습니다."),
    SELF_EVALUATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "E003", "자기 자신을 평가할 수 없습니다."),
    EVALUATION_FOR_NOT_COMPLETED_CALL(HttpStatus.FORBIDDEN, "E004", "완료된 통화만 평가할 수 있습니다."),
    FEEDBACK_TYPE_NOT_NULL(HttpStatus.BAD_REQUEST, "E005", "피드백 타입은 null일 수 없습니다."),
    INVALID_FEEDBACK_TYPE(HttpStatus.BAD_REQUEST, "E006", "피드백 타입이 유효하지 않습니다."),

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

    public String formatMessage(Object... args) {
        return String.format(this.message, args);
    }
}