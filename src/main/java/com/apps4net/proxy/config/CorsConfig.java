package com.apps4net.proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) configuration for the proxy server.
 * 
 * This configuration allows web applications from different domains to make
 * requests to the proxy server's REST API endpoints. It's essential for
 * JavaScript applications running in browsers that need to communicate with
 * the proxy server.
 * 
 * The configuration includes:
 * - Allowed origins (domains that can make requests)
 * - Allowed HTTP methods (GET, POST, PUT, DELETE, etc.)
 * - Allowed headers for requests
 * - Credential support for authenticated requests
 * - Preflight request caching
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Configures CORS mappings for all endpoints.
     * 
     * This method defines global CORS rules that apply to all REST API endpoints.
     * It allows requests from any origin during development, but should be
     * restricted to specific domains in production for security.
     * 
     * @param registry the CORS registry to configure
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*") // Allow all origins (use specific domains in production)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // Cache preflight response for 1 hour
    }

    /**
     * Creates a CORS configuration source bean for more fine-grained control.
     * 
     * This bean provides an alternative way to configure CORS that can be used
     * with Spring Security if needed. It offers the same permissive configuration
     * as the addCorsMappings method but as a reusable bean.
     * 
     * @return configured CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow all origins (restrict in production)
        configuration.addAllowedOriginPattern("*");
        
        // Allow common HTTP methods
        configuration.addAllowedMethod("GET");
        configuration.addAllowedMethod("POST");
        configuration.addAllowedMethod("PUT");
        configuration.addAllowedMethod("DELETE");
        configuration.addAllowedMethod("OPTIONS");
        configuration.addAllowedMethod("HEAD");
        
        // Allow all headers
        configuration.addAllowedHeader("*");
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Cache preflight requests for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        
        return source;
    }
}
