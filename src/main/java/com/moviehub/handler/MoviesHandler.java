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
    private static final MovieRepository repo = new MovieRepository();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static MovieRepository getRepository() {
        return repo;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();

            if (method.equalsIgnoreCase("GET")) {
                handleGet(ex);
            } else if (method.equalsIgnoreCase("POST")) {
                handlePost(ex);
            } else if (method.equalsIgnoreCase("DELETE")) {
                handleDelete(ex);
            } else {
                ex.getResponseHeaders().set("Allow", "GET,POST,DELETE");
                sendError(ex, 405, "Метод не поддерживается");
            }
        } catch (Exception e) {
            sendError(ex, 500, "Внутренняя ошибка сервера");
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
            sendError(ex, 400, "Неверный JSON");
            return;
        }

        try {
            validateMovie(title, year);
        } catch (ValidationException e) {
            sendValidationError(ex, e.getErrors());
            return;
        }

        Movie movie = new Movie();
        movie.title = title;
        movie.year = year;

        Movie saved = repo.add(movie);

        String json = mapper.writeValueAsString(saved);

        sendJson(ex, 201, json);
    }

    protected String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    protected void requireJsonContentType(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null || !contentType.equalsIgnoreCase("application/json")) {
            sendError(ex, 415, "Неподдерживаемый Content-Type");
            throw new IllegalStateException();
        }
    }

    private String extractTitle(String body) throws IllegalArgumentException {
        try {
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
        String query = ex.getRequestURI().getQuery();

        if (query != null && query.startsWith("year=")) {
            handleGetByYear(ex, query);
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length == 2) {
            handleGetAll(ex);
            return;
        }

        if (parts.length == 3) {
            handleGetById(ex, parts[2]);
            return;
        }

        sendError(ex, 404, "Не найдено");
    }

    private void handleGetAll(HttpExchange ex) throws IOException {
        String json = mapper.writeValueAsString(repo.findAll());
        sendJson(ex, 200, json);
    }

    private void handleGetById(HttpExchange ex, String idStr) throws IOException {
        long id;

        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        Movie movie = repo.findById(id);

        if (movie == null) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }

        String json = mapper.writeValueAsString(movie);

        sendJson(ex, 200, json);
    }

    private void handleGetByYear(HttpExchange ex, String query) throws IOException {
        int year;

        try {
            year = Integer.parseInt(query.substring("year=".length()));
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса - 'year'");
            return;
        }

        String json = mapper.writeValueAsString(repo.findByYear(year));

        sendJson(ex, 200, json);
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length != 3) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }

        long id;
        try {
            id = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        boolean deleted = repo.deleteById(id);

        if (!deleted) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }

        sendNoContent(ex);
    }
}
