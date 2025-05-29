package com.apps4net.proxy.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Utility class for admin authentication and authorization.
 * 
 * This class provides methods to validate admin API keys and check
 * authorization for administrative endpoints.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@Component
public class AdminAuthUtils {
    
    @Value("${proxy.admin.api.key}")
    private String adminApiKey;
    
    /**
     * Validates an admin API key against the configured admin key.
     * 
     * @param providedKey the API key provided in the request
     * @return true if the key is valid, false otherwise
     */
    public boolean isValidAdminKey(String providedKey) {
        if (providedKey == null || providedKey.trim().isEmpty()) {
            return false;
        }
        
        if (adminApiKey == null || adminApiKey.trim().isEmpty()) {
            return false;
        }
        
        return adminApiKey.equals(providedKey.trim());
    }
    
    /**
     * Extracts the API key from an Authorization header.
     * 
     * Supports the following formats:
     * - "Bearer {key}"
     * - "ApiKey {key}"
     * - "{key}" (raw key)
     * 
     * @param authHeader the Authorization header value
     * @return the extracted API key, or null if the header is invalid
     */
    public String extractApiKeyFromHeader(String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = authHeader.trim();
        
        // Handle "Bearer {key}" format
        if (trimmed.toLowerCase().startsWith("bearer ")) {
            return trimmed.substring(7).trim();
        }
        
        // Handle "ApiKey {key}" format
        if (trimmed.toLowerCase().startsWith("apikey ")) {
            return trimmed.substring(7).trim();
        }
        
        // Handle raw key
        return trimmed;
    }
    
    /**
     * Checks if the current request is authorized for admin operations.
     * 
     * @param authHeader the Authorization header from the request
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorizedAdmin(String authHeader) {
        String apiKey = extractApiKeyFromHeader(authHeader);
        return isValidAdminKey(apiKey);
    }
}
