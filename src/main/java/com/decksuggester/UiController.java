package com.decksuggester;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping("/")
    public String root() {
        return "redirect:/ui";
    }

    @GetMapping({"/ui", "/ui/"})
    public String ui() {
        return "forward:/ui/index.html";
    }

    /** Serves the SPA shell for deep links; paths with a dot (static assets) are excluded. */
    @GetMapping("/ui/{path:[^\\.]*}")
    public String uiRoute() {
        return "forward:/ui/index.html";
    }

    @GetMapping("/ui/decks/{id:[^\\.]*}")
    public String uiDeckRoute() {
        return "forward:/ui/index.html";
    }

}
