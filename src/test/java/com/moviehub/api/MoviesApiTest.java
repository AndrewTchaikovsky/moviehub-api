package com.moviehub.api;

import com.moviehub.handler.MoviesHandler;
import com.moviehub.server.MoviesServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {
    private static final String BASE = "http://localhost:8080"; // !!! добавьте базовую часть URL
    private static MoviesServer server;
    private static HttpClient client;

    private HttpResponse<String> post(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithWrongContentType(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain") // ставим неверный Content-Type
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithoutContentType(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                // намеренно пропускаем Content-Type
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

    }

    @BeforeEach
    void clearRepository() {
        MoviesHandler.getRepository().clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies")) // !!! Добавьте правильный URI
                .GET()
                .build();

        HttpResponse<String> resp =
                client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void postMovie_thenGetMovies_returnsMovies() throws Exception {
        String json = """
                {
                "title": "Начало",
                "year": 2010
                }
                """;

        HttpResponse<String> postResp = post(json);

        assertEquals(201, postResp.statusCode());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> respGet = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, respGet.statusCode());
        assertTrue(respGet.body().contains("Начало"));
    }


    @Test
    void postMovie_whenTitleIsEmpty_return422() throws Exception {
        String json = """
                        {
                        "title": "",
                        "year": 2010
                        }
                """;

        HttpResponse<String> resp = post(json);

        assertEquals(422, resp.statusCode(), "Пустое название должно приводить к 422");
    }

    @Test
    void postMovie_whenTitleIsTooLong_return422() throws Exception {
        String longTitle = "A".repeat(101);

        String json = String.format("""
                        {
                        "title": "%s",
                        "year": 2010
                        }
                """, longTitle);

        HttpResponse<String> resp = post(json);

        assertEquals(422, resp.statusCode(), "Название длиннее 100 символов должно приводить к 422");
    }

    @Test
    void postMovie_whenYearIsTooEarly_return422() throws Exception {
        String json = """
                        {
                        "title": "Начало",
                        "year": 1800
                        }
                """;

        HttpResponse<String> resp = post(json);

        assertEquals(422, resp.statusCode(), "Год меньше 1888 должен приводить к 422");
    }

    @Test
    void postMovie_whenYearIsInFuture_return422() throws Exception {
        int futureYear = java.time.Year.now().getValue() + 2;

        String json = String.format("""
                        {
                        "title": "Начало",
                        "year": %d
                        }
                """, futureYear);

        HttpResponse<String> resp = post(json);

        assertEquals(422, resp.statusCode(), "Год больше текущего года + 1 должен приводить к 422");
    }

    @Test
    void postMovie_whenContentTypeIsNotJson_return415() throws Exception {
        String json = """
                        {
                        "title": "Начало",
                        "year": 2010
                        }
                """;

        HttpResponse<String> resp = postWithWrongContentType(json);

        assertEquals(415, resp.statusCode(), "Неверный Content-Type должен приводить к 415");
    }

    @Test
    void postMovie_whenJsonIsMalformed_return400() throws Exception {
        String badJson = """
                        {
                        "title": "Начало",
                        "year": 2010"
                """; // намеренно пропускаем закрывающуюся скобку, чтобы формат json был неверным

        HttpResponse<String> resp = post(badJson);

        assertEquals(400, resp.statusCode(), "Некорректный JSON должен приводить к 400");
    }

    @Test
    void postMovie_withoutJsonContentType_return415() throws Exception {
        String json = """
                {
                "title": "Начало",
                "year": 2010
                }
                """;

        HttpResponse<String> resp = postWithoutContentType(json);

        assertEquals(415, resp.statusCode(), "Отсутствующий Content-Type должен приводить к 415");
    }

    @Test
    void postMovie_whenMultipleErrors_returnAllDetails() throws Exception {
        String json = """
                {
                "title": "",
                "year": 3000
                }
                """;

        HttpResponse<String> resp = post(json);

        int maxYear = java.time.Year.now().getValue() + 1;

        assertEquals(422, resp.statusCode());
        assertTrue(resp.body().contains("название не должно быть пустым"));
        assertTrue(resp.body().contains("год должен быть между 1888 и " + maxYear));
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        String json = """
                        {
                        "title": "Матрица",
                        "year": 1999
                        }
                """;

        HttpResponse<String> postResp = post(json);
        assertEquals(201, postResp.statusCode());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Матрица"));
        assertTrue(resp.body().contains("\"id\":1"));
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void deleteMovieById_whenExists_return204() throws Exception {
        String json = """
                        {
                        "title": "Терминатор",
                        "year": 1984
                        }
                """;

        HttpResponse<String> postResp = post(json);
        assertEquals(201, postResp.statusCode());

        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .DELETE()
                .build();

        HttpResponse<String> deleteResp = client.send(delete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, deleteResp.statusCode());

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/1"))
                .GET()
                .build();

        HttpResponse<String> getResp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResp.statusCode());
    }

    @Test
    void deleteMovieById_whenNotFound_returns404() throws Exception {
        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(delete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Фильм не найден"));
    }

    @Test
    void deleteMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest delete = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();

        HttpResponse<String> resp = client.send(delete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный ID"));
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        post("""
                {
                "title": "Матрица",
                "year": 1999
                }
                """);

        post("""
                {
                "title": "Начало",
                "year": 2010
                }
                """);

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2010"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Начало"));
        assertTrue(resp.body().contains("2010"));
        assertFalse(resp.body().contains("Матрица"));
    }

    @Test
    void getMoviesByYear_whenNoMovies_returnsEmptyArray() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2050"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Некорректный параметр запроса"));
    }

    @Test
    void getMovieByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("year"));
    }

    @Test
    void getMoviesByYear_whenYearIsEmpty_returns400() throws Exception {
        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year="))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode());
    }

    @Test
    void getMovieByYear_whenUnknownQueryParam_IgnoresIt() throws Exception {
        post("""
                {
                "title": "Начало",
                "year": 2010
                }
                """);

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?notyear=2010"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Начало"));
    }

    @Test
    void getMoviesByYear_whenNoMoviesFound_returnsEmptyArray() throws Exception {
        post("""
                {
                "title": "Начало",
                "year": 2010
                }
                """);

        HttpRequest get = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2000"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body());
    }

}
