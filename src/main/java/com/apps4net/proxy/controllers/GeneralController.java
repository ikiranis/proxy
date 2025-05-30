package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.apps4net.proxy.services.ProxyService;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import com.apps4net.proxy.utils.ServerUtils;
import com.apps4net.proxy.utils.AdminAuthUtils;
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
    private final AdminAuthUtils adminAuthUtils;

    /**
     * Constructs a new GeneralController with the specified ProxyService and AdminAuthUtils.
     * 
     * @param proxyService the service that handles proxy operations and client management
     * @param adminAuthUtils the utility for admin authentication and authorization
     */
    @Autowired
    public GeneralController(ProxyService proxyService, AdminAuthUtils adminAuthUtils) {
        this.proxyService = proxyService;
        this.adminAuthUtils = adminAuthUtils;
    }

    /**
     * Forwards HTTP requests to connected proxy clients.
     * 
     * This endpoint accepts JSON-formatted proxy requests and forwards them to the
     * specified client. It performs connection validation before forwarding and handles
     * response parsing including HTTP headers and base64-encoded binary content.
     * 
     * Authentication Required: This endpoint requires a valid admin API key in the Authorization header.
     * Supported formats:
     * - Authorization: Bearer {admin-api-key}
     * - Authorization: ApiKey {admin-api-key}
     * - Authorization: {admin-api-key}
     * 
     * Response handling supports:
     * - Plain text responses
     * - Structured responses with HTTP headers
     * - Base64-encoded binary content (e.g., PDFs, images)
     * 
     * @param authorization the Authorization header containing the admin API key
     * @param proxyRequest the request to forward, containing client name, HTTP method, URL, and body
     * @return ResponseEntity containing the response from the target client, or error information
     *         - 200: Successful response from client
     *         - 401: Unauthorized (invalid or missing admin API key)
     *         - 404: Client not connected
     *         - 500: Internal server error during forwarding
     * 
     * @see ProxyRequest
     * @see ProxyResponse
     */
    @PostMapping(path = "/api/forward", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> forwardToClient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ProxyRequest proxyRequest) {
        
        // Check admin authentication first
        if (!adminAuthUtils.isAuthorizedAdmin(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Unauthorized\", \"message\": \"Valid admin API key required in Authorization header\", \"timestamp\": \"" + 
                          LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\"}");
        }
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
            java.util.List<String> connectedClientNames = proxyService.getConnectedClientNames();
            
            // Basic server information
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", "1.0");
            healthInfo.put("socketServerRunning", true); // Socket server is started in ProxyService constructor
            healthInfo.put("connectedClients", connectedClients);
            healthInfo.put("connectedClientNames", connectedClientNames);
            healthInfo.put("uptime", ServerUtils.getServerUptime());
            
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
     * Provides security status information including banned IPs and threat statistics.
     * 
     * This endpoint returns information about:
     * - Currently banned IP addresses
     * - Suspicious activity statistics
     * - Threat detection configuration
     * - Recent security events
     * 
     * Authentication Required: This endpoint requires a valid admin API key in the Authorization header.
     * Supported formats:
     * - Authorization: Bearer {admin-api-key}
     * - Authorization: ApiKey {admin-api-key}
     * - Authorization: {admin-api-key}
     * 
     * This endpoint is useful for:
     * - Security monitoring and alerting
     * - Administrative oversight of the threat detection system
     * - Forensic analysis of attack patterns
     * - Validation that the security system is working properly
     * 
     * @param authorization the Authorization header containing the admin API key
     * @return ResponseEntity containing security status information in JSON format
     *         - 200: Security status retrieved successfully
     *         - 401: Unauthorized (invalid or missing admin API key)
     *         - 500: Error retrieving security information
     * 
     * @since 1.0
     */
    @GetMapping(path = "/api/security-status", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getSecurityStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Map<String, Object> securityInfo = new HashMap<>();
        
        // Check admin authentication first
        if (!adminAuthUtils.isAuthorizedAdmin(authorization)) {
            securityInfo.put("error", "Unauthorized");
            securityInfo.put("message", "Valid admin API key required in Authorization header");
            securityInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(securityInfo);
        }
        
        try {
            // Get current security statistics
            securityInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            securityInfo.put("bannedIPs", ClientHandler.getBannedIPs());
            securityInfo.put("suspiciousAttempts", ClientHandler.getSuspiciousAttempts());
            securityInfo.put("bannedIPCount", ClientHandler.getBannedIPs().size());
            
            // Configuration information
            Map<String, Object> config = new HashMap<>();
            config.put("maxAttemptsBeforeBan", 5);
            config.put("timeWindowMinutes", 15);
            config.put("permanentBanThreshold", 15);
            config.put("authFailureTolerance", 8);
            config.put("gracePeriodMinutes", 30);
            securityInfo.put("configuration", config);
            
            // System status
            securityInfo.put("threatDetectionEnabled", true);
            securityInfo.put("autoBlockingEnabled", true);
            securityInfo.put("status", "operational");
            
            return ResponseEntity.ok(securityInfo);
            
        } catch (Exception e) {
            securityInfo.put("status", "error");
            securityInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            securityInfo.put("error", e.getMessage());
            securityInfo.put("message", "Failed to retrieve security status");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(securityInfo);
        }
    }

    /**
     * Administrative endpoint for managing IP bans and authentication security.
     * 
     * This endpoint allows administrators to:
     * - Manually ban/unban IP addresses
     * - View current security status
     * - Override security settings if needed
     * 
     * Authentication Required: This endpoint requires a valid admin API key in the Authorization header.
     * Supported formats:
     * - Authorization: Bearer {admin-api-key}
     * - Authorization: ApiKey {admin-api-key}
     * - Authorization: {admin-api-key}
     * 
     * @param authorization the Authorization header containing the admin API key
     * @param request the request body containing action and optional ip parameter
     * @return ResponseEntity containing the result of the administrative action
     *         - 200: Action completed successfully
     *         - 400: Invalid action or missing parameters
     *         - 401: Unauthorized (invalid or missing admin API key)
     *         - 500: Error performing action
     * 
     * @since 1.0
     */
    @PostMapping(path = "/api/admin/security", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> adminSecurityAction(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, String> request) {
        
        Map<String, Object> response = new HashMap<>();
        
        // Check admin authentication first
        if (!adminAuthUtils.isAuthorizedAdmin(authorization)) {
            response.put("error", "Unauthorized");
            response.put("message", "Valid admin API key required in Authorization header");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        try {
            String action = request.get("action");
            String ip = request.get("ip");
            
            if (action == null) {
                response.put("error", "Missing 'action' parameter");
                response.put("validActions", new String[]{"ban", "unban", "status"});
                return ResponseEntity.badRequest().body(response);
            }
            
            switch (action.toLowerCase()) {
                case "ban":
                    if (ip == null || ip.trim().isEmpty()) {
                        response.put("error", "Missing 'ip' parameter for ban action");
                        return ResponseEntity.badRequest().body(response);
                    }
                    ClientHandler.banIP(ip.trim());
                    response.put("success", true);
                    response.put("message", "IP " + ip + " has been banned");
                    response.put("action", "ban");
                    response.put("ip", ip);
                    break;
                    
                case "unban":
                    if (ip == null || ip.trim().isEmpty()) {
                        response.put("error", "Missing 'ip' parameter for unban action");
                        return ResponseEntity.badRequest().body(response);
                    }
                    
                    // Check auto-ban status before unbanning
                    Map<String, Object> preBanStatus = ClientHandler.checkAutoBanStatus(ip.trim());
                    
                    boolean wasUnbanned = ClientHandler.unbanIP(ip.trim());
                    response.put("success", true);
                    if (wasUnbanned) {
                        response.put("message", "IP " + ip + " has been successfully unbanned and tracking data cleared");
                        response.put("wasActuallyBanned", true);
                    } else {
                        response.put("message", "IP " + ip + " was not in the banned list (may have already been unbanned)");
                        response.put("wasActuallyBanned", false);
                    }
                    response.put("action", "unban");
                    response.put("ip", ip);
                    response.put("preBanStatus", preBanStatus);
                    response.put("note", "All tracking data has been cleared. IP will not be auto-banned unless new suspicious activity occurs.");
                    break;
                    
                case "status":
                    response.put("success", true);
                    response.put("bannedIPs", ClientHandler.getBannedIPs());
                    response.put("suspiciousAttempts", ClientHandler.getSuspiciousAttempts());
                    response.put("action", "status");
                    break;
                    
                case "check":
                    if (ip == null || ip.trim().isEmpty()) {
                        response.put("error", "Missing 'ip' parameter for check action");
                        return ResponseEntity.badRequest().body(response);
                    }
                    response.put("success", true);
                    response.put("action", "check");
                    response.put("ip", ip);
                    response.put("isBanned", ClientHandler.getBannedIPs().contains(ip.trim()));
                    response.put("autoBanStatus", ClientHandler.checkAutoBanStatus(ip.trim()));
                    break;
                    
                default:
                    response.put("error", "Invalid action: " + action);
                    response.put("validActions", new String[]{"ban", "unban", "status", "check"});
                    return ResponseEntity.badRequest().body(response);
            }
            
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
