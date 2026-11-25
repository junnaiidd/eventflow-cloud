package com.example.eventflow.service;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The college's departments and event categories. They come from configuration (app.college.*), so no
 * database table is needed; an event's existing "type" column stores its category.
 */
@Component
public class CollegeCatalog {

    private final String collegeName;
    private final List<String> departments;
    private final List<String> categories;
    private final int maxYear;

    public CollegeCatalog(@Value("${app.college.name:Your College}") String collegeName,
            @Value("${app.college.departments}") List<String> departments,
            @Value("${app.college.categories}") List<String> categories,
            @Value("${app.college.max-year:4}") int maxYear) {
        this.collegeName = collegeName;
        this.departments = departments.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.categories = categories.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.maxYear = maxYear;
    }

    public String collegeName() { return collegeName; }
    public List<String> departments() { return departments; }
    public List<String> categories() { return categories; }
    public List<Integer> years() { return IntStream.rangeClosed(1, maxYear).boxed().toList(); }
    public boolean isDepartment(String value) { return value != null && departments.contains(value); }
    public boolean isCategory(String value) { return value != null && categories.contains(value); }
    public boolean isYear(Integer year) { return year != null && year >= 1 && year <= maxYear; }
}
