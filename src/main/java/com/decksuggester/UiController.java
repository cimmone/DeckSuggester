package com.decksuggester;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping({"/ui", "/ui/"})
    public String ui() {
        return "forward:/ui/index.html";
    }

    /**
     * Deep links into the single-page app (for example the per-deck editor at
     * {@code /ui/decks/{uuid}}) must serve the SPA shell so the client-side
     * router can render the requested view. Static asset paths (which contain a
     * dot, e.g. {@code /ui/assets/app.js}) are excluded so they resolve to the
     * real files rather than the HTML shell.
     */
    @GetMapping("/ui/{path:[^\\.]*}")
    public String uiRoute() {
        return "forward:/ui/index.html";
    }

    @GetMapping("/ui/decks/{id:[^\\.]*}")
    public String uiDeckRoute() {
        return "forward:/ui/index.html";
    }

}
