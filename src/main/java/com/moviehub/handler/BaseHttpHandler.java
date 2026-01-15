package com.moviehub.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8"; // !!! Укажите содержимое заголовка Content-Type

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws java.io.IOException {
        ex.sendResponseHeaders(204, -1);
    }

    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        String json = """
                {
                "error": "%s"
                }
                """.formatted(message);

        sendJson(ex, status, json);
    }

    protected void sendValidationError(HttpExchange ex, List<String> details) throws IOException {
        String joined = details.stream()
                .map(d -> "\"" + d + "\"")
                .collect(Collectors.joining(", "));

        String json = """
                {
                "error": "Ошибка валидации",
                "details": [%s]
                }
                """.formatted(joined);

        sendJson(ex, 422, json);
    }
}
