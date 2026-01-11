package com.moviehub.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviehub.exceptions.ValidationException;
import com.moviehub.model.Movie;
import com.moviehub.storage.MovieRepository;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MovieRepository repo = new MovieRepository();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();

            if (method.equalsIgnoreCase("GET")) {
                handleGet(ex);
            } else if (method.equalsIgnoreCase("POST")) {
                handlePost(ex);
            } else {
                sendJson(ex, 405, "{\"error\":\"Такого метода не существует\"}");
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"Внутренняя ошибка сервера\"}");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        try {
            requireJsonContentType(ex);
        } catch (IllegalStateException e) {
            return;
        }

        String body = readBody(ex);

        String title;
        int year;

        try {
            title = extractTitle(body);
            year = extractYear(body);
        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, """
                    {
                    "error": "Неверный JSON"
                    }
                    """);
            return;
        }

        try {
            validateMovie(title, year);
        } catch (ValidationException e) {
            String details = e.getErrors().stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(Collectors.joining(", "));

            sendJson(ex, 422, """
                    {
                    "error": "Ошибка валидации",
                    "details": [%s]
                    }
                    """.formatted(details));
            return;
        }

        Movie movie = new Movie();
        movie.title = title;
        movie.year = year;

        Movie saved = repo.add(movie);

        String json = String.format(
                "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                saved.id, saved.title, saved.year
        );

        sendJson(ex, 201, json);
    }

    protected String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    protected void requireJsonContentType(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null || !contentType.equalsIgnoreCase("application/json")) {
            sendJson(ex, 415, """
                    {
                    "error": "Этот тип данных не поддерживается"
                    }
                    """);
            throw new IllegalStateException();
        }
    }

    private String extractTitle(String body) throws IllegalArgumentException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            if (!node.has("title") || node.get("title").isNull()) {
                throw new IllegalArgumentException("нет названия");
            }
            return node.get("title").asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("title");
        }
    }

    private int extractYear(String body) throws IllegalArgumentException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            if (!node.has("year") || node.get("year").isNull()) {
                throw new IllegalArgumentException("нет года");
            }
            return node.get("year").asInt();
        } catch (Exception e) {
            throw new IllegalArgumentException("year");
        }
    }

    private void validateMovie(String title, int year) throws ValidationException {
        List<String> errors = new ArrayList<>();

        if (title == null || title.isBlank()) {
            errors.add("название не должно быть пустым");
        }

        if (title != null && title.length() > 100) {
            errors.add("название не должно превышать 100 символов");
        }

        int maxYear = java.time.Year.now().getValue() + 1;

        if (year < 1888 || year > maxYear) {
            errors.add("год должен быть между 1888 и " + maxYear);
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        String json = repo.findAll().stream()
                .map(m -> String.format(
                        "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                        m.id, m.title, m.year))
                .collect(Collectors.joining(",", "[", "]"));
        sendJson(ex, 200, json);
    }

}
