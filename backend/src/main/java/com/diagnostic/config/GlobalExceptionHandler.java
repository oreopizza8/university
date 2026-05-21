package com.diagnostic.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_FAILED");
        Map<String, String> fields = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** 잘못된 타입·malformed JSON 등 본문 파싱 실패 → 클라이언트 오류(400). 서버 장애(500)와 구분. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "MALFORMED_REQUEST");
        body.put("message", "요청 본문을 해석할 수 없습니다. JSON 형식과 필드 타입을 확인하세요.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** 지원하지 않는 HTTP 메서드 → 405. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "METHOD_NOT_ALLOWED");
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "INTERNAL_ERROR");
        // NPE 등은 getMessage()가 null이라 클래스명으로 폴백(소비자에 null 노출 방지).
        body.put("message", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
