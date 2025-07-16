package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.apps4net.proxy.services.ProxyService;
import com.apps4net.proxy.services.ClientHandlerService;
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
    private final com.apps4net.proxy.utils.ConnectionLogger connectionLogger;
    private static final String PROJECT_VERSION = "1.4";

    /**
     * Constructs a new GeneralController with the specified services.
     * 
     * @param proxyService the service that handles proxy operations and client management
     * @param adminAuthUtils the utility for admin authentication and authorization
     * @param connectionLogger the connection logger for tracking client connections
     */
    public GeneralController(ProxyService proxyService, AdminAuthUtils adminAuthUtils, com.apps4net.proxy.utils.ConnectionLogger connectionLogger) {
        this.proxyService = proxyService;
        this.adminAuthUtils = adminAuthUtils;
        this.connectionLogger = connectionLogger;
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
     * - List of connected client names
     * - Detailed client information including connection start times and uptime
     * - Server version information
     * - Automatic cleanup status information
     * 
     * The server is considered unhealthy if no proxy clients are connected,
     * as this means the proxy service cannot fulfill its primary function.
     * 
     * Note: Connection cleanup is now performed automatically every minute via
     * a scheduled background task, so this endpoint provides current status
     * without triggering additional cleanup operations.
     * 
     * Client details include:
     * - Client name
     * - Connection start time (ISO format)
     * - Human-readable uptime duration
     * - Connection status
     * - Connection status
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
            // Note: Automatic cleanup now runs every minute via scheduled task
            // No need to perform cleanup on every health check
            
            int connectedClients = proxyService.getConnectedClientCount();
            java.util.List<String> connectedClientNames = proxyService.getConnectedClientNames();
            java.util.List<Map<String, Object>> clientDetails = proxyService.getConnectedClientsDetails();
            
            // Basic server information
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", PROJECT_VERSION);
            healthInfo.put("socketServerRunning", true); // Socket server is started in ProxyService constructor
            healthInfo.put("connectedClients", connectedClients);
            healthInfo.put("connectedClientNames", connectedClientNames);
            healthInfo.put("clientDetails", clientDetails);
            healthInfo.put("automaticCleanup", "Enabled (runs every 60 seconds)");
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
            securityInfo.put("bannedIPs", ClientHandlerService.getBannedIPs());
            securityInfo.put("suspiciousAttempts", ClientHandlerService.getSuspiciousAttempts());
            securityInfo.put("bannedIPCount", ClientHandlerService.getBannedIPs().size());
            
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
                    ClientHandlerService.banIP(ip.trim());
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
                    Map<String, Object> preBanStatus = ClientHandlerService.checkAutoBanStatus(ip.trim());
                    
                    boolean wasUnbanned = ClientHandlerService.unbanIP(ip.trim());
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
                    response.put("bannedIPs", ClientHandlerService.getBannedIPs());
                    response.put("suspiciousAttempts", ClientHandlerService.getSuspiciousAttempts());
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
                    response.put("isBanned", ClientHandlerService.getBannedIPs().contains(ip.trim()));
                    response.put("autoBanStatus", ClientHandlerService.checkAutoBanStatus(ip.trim()));
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

    /**
     * Manually triggers cleanup of unhealthy client connections.
     * 
     * This endpoint removes zombie connections that appear connected but are actually broken,
     * helping to maintain accurate connection state and prevent timeout issues.
     * 
     * @param authHeader the authorization header for admin authentication
     * @return response containing the number of connections cleaned up and detailed client information
     */
    @PostMapping("/api/cleanup-connections")
    public ResponseEntity<Map<String, Object>> cleanupConnections(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Admin authentication check
            if (!adminAuthUtils.isAuthorizedAdmin(authHeader)) {
                response.put("error", "Unauthorized");
                response.put("message", "Admin authentication required");
                return ResponseEntity.status(401).body(response);
            }
            
            // Perform connection cleanup
            int removedCount = proxyService.cleanupUnhealthyConnections();
            
            response.put("success", true);
            response.put("removedConnections", removedCount);
            response.put("remainingConnections", proxyService.getConnectedClientCount());
            response.put("connectedClients", proxyService.getConnectedClientNames());
            response.put("clientDetails", proxyService.getConnectedClientsDetails());
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", "Cleanup failed");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Retrieves connection logs for monitoring client connections and disconnections.
     * 
     * This endpoint provides comprehensive connection logging information including:
     * - All connection and disconnection events with timestamps
     * - Client names and IP addresses
     * - Connection statistics and summaries
     * - Optional filtering by event type, client name, or time range
     * 
     * The endpoint supports several query parameters for filtering:
     * - eventType: Filter by "CONNECT" or "DISCONNECT" events
     * - clientName: Filter logs for a specific client
     * - limit: Limit the number of results returned
     * 
     * This is useful for:
     * - Monitoring client connection patterns
     * - Debugging connection issues
     * - Auditing client access
     * - Tracking system usage and health
     * 
     * Authentication Required: This endpoint requires a valid admin API key in the Authorization header.
     * 
     * @param authorization the Authorization header containing the admin API key
     * @param eventType optional filter for event type ("CONNECT" or "DISCONNECT")
     * @param clientName optional filter for specific client name
     * @param limit optional limit for number of results (default: all)
     * @return ResponseEntity containing connection logs and statistics in JSON format
     *         - 200: Logs retrieved successfully
     *         - 401: Unauthorized (invalid or missing admin API key)
     *         - 500: Internal server error during log retrieval
     * 
     * @since 1.2
     */
    @GetMapping(path = "/api/admin/connection-logs", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getConnectionLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "clientName", required = false) String clientName,
            @RequestParam(value = "limit", required = false) Integer limit) {
        
        Map<String, Object> response = new HashMap<>();
        
        // Check admin authentication first
        if (!adminAuthUtils.isAuthorizedAdmin(authorization)) {
            response.put("error", "Unauthorized");
            response.put("message", "Valid admin API key required in Authorization header");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        try {
            java.util.List<Map<String, Object>> logs;
            
            // Apply filters based on query parameters
            if (clientName != null && !clientName.trim().isEmpty()) {
                logs = connectionLogger.getLogsForClient(clientName.trim());
            } else if (eventType != null && !eventType.trim().isEmpty()) {
                String normalizedEventType = eventType.trim().toUpperCase();
                if ("CONNECT".equals(normalizedEventType) || "DISCONNECT".equals(normalizedEventType)) {
                    logs = connectionLogger.getLogsByEventType(normalizedEventType);
                } else {
                    response.put("error", "Invalid event type");
                    response.put("message", "Event type must be 'CONNECT' or 'DISCONNECT'");
                    response.put("validEventTypes", java.util.Arrays.asList("CONNECT", "DISCONNECT"));
                    return ResponseEntity.badRequest().body(response);
                }
            } else {
                logs = connectionLogger.getAllLogs();
            }
            
            // Apply limit if specified
            if (limit != null && limit > 0 && logs.size() > limit) {
                // Take the most recent entries
                int startIndex = Math.max(0, logs.size() - limit);
                logs = logs.subList(startIndex, logs.size());
            }
            
            // Get connection statistics
            Map<String, Object> statistics = connectionLogger.getConnectionStatistics();
            
            // Build response
            response.put("success", true);
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.put("logs", logs);
            response.put("statistics", statistics);
            response.put("totalReturned", logs.size());
            response.put("filters", createFiltersMap(eventType, clientName, limit));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", "Failed to retrieve connection logs");
            response.put("message", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Clears all connection logs.
     * 
     * This endpoint removes all stored connection log entries. Use with caution
     * as this action cannot be undone.
     * 
     * Authentication Required: This endpoint requires a valid admin API key in the Authorization header.
     * 
     * @param authorization the Authorization header containing the admin API key
     * @return ResponseEntity containing the operation result
     *         - 200: Logs cleared successfully
     *         - 401: Unauthorized (invalid or missing admin API key)
     *         - 500: Internal server error during log clearing
     * 
     * @since 1.2
     */
    @PostMapping(path = "/api/admin/connection-logs/clear", produces = "application/json")
    public ResponseEntity<Map<String, Object>> clearConnectionLogs(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        
        Map<String, Object> response = new HashMap<>();
        
        // Check admin authentication first
        if (!adminAuthUtils.isAuthorizedAdmin(authorization)) {
            response.put("error", "Unauthorized");
            response.put("message", "Valid admin API key required in Authorization header");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        
        try {
            Map<String, Object> statisticsBeforeClear = connectionLogger.getConnectionStatistics();
            connectionLogger.clearLogs();
            
            response.put("success", true);
            response.put("message", "Connection logs cleared successfully");
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            response.put("clearedEntries", statisticsBeforeClear.get("totalLogEntries"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", "Failed to clear connection logs");
            response.put("message", e.getMessage());
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Creates a map containing the applied filters for the response.
     * 
     * @param eventType the event type filter
     * @param clientName the client name filter
     * @param limit the limit filter
     * @return a map containing the filter information
     */
    private Map<String, Object> createFiltersMap(String eventType, String clientName, Integer limit) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("eventType", eventType);
        filters.put("clientName", clientName);
        filters.put("limit", limit);
        return filters;
    }

    /**
     * Checks the health status of a specific proxy client by name.
     * 
     * This endpoint allows clients to perform self-health checks by verifying
     * their connection status with the server. It returns detailed information
     * about whether the client is currently connected and has a healthy socket connection.
     * 
     * The endpoint performs comprehensive health validation including:
     * - Checking if the client exists in the connection pool
     * - Validating socket state (connected, not closed, streams not shutdown)
     * - Verifying the connection is accessible and responsive
     * 
     * This API is designed for:
     * - Client-side heartbeat and health monitoring
     * - Automated reconnection logic
     * - Administrative monitoring of specific clients
     * - Troubleshooting connection issues
     * 
     * @param clientName the name of the client to check (from URL path)
     * @return ResponseEntity containing client health information in JSON format
     *         - 200: Client is connected and healthy
     *         - 404: Client is not connected or not found
     *         - 500: Error occurred while checking client health
     * 
     * @since 1.4
     */
    @GetMapping(path = "/api/health/{clientName}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> checkClientHealth(@PathVariable String clientName) {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            // Set basic response information
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", PROJECT_VERSION);
            healthInfo.put("clientName", clientName);
            
            // Check if the client is connected and healthy
            boolean isConnected = proxyService.isClientConnected(clientName);
            
            if (isConnected) {
                // Client is connected and healthy
                healthInfo.put("status", "healthy");
                healthInfo.put("connected", true);
                healthInfo.put("message", "Client is connected and healthy");
                healthInfo.put("connectionStatus", "active");
                
                return ResponseEntity.ok(healthInfo);
            } else {
                // Client is not connected or unhealthy
                healthInfo.put("status", "disconnected");
                healthInfo.put("connected", false);
                healthInfo.put("message", "Client is not connected or connection is unhealthy");
                healthInfo.put("connectionStatus", "inactive");
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(healthInfo);
            }
            
        } catch (Exception e) {
            // Error occurred while checking client health
            healthInfo.put("status", "error");
            healthInfo.put("connected", false);
            healthInfo.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            healthInfo.put("server", "Proxy Server");
            healthInfo.put("version", PROJECT_VERSION);
            healthInfo.put("clientName", clientName);
            healthInfo.put("error", e.getMessage());
            healthInfo.put("message", "Error occurred while checking client health");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(healthInfo);
        }
    }
}
