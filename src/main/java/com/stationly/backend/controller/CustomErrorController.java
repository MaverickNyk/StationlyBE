package com.stationly.backend.controller;

import com.stationly.backend.model.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ErrorResponse> handleErrorJson(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = HttpStatus.NOT_FOUND.value();

        if (status != null) {
            statusCode = Integer.parseInt(status.toString());
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(statusCode)
                .error(HttpStatus.valueOf(statusCode).getReasonPhrase())
                .message("Oops! The resource you are looking for does not exist on Mind The Time.")
                .path(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI).toString())
                .build();

        return new ResponseEntity<>(error, HttpStatus.valueOf(statusCode));
    }

    @RequestMapping(value = "/error", produces = MediaType.TEXT_HTML_VALUE)
    public String handleErrorHtml(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String code = "404";
        if (status != null) {
            code = status.toString();
        }

        return "<html>" +
                "<head><title>Mind The Time - Error</title>" +
                "<style>" +
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8f9fa; color: #333; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }"
                +
                ".container { text-align: center; padding: 40px; background: white; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); max-width: 500px; }"
                +
                "h1 { color: #dc3545; font-size: 80px; margin: 0; }" +
                "p { font-size: 18px; color: #666; }" +
                "a { display: inline-block; margin-top: 20px; padding: 12px 24px; background-color: #007bff; color: white; text-decoration: none; border-radius: 6px; font-weight: bold; }"
                +
                "a:hover { background-color: #0056b3; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='container'>" +
                "<h1>" + code + "</h1>" +
                "<h2>Oops! Lost in London?</h2>" +
                "<p>We couldn't find the page or endpoint you're looking for on <strong>Mind The Time</strong>.</p>" +
                "<a href='/swagger-ui.html'>Explore API Docs</a>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
