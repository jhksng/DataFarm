package com.smartfarm.smartfarm_server.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception e) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", false);
        Map<String, Object> errors = new LinkedHashMap<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("exception", e.getClass().getName());
        server.put("message", e.getMessage());
        errors.put("server", server);
        resp.put("errors", errors);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp);
    }
}
