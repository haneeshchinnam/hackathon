package com.example.detection.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import java.time.Instant;
import java.util.*;

@lombok.extern.slf4j.Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex,HttpServletRequest req) {return error(ex.getStatusCode().value(),ex.getReason(),req);}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex,HttpServletRequest req) {
        return error(400,String.join("; ",ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()+": "+e.getDefaultMessage()).toList()),req);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> constraint(ConstraintViolationException ex,HttpServletRequest req) {
        return error(400,String.join("; ",ex.getConstraintViolations().stream().map(e->e.getPropertyPath()+": "+e.getMessage()).toList()),req);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.bind.MissingRequestHeaderException.class,org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    public ResponseEntity<?> malformed(Exception ex,HttpServletRequest req) {return error(400,"Malformed request; check field names, types and required parameters",req);}
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> conflict(Exception ex,HttpServletRequest req) {return error(409,"Record conflicts with an existing ID or database constraint",req);}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> large(Exception ex,HttpServletRequest req) {return error(413,"Upload exceeds 20 MB",req);}
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(Exception ex,HttpServletRequest req) {return error(403,"You do not have permission to access this resource",req);}
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> unauthorized(Exception ex,HttpServletRequest req) {return error(401,"Invalid authentication credentials",req);}
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex,HttpServletRequest req) {
        log.error("Request failed: {} {} ({})",req.getMethod(),req.getRequestURI(),ex.getClass().getSimpleName());
        return error(500,"An unexpected error occurred",req);
    }
    private ResponseEntity<?> error(int status,String message,HttpServletRequest req) {
        return ResponseEntity.status(status).body(Map.of("status",status,"error",HttpStatus.valueOf(status).getReasonPhrase(),"message",message==null?"Request failed":message,"path",req.getRequestURI(),"timestamp",Instant.now().toString()));
    }
}
