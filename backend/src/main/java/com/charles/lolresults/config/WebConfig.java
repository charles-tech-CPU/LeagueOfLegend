package com.charles.lolresults.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origines autorisees a appeler l'API. Par defaut le serveur de dev Vite
 * (port 5173) quel que soit l'hote : localhost, 127.0.0.1 ou une IP du reseau
 * local, puisque le front appelle l'API via window.location.hostname.
 * A surcharger via la variable d'environnement CORS_ALLOWED_ORIGINS
 * (liste separee par des virgules, motifs acceptes).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
