package com.moviehub.handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class MoviesHandler extends BaseHttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (method.equalsIgnoreCase("GET")) {
            // Напишите реализацию с использованием метода sendJson
            sendJson(ex, 200, "[]");
        }
    }
}
