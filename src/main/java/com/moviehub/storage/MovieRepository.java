package com.moviehub.storage;

import com.moviehub.model.Movie;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class MovieRepository {
    private final Map<Long, Movie> movies = new LinkedHashMap<>();
    private long nextId = 1;

    public Movie add(Movie movie) {
        movie.id = nextId++;
        movies.put(movie.id, movie);
        return movie;
    }

    public Collection<Movie> findAll() {
        return movies.values();
    }
}
