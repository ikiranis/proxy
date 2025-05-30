package com.apps4net.proxy.controllers;

import java.net.Socket;
import java.util.Map;
import java.util.HashMap;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import com.apps4net.proxy.utils.Logger;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Handles communication with individual proxy clients connected via socket connections.
 * 
 * This class manages the lifecycle of a single client connection, including:
 * - Client registration and identification
 * - Bidirectional communication for request/response handling
 * - Connection management and cleanup
 * - Synchronized request forwarding to prevent concurrent access issues
 * 
 * Each instance runs in its own thread and maintains object streams for
 * efficient serialized communication with the client.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
public class ClientHandler extends Thread {
    private final Socket socket;
    private final Map<String, ClientHandler> clients;
    private final String requiredAuthToken;
    private String clientName;
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;
    
    // Threat Detection and IP Banning System
    private static final Set<String> bannedIPs = ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicInteger> suspiciousAttempts = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> firstAttemptTime = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> lastAttemptTime = new ConcurrentHashMap<>();
    private static final Map<String, LocalDateTime> recentlyUnbannedIPs = new ConcurrentHashMap<>();
    
    // Threat detection thresholds
    private static final int MAX_ATTEMPTS_BEFORE_BAN = 5;  // Ban after 5 suspicious attempts (increased from 3)
    private static final int TIME_WINDOW_MINUTES = 15;     // Within 15 minutes (increased from 10)
    private static final int PERMANENT_BAN_THRESHOLD = 15; // Permanent ban after 15 total attempts (increased from 10)
    private static final int AUTH_FAILURE_TOLERANCE = 8;   // Allow more auth failures before banning
    private static final int GRACE_PERIOD_MINUTES = 30;    // Grace period after manual unban (30 minutes)

    /**
     * Creates a new ClientHandler for managing communication with a connected client.
     * 
     * @param socket the socket connection to the client
     * @param clients the shared map of all connected clients for registration management
     * @param authToken the required authentication token for client verification
     */
    public ClientHandler(Socket socket, Map<String, ClientHandler> clients, String authToken) {
        this.socket = socket;
        this.clients = clients;
        this.requiredAuthToken = authToken;
    }

    /**
     * Main execution thread for the client handler.
     * 
     * This method:
     * 1. Detects the incoming protocol (HTTP vs Java object serialization)
     * 2. Handles HTTP requests with appropriate error responses
     * 3. Initializes object streams for valid proxy client connections
     * 4. Receives and registers the client name
     * 5. Keeps the connection alive for request/response operations
     * 6. Handles cleanup when the connection is terminated
     * 
     * The actual request/response handling is performed by the
     * {@link #sendRequestAndGetResponse(ProxyRequest)} method.
     */
    public void run() {
        String clientIP = extractClientIP();
        
        Logger.info("=== CLIENT HANDLER STARTED ===");
        Logger.info("Handling connection from: " + clientIP);
        Logger.info("Socket details: " + socket);
        Logger.info("Thread: " + Thread.currentThread().getName());
        Logger.info("Timestamp: " + java.time.LocalDateTime.now());
        
        // SECURITY: Check if IP is banned before processing any requests
        if (isBannedIP(clientIP)) {
            Logger.info("SECURITY: Rejected connection from banned IP: " + clientIP);
            Logger.info("Connection will be closed immediately");
            try { socket.close(); } catch (Exception ignored) {}
            Logger.info("=== CLIENT HANDLER ENDED (BANNED IP) ===");
            return;
        }
        
        Logger.info("IP security check passed for: " + clientIP);
        
        try {
            Logger.info("Initializing object streams for proxy client communication...");
            
            // Set socket timeout for request/response operations
            socket.setSoTimeout(30000); // 30 second timeout for request/response operations
            
            // Initialize object streams directly without protocol detection
            // The ObjectInputStream will naturally throw an exception if HTTP data is sent
            Logger.info("Creating ObjectOutputStream...");
            objectOut = new ObjectOutputStream(socket.getOutputStream());
            Logger.info("Creating ObjectInputStream...");
            objectIn = new ObjectInputStream(socket.getInputStream());
            Logger.info("Object streams created successfully");
            
            // First message from client should be the authentication token
            Logger.info("Waiting for authentication token from client...");
            String clientToken = (String) objectIn.readObject();
            Logger.info("Received authentication token from " + clientIP);
            Logger.info("Token length: " + (clientToken != null ? clientToken.length() : 0) + " characters");
            
            // Verify authentication token
            Logger.info("Validating authentication token...");
            Logger.info("Expected token length: " + (requiredAuthToken != null ? requiredAuthToken.length() : 0));
            Logger.info("Received token preview: " + (clientToken != null ? clientToken.substring(0, Math.min(10, clientToken.length())) + "..." : "null"));
            
            if (requiredAuthToken == null || !requiredAuthToken.equals(clientToken)) {
                Logger.error("SECURITY: Authentication failed from " + clientIP + " - invalid token");
                Logger.error("Expected token: " + (requiredAuthToken != null ? requiredAuthToken.substring(0, Math.min(10, requiredAuthToken.length())) + "..." : "null"));
                Logger.error("Received token: " + (clientToken != null ? clientToken.substring(0, Math.min(10, clientToken.length())) + "..." : "null"));
                recordSuspiciousActivity(clientIP, "AUTHENTICATION_FAILED");
                
                // Send authentication failure response
                Logger.info("Sending AUTH_FAILED response to client");
                objectOut.writeObject("AUTH_FAILED");
                objectOut.flush();
                Logger.info("=== CLIENT HANDLER ENDED (AUTH FAILED) ===");
                return;
            }
            
            Logger.info("Authentication successful for " + clientIP);
            // Send authentication success response
            Logger.info("Sending AUTH_SUCCESS response to client");
            objectOut.writeObject("AUTH_SUCCESS");
            objectOut.flush();
            Logger.info("Authentication response sent successfully");
            
            // Second message from client should be its name
            Logger.info("Waiting for client name...");
            clientName = (String) objectIn.readObject();
            Logger.info("Received client name: '" + clientName + "' from " + clientIP);
            
            // Register client in the clients map
            Logger.info("Registering client in connection pool...");
            clients.put(clientName, this);
            Logger.info("Client registration successful");
            
            Logger.info("=== CLIENT CONNECTION ESTABLISHED ===");
            Logger.info("Client Name: " + clientName);
            Logger.info("Client IP: " + clientIP);
            Logger.info("Total connected clients: " + clients.size());
            Logger.info("======================================");
            
            // No request/response loop here; handled by sendRequestAndGetResponse
            // Just keep the thread alive until socket closes
            Logger.info("Client '" + clientName + "' is ready to receive requests");
            while (!socket.isClosed()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
            
            Logger.info("Socket closed for client '" + clientName + "' from " + clientIP);
        } catch (java.io.StreamCorruptedException e) {
            // Handle the specific case where someone tries to send HTTP to the socket port
            Logger.error("=== STREAM CORRUPTION DETECTED ===");
            Logger.error("Client IP: " + clientIP);
            Logger.error("Error message: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("invalid stream header")) {
                Logger.info("THREAT DETECTED: Invalid protocol from " + clientIP + " (likely HTTP to socket port)");
                recordSuspiciousActivity(clientIP, "INVALID_PROTOCOL_STREAM_CORRUPTION");
                handleHttpRequest();
            } else {
                Logger.info("THREAT DETECTED: Stream corruption from " + clientIP);
                recordSuspiciousActivity(clientIP, "STREAM_CORRUPTION");
            }
            Logger.error("=== END STREAM CORRUPTION ===");
        } catch (java.io.IOException e) {
            Logger.error("=== I/O ERROR DETECTED ===");
            Logger.error("Client IP: " + clientIP);
            Logger.error("Error type: " + e.getClass().getSimpleName());
            Logger.error("Error message: " + e.getMessage());
            
            // Check if this is a legitimate client connection issue vs HTTP probe
            if (e.getMessage() != null && e.getMessage().contains("mark/reset not supported")) {
                Logger.info("PROTOCOL: mark/reset not supported from " + clientIP + " - this may be a legitimate client with stream issues");
                Logger.info("PROTOCOL: Not treating mark/reset error as suspicious activity - likely legitimate client");
                // Don't record as suspicious activity - this is often a legitimate client issue
            } else if (e.getMessage() != null && 
                      (e.getMessage().toLowerCase().contains("connection reset") || 
                       e.getMessage().toLowerCase().contains("broken pipe") ||
                       e.getMessage().toLowerCase().contains("socket closed"))) {
                Logger.info("Connection from " + clientIP + " terminated normally during protocol detection");
                // These are normal connection termination scenarios, not threats
            } else {
                Logger.info("Connection from " + clientIP + " closed during protocol detection");
                // Only record truly suspicious I/O errors, not normal client connection issues
                Logger.info("I/O error details: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
            Logger.error("=== END I/O ERROR ===");
        } catch (ClassNotFoundException e) {
            Logger.error("=== CLASS NOT FOUND ERROR ===");
            Logger.error("Client IP: " + clientIP);
            Logger.error("This suggests version mismatch between client and server");
            Logger.error("Missing class: " + e.getMessage());
            Logger.error("=== END CLASS NOT FOUND ERROR ===");
            recordSuspiciousActivity(clientIP, "CLASS_NOT_FOUND_VERSION_MISMATCH");
        } catch (Exception e) {
            Logger.error("=== UNEXPECTED ERROR ===");
            Logger.error("Client IP: " + clientIP);
            Logger.error("Error type: " + e.getClass().getSimpleName());
            Logger.error("Error message: " + e.getMessage());
            Logger.error("Full stack trace:", e);
            Logger.error("=== END UNEXPECTED ERROR ===");
            recordSuspiciousActivity(clientIP, "CONNECTION_TERMINATED_UNEXPECTEDLY");
        } finally {
            Logger.info("=== CLIENT HANDLER CLEANUP ===");
            Logger.info("Cleaning up connection for: " + clientIP);
            if (clientName != null) {
                Logger.info("Removing client '" + clientName + "' from connection pool");
                clients.remove(clientName);
                Logger.info("Client removed. Remaining clients: " + clients.size());
            } else {
                Logger.info("No client name to remove (connection failed before registration)");
            }
            try { 
                socket.close(); 
                Logger.info("Socket closed successfully");
            } catch (java.io.IOException e) {
                Logger.error("Error closing socket: " + e.getMessage());
            }
            Logger.info("=== CLIENT HANDLER ENDED ===");
        }
    }

    /**
     * Sends a proxy request to the connected client and waits for the response.
     * 
     * This method is synchronized to ensure that only one request is processed
     * at a time per client connection, preventing race conditions and ensuring
     * proper request-response pairing.
     * 
     * The method performs the following operations:
     * 1. Serializes and sends the ProxyRequest to the client
     * 2. Flushes the output stream to ensure immediate delivery
     * 3. Waits for the client to process the request and send back a ProxyResponse
     * 4. Deserializes and returns the response
     * 
     * @param proxyRequest the request to send to the client, containing HTTP method, URL, and body
     * @return the response from the client after processing the request
     * @throws Exception if communication fails, serialization errors occur, or client disconnects
     * 
     * @see ProxyRequest
     * @see ProxyResponse
     */
    public synchronized ProxyResponse sendRequestAndGetResponse(ProxyRequest proxyRequest) throws Exception {
        try {
            Logger.info("Sending ProxyRequest to client '" + clientName + "': " + proxyRequest);
            
            // Perform connection health check before attempting communication
            if (!isConnectionHealthy()) {
                throw new Exception("Client '" + clientName + "' connection is unhealthy - removing from connection pool");
            }
            
            objectOut.writeObject(proxyRequest);
            objectOut.flush();
            Logger.info("ProxyRequest sent successfully, waiting for response...");
            
            // Wait for ProxyResponse with better error handling
            Object responseObj = objectIn.readObject();
            Logger.info("Received response object of type: " + responseObj.getClass().getName());
            
            if (!(responseObj instanceof ProxyResponse)) {
                throw new Exception("Received unexpected object type: " + responseObj.getClass().getName() + " (expected ProxyResponse)");
            }
            
            ProxyResponse proxyResponse = (ProxyResponse) responseObj;
            Logger.info("ProxyResponse received successfully from client '" + clientName + "'");
            return proxyResponse;
            
        } catch (java.net.SocketTimeoutException e) {
            Logger.error("Timeout occurred while waiting for response from client '" + clientName + "'");
            Logger.error("Client may be unresponsive or experiencing connection issues");
            Logger.error("Socket timeout: " + socket.getSoTimeout() + "ms");
            throw new Exception("Request timeout: Client '" + clientName + "' did not respond within " + 
                              (socket.getSoTimeout() / 1000) + " seconds");
            
        } catch (java.io.StreamCorruptedException e) {
            Logger.error("Stream corruption detected while communicating with client '" + clientName + "'");
            Logger.error("This typically indicates the client disconnected or sent invalid data");
            Logger.error("Error details: " + e.getMessage());
            throw new Exception("Communication error with client '" + clientName + "': Stream corrupted - " + e.getMessage());
            
        } catch (java.io.EOFException e) {
            Logger.error("Unexpected end of stream while reading response from client '" + clientName + "'");
            Logger.error("This typically indicates the client disconnected during response transmission");
            throw new Exception("Communication error with client '" + clientName + "': Client disconnected during response");
            
        } catch (java.io.IOException e) {
            Logger.error("I/O error during communication with client '" + clientName + "'");
            Logger.error("Socket state: closed=" + socket.isClosed() + ", connected=" + socket.isConnected());
            Logger.error("Error details: " + e.getMessage());
            throw new Exception("Communication error with client '" + clientName + "': " + e.getMessage());
            
        } catch (ClassNotFoundException e) {
            Logger.error("Class not found during response deserialization from client '" + clientName + "'");
            Logger.error("This suggests a version mismatch between client and server");
            Logger.error("Missing class: " + e.getMessage());
            throw new Exception("Serialization error with client '" + clientName + "': " + e.getMessage());
        }
    }

    /**
     * Handles HTTP requests sent to the socket server port by mistake.
     * 
     * This method sends an appropriate HTTP error response explaining that
     * the socket port is for proxy clients only, and directs users to use
     * the REST API endpoints instead.
     */
    private void handleHttpRequest() {
        try {
            String httpResponse = 
                "HTTP/1.1 400 Bad Request\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                "{\n" +
                "  \"error\": \"Invalid Protocol\",\n" +
                "  \"message\": \"This port is for proxy client connections only.\"\n" +
                "}\n";
            
            socket.getOutputStream().write(httpResponse.getBytes());
            socket.getOutputStream().flush();
            Logger.info("Responded to HTTP request with protocol error message");
        } catch (Exception e) {
            Logger.error("Failed to send HTTP error response", e);
        }
    }

    /**
     * Extracts the client IP address from the socket connection.
     * 
     * @return the client IP address as a string
     */
    private String extractClientIP() {
        try {
            return socket.getInetAddress().getHostAddress();
        } catch (Exception e) {
            Logger.error("Failed to extract client IP", e);
            return "unknown";
        }
    }

    /**
     * Checks if an IP address is currently banned.
     * 
     * @param ip the IP address to check
     * @return true if the IP is banned, false otherwise
     */
    private boolean isBannedIP(String ip) {
        return bannedIPs.contains(ip);
    }

    /**
     * Records suspicious activity from an IP address and implements automatic banning.
     * 
     * This method tracks suspicious activities and automatically bans IPs that:
     * - Exceed MAX_ATTEMPTS_BEFORE_BAN within TIME_WINDOW_MINUTES
     * - Reach PERMANENT_BAN_THRESHOLD total attempts across all time
     * - Has special handling for recently unbanned IPs and authentication failures
     * 
     * @param ip the IP address performing suspicious activity
     * @param activity the type of suspicious activity detected
     */
    private void recordSuspiciousActivity(String ip, String activity) {
        // Check if this IP was recently unbanned and is in grace period
        LocalDateTime recentUnbanTime = recentlyUnbannedIPs.get(ip);
        if (recentUnbanTime != null) {
            long minutesSinceUnban = ChronoUnit.MINUTES.between(recentUnbanTime, LocalDateTime.now());
            if (minutesSinceUnban <= GRACE_PERIOD_MINUTES) {
                Logger.info("SECURITY: IP " + ip + " is in grace period (" + minutesSinceUnban + "/" + GRACE_PERIOD_MINUTES + " minutes) - allowing activity: " + activity);
                return;
            } else {
                // Grace period expired, remove from recently unbanned list
                recentlyUnbannedIPs.remove(ip);
                Logger.info("SECURITY: Grace period expired for " + ip + ", resuming normal threat detection");
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Update attempt counters
        AtomicInteger attempts = suspiciousAttempts.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int currentAttempts = attempts.incrementAndGet();
        
        // Track timing
        firstAttemptTime.putIfAbsent(ip, now);
        lastAttemptTime.put(ip, now);
        
        Logger.info("SECURITY: Suspicious activity from " + ip + " - " + activity + " (attempt #" + currentAttempts + ")");
        
        // Check for immediate ban conditions
        LocalDateTime firstAttempt = firstAttemptTime.get(ip);
        long minutesSinceFirst = ChronoUnit.MINUTES.between(firstAttempt, now);
        
        // Different thresholds for authentication failures vs other threats
        int banThreshold = MAX_ATTEMPTS_BEFORE_BAN;
        if ("AUTHENTICATION_FAILED".equals(activity)) {
            banThreshold = AUTH_FAILURE_TOLERANCE; // More lenient for auth failures
            Logger.info("SECURITY: Using auth failure tolerance (" + AUTH_FAILURE_TOLERANCE + ") for " + ip);
        }
        
        // Ban if too many attempts in time window
        if (currentAttempts >= banThreshold && minutesSinceFirst <= TIME_WINDOW_MINUTES) {
            bannedIPs.add(ip);
            Logger.info("SECURITY: IP " + ip + " BANNED for " + currentAttempts + " suspicious attempts within " + minutesSinceFirst + " minutes (threshold: " + banThreshold + ")");
        }
        
        // Permanent ban for persistent attackers
        if (currentAttempts >= PERMANENT_BAN_THRESHOLD) {
            bannedIPs.add(ip);
            Logger.info("SECURITY: IP " + ip + " PERMANENTLY BANNED for reaching " + currentAttempts + " total suspicious attempts");
        }
        
        // Clean up old tracking data for IPs that haven't been active recently
        cleanupOldTrackingData();
    }

    /**
     * Cleans up tracking data for IPs that haven't been active in the last 24 hours.
     * This prevents memory leaks from accumulating data for old attackers.
     * Also cleans up expired grace periods.
     */
    private void cleanupOldTrackingData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        LocalDateTime gracePeriodCutoff = LocalDateTime.now().minusMinutes(GRACE_PERIOD_MINUTES);
        
        lastAttemptTime.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                String ip = entry.getKey();
                suspiciousAttempts.remove(ip);
                firstAttemptTime.remove(ip);
                // Don't remove from bannedIPs - those are permanent
                return true;
            }
            return false;
        });
        
        // Clean up expired grace periods
        recentlyUnbannedIPs.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(gracePeriodCutoff)) {
                Logger.info("SECURITY: Grace period expired for IP " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Gets the current set of banned IPs for monitoring purposes.
     * 
     * @return a copy of the banned IPs set
     */
    public static Set<String> getBannedIPs() {
        return Set.copyOf(bannedIPs);
    }

    /**
     * Gets the current suspicious activity statistics for monitoring.
     * 
     * @return a copy of the suspicious attempts map
     */
    public static Map<String, Integer> getSuspiciousAttempts() {
        return suspiciousAttempts.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
    }

    /**
     * Manually bans an IP address (for administrative purposes).
     * 
     * @param ip the IP address to ban
     */
    public static void banIP(String ip) {
        bannedIPs.add(ip);
        Logger.info("SECURITY: IP " + ip + " manually banned by administrator");
    }

    /**
     * Manually unbans an IP address (for administrative purposes).
     * 
     * @param ip the IP address to unban
     * @return true if the IP was found and removed from the ban list, false if it wasn't banned
     */
    public static boolean unbanIP(String ip) {
        boolean wasRemoved = bannedIPs.remove(ip);
        if (wasRemoved) {
            Logger.info("SECURITY: IP " + ip + " manually unbanned by administrator");
            // Also clear any suspicious activity tracking for this IP
            suspiciousAttempts.remove(ip);
            firstAttemptTime.remove(ip);
            lastAttemptTime.remove(ip);
            
            // Add to grace period to prevent immediate re-banning
            recentlyUnbannedIPs.put(ip, LocalDateTime.now());
            Logger.info("SECURITY: IP " + ip + " added to grace period for " + GRACE_PERIOD_MINUTES + " minutes");
            Logger.info("SECURITY: Cleared all tracking data for unbanned IP " + ip);
        } else {
            Logger.info("SECURITY: Attempted to unban IP " + ip + " but it was not in the banned list");
        }
        return wasRemoved;
    }

    /**
     * Checks if an IP address would be automatically banned based on current tracking data.
     * This is useful for debugging unban issues.
     * 
     * @param ip the IP address to check
     * @return information about why the IP might be auto-banned
     */
    public static Map<String, Object> checkAutoBanStatus(String ip) {
        Map<String, Object> status = new HashMap<>();
        
        // Check if IP is in grace period
        LocalDateTime recentUnbanTime = recentlyUnbannedIPs.get(ip);
        if (recentUnbanTime != null) {
            long minutesSinceUnban = ChronoUnit.MINUTES.between(recentUnbanTime, LocalDateTime.now());
            if (minutesSinceUnban <= GRACE_PERIOD_MINUTES) {
                status.put("inGracePeriod", true);
                status.put("gracePeriodRemainingMinutes", GRACE_PERIOD_MINUTES - minutesSinceUnban);
                status.put("wouldBeAutoBanned", false);
                status.put("reason", "IP is in grace period for " + (GRACE_PERIOD_MINUTES - minutesSinceUnban) + " more minutes");
                return status;
            }
        }
        status.put("inGracePeriod", false);
        
        AtomicInteger attempts = suspiciousAttempts.get(ip);
        LocalDateTime firstAttempt = firstAttemptTime.get(ip);
        LocalDateTime lastAttempt = lastAttemptTime.get(ip);
        
        if (attempts == null) {
            status.put("hasTrackingData", false);
            status.put("wouldBeAutoBanned", false);
            status.put("reason", "No suspicious activity tracking data");
            return status;
        }
        
        status.put("hasTrackingData", true);
        status.put("suspiciousAttempts", attempts.get());
        status.put("firstAttempt", firstAttempt.toString());
        status.put("lastAttempt", lastAttempt.toString());
        
        long minutesSinceFirst = ChronoUnit.MINUTES.between(firstAttempt, LocalDateTime.now());
        status.put("minutesSinceFirstAttempt", minutesSinceFirst);
        
        boolean wouldBanTimeWindow = attempts.get() >= MAX_ATTEMPTS_BEFORE_BAN && minutesSinceFirst <= TIME_WINDOW_MINUTES;
        boolean wouldBanAuthFailure = attempts.get() >= AUTH_FAILURE_TOLERANCE && minutesSinceFirst <= TIME_WINDOW_MINUTES;
        boolean wouldBanPermanent = attempts.get() >= PERMANENT_BAN_THRESHOLD;
        
        status.put("wouldBeAutoBanned", wouldBanTimeWindow || wouldBanAuthFailure || wouldBanPermanent);
        
        if (wouldBanPermanent) {
            status.put("reason", "Would be permanently banned due to " + attempts.get() + " total attempts (threshold: " + PERMANENT_BAN_THRESHOLD + ")");
        } else if (wouldBanAuthFailure) {
            status.put("reason", "Would be banned for " + attempts.get() + " auth failure attempts within " + minutesSinceFirst + " minutes (threshold: " + AUTH_FAILURE_TOLERANCE + " within " + TIME_WINDOW_MINUTES + " minutes)");
        } else if (wouldBanTimeWindow) {
            status.put("reason", "Would be banned for " + attempts.get() + " attempts within " + minutesSinceFirst + " minutes (threshold: " + MAX_ATTEMPTS_BEFORE_BAN + " within " + TIME_WINDOW_MINUTES + " minutes)");
        } else {
            status.put("reason", "Would not be auto-banned based on current criteria");
        }
        
        return status;
    }

    /**
     * Performs a health check on the client connection to detect broken sockets.
     * 
     * This method tests the connection state before attempting to send requests,
     * helping to detect zombie connections that appear connected but are actually broken.
     * 
     * @return true if the connection is healthy and ready for communication
     */
    private boolean isConnectionHealthy() {
        try {
            // Basic socket state checks
            if (socket == null || socket.isClosed() || !socket.isConnected()) {
                Logger.error("Connection health check failed: Socket is null, closed, or not connected");
                return false;
            }
            
            // Check if streams are closed
            if (socket.isInputShutdown() || socket.isOutputShutdown()) {
                Logger.error("Connection health check failed: Input or output stream is shutdown");
                return false;
            }
            
            // Check if the socket is still valid by accessing streams
            try {
                socket.getOutputStream();
                socket.getInputStream();
            } catch (Exception e) {
                Logger.error("Connection health check failed: Cannot access socket streams - " + e.getMessage());
                return false;
            }
            
            Logger.debug("Connection health check passed for client '" + clientName + "'");
            return true;
            
        } catch (Exception e) {
            Logger.error("Connection health check failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the socket connection for this client handler.
     * Used for connection health monitoring and cleanup operations.
     * 
     * @return the socket connection to the client
     */
    public Socket getSocket() {
        return socket;
    }
}
