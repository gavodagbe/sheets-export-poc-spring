package com.example.sheetsexport.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Permet au front de savoir s'il y a déjà une session Google (pour adapter le libellé du bouton).
 */
@RestController
public class SessionController {

    @GetMapping("/api/session")
    public Map<String, Object> session(@AuthenticationPrincipal OAuth2User user) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean authenticated = user != null;
        body.put("authenticated", authenticated);
        body.put("email", authenticated ? user.getAttribute("email") : null);
        return body;
    }
}
