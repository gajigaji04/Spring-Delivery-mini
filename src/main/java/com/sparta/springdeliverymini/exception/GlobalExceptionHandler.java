package com.sparta.springdeliverymini.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 409 아이디 중복, 401 로그인 실패 등 서비스에서 상태코드를 지정해 던지는 예외
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    // 동시에 같은 아이디로 가입해 중복 체크를 통과한 경우에도 DB unique 제약으로 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "이미 사용 중인 아이디입니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    // JSON 문법 오류, 타입 불일치, enum에 없는 값이 들어오는 등 본문을 읽을 수 없는 경우
    // 예) 회원가입 role에 "ADMIN", 주문 상태 변경 status에 "FOO", 숫자 필드에 문자열
    // 여러 API에서 공통으로 발생하므로 특정 필드를 언급하지 않는 메시지 사용
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", "요청 본문 형식이 올바르지 않습니다. 값의 형식과 허용된 값을 확인해주세요."));
    }
}
