package com.example.eventflow;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving static pages
 */
@Controller
public class PageController {

    /**
     * Serve the main index page
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}