package com.moviehub.handler;

import com.moviehub.model.Movie;
import com.moviehub.storage.MovieRepository;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MovieRepository repo =  new MovieRepository();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();

            if (method.equalsIgnoreCase("GET")) {
                handleGet(ex);
            } else if (method.equalsIgnoreCase("POST")) {
                handlePost(ex);
            } else {
                sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"Internal Server Error\"}");
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (!body.contains("\"title\"") || !body.contains("\"year\"")) {
            sendJson(ex, 422, "{\"error\":\"Invalid JSON\"}");
            return;
        }

        String title = body.split("\"title\"\\s*:\\s*\"")[1].split("\"")[0];

        if (title.isBlank() || title.length() > 100) {
            sendJson(ex, 422, """
                    {
                    "error": "Ошибка валидации",
                    "details": ["название не должно быть пустым или длиннее 100 символов"]
                    }
                    """);
            return;
        }

        int year = Integer.parseInt(body.split("\"year\"\\s*:\\s*")[1].split("[^0-9]")[0]);

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

    private void handleGet(HttpExchange ex) throws IOException {
        String json = repo.findAll().stream()
                .map(m -> String.format(
                        "{\"id\":%d,\"title\":\"%s\",\"year\":%d}",
                        m.id, m.title, m.year))
                .collect(Collectors.joining(",", "[", "]"));
        sendJson(ex, 200, json);
    }

}
