package com.example.eventflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.eventflow.service.CollegeCatalog;

/** Public lookup data for forms: college name, departments, event categories, years of study. */
@RestController
public class MetaController {

    public record Meta(String collegeName, List<String> departments, List<String> categories, List<Integer> years) {
    }

    private final CollegeCatalog catalog;

    public MetaController(CollegeCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/meta")
    public Meta meta() {
        return new Meta(catalog.collegeName(), catalog.departments(), catalog.categories(), catalog.years());
    }
}
