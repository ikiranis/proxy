package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.apps4net.proxy.services.ProxyService;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;

/**
 * REST controller responsible for handling HTTP requests to the proxy server.
 * 
 * This controller provides endpoints for:
 * - Forwarding HTTP requests to connected proxy clients
 * - Server health check for monitoring and client validation
 * 
 * The controller validates client connections before forwarding requests and
 * returns appropriate JSON responses for success and error cases.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@RestController
public class GeneralController {
    private final ProxyService proxyService;

    /**
     * Constructs a new GeneralController with the specified ProxyService.
     * 
     * @param proxyService the service that handles proxy operations and client management
     */
    @Autowired
    public GeneralController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Forwards HTTP requests to connected proxy clients.
     * 
     * This endpoint accepts JSON-formatted proxy requests and forwards them to the
     * specified client. It performs connection validation before forwarding and handles
     * response parsing including HTTP headers and base64-encoded binary content.
     * 
     * Response handling supports:
     * - Plain text responses
     * - Structured responses with HTTP headers
     * - Base64-encoded binary content (e.g., PDFs, images)
     * 
     * @param proxyRequest the request to forward, containing client name, HTTP method, URL, and body
     * @return ResponseEntity containing the response from the target client, or error information
     *         - 200: Successful response from client
     *         - 404: Client not connected
     *         - 500: Internal server error during forwarding
     * 
     * @see ProxyRequest
     * @see ProxyResponse
     */
    @PostMapping(path = "/api/forward", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> forwardToClient(@RequestBody ProxyRequest proxyRequest) {
        // Check if client is connected before forwarding the request
        String clientName = proxyRequest.getClientName();
        if (!proxyService.isClientConnected(clientName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"Client not connected\", \"clientName\": \"" + clientName + "\"}");
        }
        
        try {
            ProxyResponse response = proxyService.forwardToClient(proxyRequest);
            // If the response body contains headers and base64 body, parse and set headers
            if (response.getBody() != null && response.getBody().startsWith("Headers:\n")) {
                String[] parts = response.getBody().split("\\nBody-Base64:\\n", 2);
                if (parts.length == 2) {
                    String headersBlock = parts[0].replaceFirst("Headers:\\n", "");
                    String base64Body = parts[1];
                    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
                    for (String headerLine : headersBlock.split("\\n")) {
                        int idx = headerLine.indexOf(": ");
                        if (idx > 0) {
                            String headerName = headerLine.substring(0, idx);
                            String headerValue = headerLine.substring(idx + 2);
                            builder.header(headerName, headerValue);
                        }
                    }
                    // Decode base64 body
                    byte[] bodyBytes = java.util.Base64.getDecoder().decode(base64Body);
                    return builder.body(bodyBytes);
                }
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            if (e.getMessage().contains("Client not connected")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"error\": \"Client not connected\", \"message\": \"" + e.getMessage() + "\"}");
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Provides server health check information for monitoring and client validation.
     * 
     * This endpoint returns comprehensive health information including:
     * - Server status (healthy/unhealthy based on client connections)
     * - Current timestamp
     * - Socket server availability
     * - Number of connected clients
     * - Server version information
     * 
     * The server is considered unhealthy if no proxy clients are connected,
     * as this means the proxy service cannot fulfill its primary function.
     * 
     * This endpoint can be used by:
     * - Monitoring systems to check server health
     * - Proxy clients to verify server availability before connecting
     * - Load balancers for health checks
     * - Administrative tools for status monitoring
     * 
     * @return ResponseEntity containing health check information in JSON format
     *         - 200: Server is healthy and has connected clients
     *         - 503: Server is unhealthy (no clients connected or experiencing issues)
     * 
     * @since 1.0
     */
    @GetMapping(path = "/api/health", produces = "application/json")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            int connectedClients = proxyService.getConnectedClientCount();
            
            // Basic server information
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", "1.0");
            healthInfo.put("socketServerRunning", true); // Socket server is started in ProxyService constructor
            healthInfo.put("connectedClients", connectedClients);
            healthInfo.put("uptime", getServerUptime());
            
            // Check if any clients are connected
            if (connectedClients == 0) {
                healthInfo.put("status", "unhealthy");
                healthInfo.put("serviceStatus", "no-clients");
                healthInfo.put("message", "Server is running but no proxy clients are connected");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(healthInfo);
            }
            
            // Server is healthy with connected clients
            healthInfo.put("status", "healthy");
            healthInfo.put("serviceStatus", "operational");
            healthInfo.put("message", "Server is running normally and ready to accept connections");
            
            return ResponseEntity.ok(healthInfo);
            
        } catch (Exception e) {
            // If any health check fails, return unhealthy status
            healthInfo.put("status", "unhealthy");
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", "1.0");
            healthInfo.put("error", e.getMessage());
            healthInfo.put("message", "Server is experiencing issues");
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(healthInfo);
        }
    }

    /**
     * Gets the server uptime information.
     * 
     * This method calculates how long the server has been running since startup.
     * It provides a simple uptime metric for monitoring purposes.
     * 
     * @return a string describing the server uptime
     */
    private String getServerUptime() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
    
    // Track server start time for uptime calculation
    private static final long startTime = System.currentTimeMillis();
}
