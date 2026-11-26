package com.trading.identity_service.controller;

import com.trading.identity_service.service.JwtService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Publishes the public signing key(s). The gateway will read this to verify tokens. */
@RestController
public class JwksController {

    private final JwtService jwtService;

    public JwksController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtService.publicJwks();
    }
}
