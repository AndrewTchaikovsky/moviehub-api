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

    public Movie findById(Long id) {
        return movies.get(id);
    }

    public boolean deleteById(Long id) {
        return movies.remove(id) != null;
    }

    public Collection<Movie> findByYear(int year) {
        return movies.values().stream()
                .filter(m -> m.year == year)
                .toList();
    }
}
