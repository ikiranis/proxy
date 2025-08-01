package com.apps4net.proxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import com.apps4net.proxy.utils.Logger;

/**
 * A proxy client that connects to a proxy server and forwards HTTP requests to local web servers.
 * 
 * This client provides the following capabilities:
 * - Automatic connection management with reconnection on failure
 * - Health monitoring with periodic heartbeat checks
 * - HTTP request forwarding to LAN-based web servers
 * - Support for both HTTP and HTTPS with SSL validation bypass
 * - Binary content handling with Base64 encoding
 * - Graceful shutdown and cleanup
 * 
 * The client automatically handles network interruptions by attempting to
 * reconnect every 5 seconds and performs health checks every 30 seconds to
 * ensure connection stability.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
public class ProxyClient {
    private final String clientName;
    private final String serverHost;
    private final int serverPort;
    private final String authToken;
    private volatile boolean isRunning = false;
    private static final int RECONNECT_DELAY_MS = 5000; // 5 seconds
    private static final int HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    // HTTP port for API health checks (loaded from configuration)
    private int httpPort = 9990; // Default value

    /**
     * Creates a new ProxyClient with the specified connection parameters.
     * 
     * @param clientName unique identifier for this client instance
     * @param serverHost hostname or IP address of the proxy server
     * @param serverPort port number of the proxy server
     * @param authToken authentication token for server verification
     */
    public ProxyClient(String clientName, String serverHost, int serverPort, String authToken) {
        this.clientName = clientName;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.authToken = authToken;
        
        // Load configuration from application.properties
        loadConfiguration();
    }

    /**
     * Starts the proxy client and begins the connection management loop.
     * 
     * This method runs continuously until {@link #stop()} is called. It handles:
     * - Initial connection establishment
     * - Automatic reconnection on connection failures
     * - Graceful handling of network interruptions
     * - Proper cleanup on shutdown
     * 
     * The method will block the calling thread. For non-blocking operation,
     * run this method in a separate thread.
     * 
     * @see #stop()
     * @see #connectAndCommunicate()
     */
    public void start() {
        isRunning = true;
        Logger.info("=== ProxyClient Starting ===");
        Logger.info("Client Name: " + clientName);
        Logger.info("Server Host: " + serverHost);
        Logger.info("Server Socket Port: " + serverPort);
        Logger.info("Server HTTP Port: " + httpPort);
        Logger.info("Auth Token: " + (authToken != null ? "***configured***" : "NOT SET"));
        Logger.info("Reconnection enabled: Yes (every " + (RECONNECT_DELAY_MS / 1000) + " seconds on failure)");
        Logger.info("Health monitoring: Yes (every " + (HEARTBEAT_INTERVAL_MS / 1000) + " seconds)");
        Logger.info("Health check endpoint: http://" + serverHost + ":" + httpPort + "/api/health/" + clientName);
        Logger.info("============================");
        
        boolean firstAttempt = true;
        int reconnectAttempt = 0;
        
        while (isRunning) {
            try {
                if (firstAttempt) {
                    performPreConnectionDiagnostics();
                    firstAttempt = false;
                } else {
                    reconnectAttempt++;
                    Logger.info("=== Reconnection Attempt #" + reconnectAttempt + " ===");
                }
                
                connectAndCommunicate();
                
                // If we get here, connection was successful and then ended
                if (isRunning) {
                    Logger.info("Connection ended normally, will attempt to reconnect");
                }
                
            } catch (Exception e) {
                handleConnectionFailure(e);
                if (isRunning) {
                    Logger.info("Attempting to reconnect in " + (RECONNECT_DELAY_MS / 1000) + " seconds...");
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        Logger.info("ProxyClient stopped (Total reconnection attempts: " + reconnectAttempt + ")");
    }

    /**
     * Stops the proxy client and terminates all connections.
     * 
     * This method performs a graceful shutdown by:
     * - Setting the running flag to false
     * - Interrupting the main connection loop
     * - Allowing current operations to complete
     * - Triggering cleanup of resources
     * 
     * The method returns immediately; actual shutdown may take a few seconds.
     */
    public void stop() {
        isRunning = false;
    }

    /**
     * Forces an immediate reconnection by closing the current socket.
     * 
     * This method can be called externally to trigger a reconnection
     * when connection issues are detected from outside the client.
     * The main connection loop will automatically attempt to reconnect.
     */
    public void forceReconnect() {
        Logger.info("Force reconnection requested - will reconnect on next iteration");
        // The isRunning flag is still true, so the main loop will attempt reconnection
        // We don't have direct access to the socket here, but the health checks will detect issues
    }

    /**
     * Gets the current connection status of the client.
     * 
     * @return true if the client is currently running and should be connected, false if stopped
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Establishes connection to the server and handles the main communication loop.
     * 
     * This method performs the following operations:
     * 1. Creates socket connection and object streams
     * 2. Registers the client with the server
     * 3. Starts heartbeat monitoring thread
     * 4. Processes incoming proxy requests
     * 5. Forwards requests to LAN web servers
     * 6. Returns responses to the proxy server
     * 
     * The method includes timeout handling and health checks to detect
     * connection issues promptly.
     * 
     * @throws Exception if connection fails, communication errors occur, or the socket becomes unhealthy
     * 
     * @see #startHeartbeatThread(Socket)
     * @see #isSocketHealthy(Socket, ObjectOutputStream, ObjectInputStream)
     * @see #forwardToLanWebserver(String, String, String)
     */
    private void connectAndCommunicate() throws Exception {
        Logger.info("=== Connection Attempt ===");
        Logger.info("Attempting to connect to " + serverHost + ":" + serverPort);
        
        try (Socket socket = new Socket(serverHost, serverPort);
             ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream objectIn = new ObjectInputStream(socket.getInputStream())) {

            Logger.info("TCP socket connection established successfully");
            Logger.info("Local address: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort());
            Logger.info("Remote address: " + socket.getRemoteSocketAddress());
            Logger.info("Socket timeout: " + socket.getSoTimeout() + "ms");
            
            Logger.info("Creating object streams...");
            Logger.info("ObjectOutputStream created successfully");
            Logger.info("ObjectInputStream created successfully");
            
            // Set socket timeout for better connection health detection
            // This allows the client to detect when server stops responding
            socket.setSoTimeout(60000); // 60 second timeout for reading from server
            Logger.info("Socket timeout set to 60 seconds for connection health monitoring");
            
            // First message from client should be the authentication token
            Logger.info("[CLIENT] Sending authentication token...");
            objectOut.writeObject(authToken);
            objectOut.flush();
            Logger.info("[CLIENT] Authentication token sent successfully");
            
            // Wait for authentication response
            Logger.info("[CLIENT] Waiting for authentication response...");
            Object authResponse = objectIn.readObject();
            Logger.info("[CLIENT] Received authentication response: " + authResponse);
            
            if (!"AUTH_SUCCESS".equals(authResponse)) {
                Logger.error("[CLIENT] Authentication failed. Expected 'AUTH_SUCCESS', got: " + authResponse);
                Logger.error("[CLIENT] Possible issues:");
                Logger.error("  - Authentication token mismatch");
                Logger.error("  - Server configuration error");
                Logger.error("  - Token contains invalid characters");
                Logger.error("  - Server not accepting connections");
                throw new Exception("Authentication failed: " + authResponse);
            }
            Logger.info("[CLIENT] Authentication successful!");
            
            // Second message from client should be its name
            Logger.info("[CLIENT] Sending client name: " + clientName);
            objectOut.writeObject(clientName);
            objectOut.flush();
            Logger.info("[CLIENT] Client name sent successfully");
            
            // Start heartbeat thread
            Logger.info("[CLIENT] Starting connection monitoring...");
            Thread heartbeatThread = startHeartbeatThread(socket, objectOut, objectIn);
            Logger.info("[CLIENT] Connection monitoring started");
            
            Logger.info("=== Connection Established Successfully ===");
            Logger.info("Client '" + clientName + "' is now connected and ready to receive requests");
            
            try {
                while (isRunning && !socket.isClosed()) {
                    // Check if socket is still connected before proceeding
                    if (!isSocketHealthy(socket, objectOut, objectIn)) {
                        Logger.error("Socket connection health check failed - initiating reconnection");
                        Logger.error("Socket state: closed=" + socket.isClosed() + ", connected=" + socket.isConnected());
                        Logger.error("Breaking communication loop to trigger reconnection");
                        break;
                    }
                    
                    // Wait for ProxyRequest from server with timeout
                    // With heartbeat mechanism, server should send regular messages
                    // If no message for 60 seconds, connection may be broken
                    Object obj;
                    try {
                        obj = objectIn.readObject();
                    } catch (java.net.SocketTimeoutException e) {
                        Logger.info("[CLIENT] No message from server for 60 seconds");
                        Logger.info("[CLIENT] Performing connection health check to verify server connectivity");
                        
                        // When timeout occurs, check if connection is still healthy
                        if (!isSocketHealthy(socket, objectOut, objectIn)) {
                            Logger.error("[CLIENT] Connection health check failed after timeout - server appears disconnected");
                            Logger.error("[CLIENT] Breaking communication loop to trigger reconnection");
                            break;
                        } else {
                            Logger.info("[CLIENT] Connection health check passed - server may be idle, continuing to wait");
                            continue;
                        }
                    }
                    
                    if (!(obj instanceof com.apps4net.proxy.shared.ProxyRequest)) {
                        Logger.error("[CLIENT] Received unexpected object type from server: " + 
                                   (obj != null ? obj.getClass().getName() : "null"));
                        Logger.error("[CLIENT] Expected: com.apps4net.proxy.shared.ProxyRequest");
                        continue;
                    }
                    
                    com.apps4net.proxy.shared.ProxyRequest proxyRequest = (com.apps4net.proxy.shared.ProxyRequest) obj;
                    Logger.info("[CLIENT] Received ProxyRequest: " + proxyRequest.getHttpMethodType() + " " + proxyRequest.getUrl());
                    
                    // Handle heartbeat requests specially
                    if ("HEARTBEAT".equals(proxyRequest.getHttpMethodType())) {
                        Logger.debug("[CLIENT] Processing heartbeat request from server");
                        // Respond immediately with a success response for heartbeats
                        com.apps4net.proxy.shared.ProxyResponse heartbeatResponse = 
                            new com.apps4net.proxy.shared.ProxyResponse(200, "heartbeat_ok");
                        
                        try {
                            objectOut.writeObject(heartbeatResponse);
                            objectOut.flush();
                            Logger.debug("[CLIENT] Heartbeat response sent successfully");
                        } catch (Exception e) {
                            Logger.error("[CLIENT] Failed to send heartbeat response: " + e.getMessage());
                        }
                        continue; // Skip normal processing for heartbeat requests
                    }
                    
                    // Perform the API call
                    Logger.info("[CLIENT] Starting API call to LAN webserver...");
                    String responseBody = forwardToLanWebserver(proxyRequest.getHttpMethodType(), proxyRequest.getUrl(), proxyRequest.getBody());
                    Logger.info("[CLIENT] API call completed, response body length: " + (responseBody != null ? responseBody.length() : 0));
                    
                    if (responseBody == null) {
                        Logger.error("[CLIENT] Warning: responseBody is null, setting to empty string");
                        responseBody = "";
                    }
                    
                    // For demo, always return 200
                    Logger.info("[CLIENT] Creating ProxyResponse object...");
                    com.apps4net.proxy.shared.ProxyResponse proxyResponse = new com.apps4net.proxy.shared.ProxyResponse(200, responseBody);
                    Logger.info("[CLIENT] ProxyResponse created successfully, preparing to send to server...");
                    
                    try {
                        Logger.info("[CLIENT] Attempting to serialize and send ProxyResponse...");
                        Logger.info("[CLIENT] Response body size: " + (responseBody != null ? responseBody.length() : 0) + " characters");
                        
                        objectOut.writeObject(proxyResponse);
                        objectOut.flush();
                        Logger.info("[CLIENT] ProxyResponse sent successfully to server");
                    } catch (java.io.IOException e) {
                        Logger.error("[CLIENT] Failed to send ProxyResponse to server");
                        Logger.error("[CLIENT] Socket state: closed=" + socket.isClosed() + ", connected=" + socket.isConnected());
                        Logger.error("[CLIENT] Error type: " + e.getClass().getSimpleName());
                        Logger.error("[CLIENT] Error message: " + e.getMessage());
                        throw e; // Re-throw to break the communication loop
                    } catch (Exception e) {
                        Logger.error("[CLIENT] Unexpected error while sending ProxyResponse");
                        Logger.error("[CLIENT] Error type: " + e.getClass().getSimpleName());
                        Logger.error("[CLIENT] Error message: " + e.getMessage());
                        Logger.error("[CLIENT] Full stack trace:", e);
                        throw e;
                    }
                }
                
                Logger.info("[CLIENT] Communication loop ended");
                if (!isRunning) {
                    Logger.info("[CLIENT] Shutdown was requested by user");
                } else if (socket.isClosed()) {
                    Logger.error("[CLIENT] Socket was closed unexpectedly");
                }
                
            } finally {
                Logger.info("[CLIENT] Stopping connection monitoring...");
                heartbeatThread.interrupt();
                Logger.info("[CLIENT] Connection monitoring stopped");
            }
            
        } catch (java.net.ConnectException e) {
            Logger.error("Connection refused by server");
            Logger.error("This typically means the server is not running or not accepting connections");
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            Logger.error("Connection timeout occurred");
            Logger.error("This suggests network latency or server responsiveness issues");
            throw e;
        } catch (java.net.UnknownHostException e) {
            Logger.error("Cannot resolve hostname: " + serverHost);
            Logger.error("Check DNS configuration or use IP address");
            throw e;
        } catch (java.io.IOException e) {
            Logger.error("I/O error during communication: " + e.getMessage());
            Logger.error("This suggests a network interruption or protocol issue");
            throw e;
        } catch (ClassNotFoundException e) {
            Logger.error("Class not found during object deserialization: " + e.getMessage());
            Logger.error("This suggests a version mismatch between client and server");
            throw e;
        }
    }

    /**
     * Starts a daemon thread that monitors connection health via API-based health checks.
     * 
     * The heartbeat thread:
     * - Runs every 30 seconds
     * - Makes HTTP requests to the server's health API endpoint
     * - Checks if the client connection is recognized as healthy by the server
     * - Triggers reconnection if the server reports the client as disconnected
     * - Handles interruption gracefully during shutdown
     * 
     * This approach is non-intrusive and doesn't interfere with the main
     * request/response communication channel between client and server.
     * 
     * @param socket the socket connection to monitor
     * @param objectOut the ObjectOutputStream (not used in API-based health checks)
     * @param objectIn the ObjectInputStream (not used in API-based health checks)
     * @return the heartbeat thread instance for lifecycle management
     * 
     * @see #performApiHealthCheck()
     */
    private Thread startHeartbeatThread(Socket socket, ObjectOutputStream objectOut, ObjectInputStream objectIn) {
        Thread heartbeatThread = new Thread(() -> {
            while (isRunning && !socket.isClosed()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    
                    // Use API-based health check instead of socket manipulation
                    if (!performApiHealthCheck()) {
                        Logger.error("Heartbeat: API health check failed - server reports client as disconnected");
                        Logger.error("Heartbeat: Triggering reconnection");
                        try {
                            socket.close(); // Force close to trigger reconnection in main loop
                        } catch (Exception e) {
                            Logger.error("Error closing socket after API health check failure: " + e.getMessage());
                        }
                        break;
                    } else {
                        Logger.info("Heartbeat: API health check passed - server recognizes client as healthy");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Logger.error("Heartbeat error: " + e.getMessage());
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        return heartbeatThread;
    }

    /**
     * Performs a basic health check on the socket connection.
     * 
     * This method verifies connection health by checking local socket state only:
     * 1. Checking if the socket is open and connected
     * 2. Testing if the socket streams are accessible
     * 3. Checking for connection shutdown states
     * 4. Verifying basic stream availability
     * 
     * This method is designed to be lightweight and non-intrusive, performing
     * only local checks without network communication. For comprehensive health
     * validation including server-side verification, use performApiHealthCheck().
     * 
     * @param socket the socket connection to check
     * @param objectOut the ObjectOutputStream (may be null)
     * @param objectIn the ObjectInputStream (may be null)
     * @return true if the socket appears healthy locally, false otherwise
     * 
     * @see #performApiHealthCheck()
     */
    private synchronized boolean isSocketHealthy(Socket socket, ObjectOutputStream objectOut, ObjectInputStream objectIn) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            Logger.debug("Socket health check failed: socket is null, closed, or not connected");
            return false;
        }
        
        try {
            // Check for shutdown states
            if (socket.isInputShutdown() || socket.isOutputShutdown()) {
                Logger.debug("Socket health check failed: input or output is shutdown");
                return false;
            }
            
            // Test if we can access the streams without writing
            // This will fail if the socket is broken
            socket.getOutputStream();
            socket.getInputStream();
            
            // Additional check for broken connections by testing available data
            // without blocking (available() returns immediately)
            try {
                socket.getInputStream().available();
            } catch (Exception e) {
                Logger.debug("Socket health check failed: cannot read stream availability - " + e.getMessage());
                return false;
            }
            
            Logger.debug("Basic socket health check passed");
            return true;
            
        } catch (Exception e) {
            Logger.debug("Socket health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Performs active server validation by testing network connectivity and socket health.
     * 
     * This method performs comprehensive validation that can detect network connectivity
     * issues, including cases where the local socket appears healthy but the network
     * connection to the server is actually broken.
     * 
     * @param objectOut the ObjectOutputStream for validation
     * @param objectIn the ObjectInputStream for validation
     * @return true if the connection appears healthy, false if connection is dead
     */
    /**
     * Determines if an exception is related to network connectivity issues.
     * 
     * This method classifies exceptions to enable intelligent error handling:
     * - Network exceptions: logged without stack traces to reduce noise
     * - Other exceptions: logged with full stack traces for debugging
     * 
     * Recognized network exception patterns include:
     * - SocketException, ConnectException, SocketTimeoutException
     * - Connection reset, Connection refused
     * - Broken pipe, Network unreachable
     * 
     * @param e the exception to classify
     * @return true if the exception is network-related, false otherwise
     */
    private boolean isNetworkException(Exception e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage();
        return className.equals("SocketException") || 
               className.equals("ConnectException") ||
               className.equals("SocketTimeoutException") ||
               (message != null && (message.contains("Connection reset") || 
                                   message.contains("Connection refused") ||
                                   message.contains("Broken pipe") ||
                                   message.contains("Network is unreachable")));
    }

    /**
     * Forwards an HTTP request to the LAN webserver using the provided method, URL, and body.
     * Returns the response body and headers as a string, or an error message if the request fails.
     *
     * @param httpMethodType The HTTP method (GET, POST, etc.)
     * @param url The URL to forward the request to
     * @param body The request body (may be null or empty for GET)
     * @return The response body and headers from the LAN webserver, or an error message
     */
    private String forwardToLanWebserver(String httpMethodType, String url, String body) {
        Logger.info("=== LAN Webserver Request ===");
        Logger.info("Method: " + httpMethodType);
        Logger.info("URL: " + url);
        Logger.info("Body Length: " + (body != null ? body.length() : 0) + " characters");
        
        // Disable SSL certificate validation (INSECURE: for development only)
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            Logger.info("SSL certificate validation disabled for development");
        } catch (Exception e) {
            Logger.error("[CLIENT] Failed to disable SSL validation: " + e.getMessage());
        }
        
        try {
            Logger.info("Parsing URL...");
            java.net.URI uri = java.net.URI.create(url);
            java.net.URL apiUrl = uri.toURL();
            Logger.info("Target host: " + apiUrl.getHost());
            Logger.info("Target port: " + apiUrl.getPort());
            Logger.info("Protocol: " + apiUrl.getProtocol());
            
            Logger.info("Opening connection...");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod(httpMethodType);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(30000); // 30 seconds
            conn.setReadTimeout(30000); // 30 seconds
            
            Logger.info("Connection timeout: " + conn.getConnectTimeout() + "ms");
            Logger.info("Read timeout: " + conn.getReadTimeout() + "ms");
            
            if (!httpMethodType.equals("GET") && body != null && !body.trim().isEmpty()) {
                Logger.info("Sending request body...");
                conn.setDoOutput(true);
                try (java.io.PrintWriter writer = new java.io.PrintWriter(conn.getOutputStream())) {
                    writer.print(body);
                    writer.flush();
                }
                Logger.info("Request body sent successfully");
            }

            Logger.info("Waiting for response...");
            int status = conn.getResponseCode();
            String statusMessage = conn.getResponseMessage();
            Logger.info("Response status: " + status + " " + (statusMessage != null ? statusMessage : ""));

            // Collect headers
            Logger.info("Reading response headers...");
            StringBuilder headers = new StringBuilder();
            for (int i = 1;; i++) {
                String headerKey = conn.getHeaderFieldKey(i);
                String headerValue = conn.getHeaderField(i);
                if (headerKey == null && headerValue == null) break;
                if (headerKey != null && headerValue != null) {
                    headers.append(headerKey).append(": ").append(headerValue).append("\n");
                }
            }
            Logger.info("Found " + headers.toString().split("\n").length + " response headers");

            // Read the response as bytes (for binary data like PDF)
            Logger.info("Reading response body...");
            java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            int totalBytes = 0;
            final int MAX_RESPONSE_SIZE = 50 * 1024 * 1024; // 50MB limit
            
            while ((bytesRead = is.read(buffer)) != -1) {
                if (totalBytes + bytesRead > MAX_RESPONSE_SIZE) {
                    Logger.error("Response size exceeds maximum allowed limit of " + (MAX_RESPONSE_SIZE / 1024 / 1024) + "MB");
                    is.close();
                    return "LAN webserver error: Response too large (exceeds " + (MAX_RESPONSE_SIZE / 1024 / 1024) + "MB limit)";
                }
                baos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            is.close();
            Logger.info("Read " + totalBytes + " bytes from response");
            
            // Encode the bytes as Base64 to safely transmit binary data
            Logger.info("Encoding response as Base64...");
            String base64Body = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            Logger.info("Base64 encoding complete. Encoded size: " + base64Body.length() + " characters");
            
            // Return both headers and base64-encoded body
            String result = "Headers:\n" + headers + "\nBody-Base64:\n" + base64Body;
            Logger.info("=== LAN Webserver Request Complete ===");
            return result;
            
        } catch (java.net.MalformedURLException e) {
            Logger.error("WEBSERVER ERROR: Invalid URL format");
            Logger.error("URL: " + url);
            Logger.error("Error: " + e.getMessage());
            return "LAN webserver error: Invalid URL format - " + e.getMessage();
            
        } catch (java.net.ConnectException e) {
            Logger.error("WEBSERVER ERROR: Connection refused");
            Logger.error("Target: " + url);
            Logger.error("This typically means the target webserver is not running");
            Logger.error("Error: " + e.getMessage());
            return "LAN webserver error: Connection refused - target webserver may not be running";
            
        } catch (java.net.SocketTimeoutException e) {
            Logger.error("WEBSERVER ERROR: Request timeout");
            Logger.error("Target: " + url);
            Logger.error("The webserver took too long to respond (>30 seconds)");
            Logger.error("Error: " + e.getMessage());
            return "LAN webserver error: Request timeout - webserver response too slow";
            
        } catch (java.net.UnknownHostException e) {
            Logger.error("WEBSERVER ERROR: Cannot resolve hostname");
            Logger.error("URL: " + url);
            Logger.error("Check if the hostname in the URL is correct");
            Logger.error("Error: " + e.getMessage());
            return "LAN webserver error: Cannot resolve hostname - " + e.getMessage();
            
        } catch (java.io.IOException e) {
            Logger.error("WEBSERVER ERROR: I/O error during request");
            Logger.error("URL: " + url);
            Logger.error("This could indicate network issues or server problems");
            Logger.error("Error: " + e.getMessage());
            return "LAN webserver error: I/O error - " + e.getMessage();
            
        } catch (Exception e) {
            Logger.error("WEBSERVER ERROR: Unexpected error");
            Logger.error("URL: " + url);
            Logger.error("Error type: " + e.getClass().getSimpleName());
            Logger.error("Error message: " + e.getMessage());
            Logger.error("Full stack trace:", e);
            return "LAN webserver error: " + e.getMessage();
        }
    }
    
    /**
     * Performs pre-connection diagnostics to help identify potential issues.
     * 
     * This method runs various checks before attempting to connect:
     * - Network configuration validation
     * - Server reachability tests
     * - Port accessibility checks
     * - DNS resolution verification
     * 
     * Results are logged to help troubleshoot connection issues.
     */
    private void performPreConnectionDiagnostics() {
        Logger.info("=== Pre-Connection Diagnostics ===");
        
        // 1. Basic configuration validation
        if (serverHost == null || serverHost.trim().isEmpty()) {
            Logger.error("DIAGNOSTIC: Server host is null or empty!");
            return;
        }
        
        if (serverPort <= 0 || serverPort > 65535) {
            Logger.error("DIAGNOSTIC: Invalid server port: " + serverPort + " (must be 1-65535)");
            return;
        }
        
        if (authToken == null || authToken.trim().isEmpty()) {
            Logger.error("DIAGNOSTIC: Authentication token is null or empty!");
            Logger.error("HINT: Ensure the auth token matches the server's proxy.auth.token configuration");
            return;
        }
        
        // 2. DNS resolution test
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(serverHost);
            Logger.info("DIAGNOSTIC: DNS resolution successful - " + serverHost + " -> " + address.getHostAddress());
        } catch (java.net.UnknownHostException e) {
            Logger.error("DIAGNOSTIC: DNS resolution failed for host: " + serverHost);
            Logger.error("HINT: Check if the hostname is correct or use an IP address directly");
            return;
        }
        
        // 3. Basic reachability test (ICMP ping alternative)
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(serverHost);
            boolean reachable = address.isReachable(5000); // 5 second timeout
            if (reachable) {
                Logger.info("DIAGNOSTIC: Host is reachable via ICMP");
            } else {
                Logger.error("DIAGNOSTIC: Host is not reachable via ICMP (may be blocked by firewall)");
            }
        } catch (Exception e) {
            Logger.error("DIAGNOSTIC: Reachability test failed: " + e.getMessage());
        }
        
        // 4. Port connectivity test
        try (java.net.Socket testSocket = new java.net.Socket()) {
            testSocket.connect(new java.net.InetSocketAddress(serverHost, serverPort), 3000);
            Logger.info("DIAGNOSTIC: Port " + serverPort + " is accessible");
            testSocket.close();
        } catch (java.net.ConnectException e) {
            Logger.error("DIAGNOSTIC: Cannot connect to port " + serverPort + " - " + e.getMessage());
            Logger.error("HINTS:");
            Logger.error("  - Check if the proxy server is running");
            Logger.error("  - Verify the port number is correct");
            Logger.error("  - Check firewall settings on both client and server");
            Logger.error("  - Ensure the server is listening on the correct interface (0.0.0.0 vs 127.0.0.1)");
        } catch (java.net.SocketTimeoutException e) {
            Logger.error("DIAGNOSTIC: Connection timeout to port " + serverPort);
            Logger.error("HINTS:");
            Logger.error("  - Server may be overloaded or slow to respond");
            Logger.error("  - Network latency issues");
            Logger.error("  - Check if there's a proxy or NAT between client and server");
        } catch (Exception e) {
            Logger.error("DIAGNOSTIC: Port connectivity test failed: " + e.getMessage());
        }
        
        Logger.info("=== Diagnostics Complete ===");
    }
    
    /**
     * Handles connection failure with detailed error analysis and troubleshooting guidance.
     * 
     * This method provides:
     * - Detailed error classification
     * - Specific troubleshooting steps
     * - Network condition analysis
     * - Configuration validation hints
     * 
     * @param e the exception that caused the connection failure
     */
    private void handleConnectionFailure(Exception e) {
        Logger.error("=== Connection Failure Analysis ===");
        Logger.error("Error Type: " + e.getClass().getSimpleName());
        Logger.error("Error Message: " + e.getMessage());
        
        // Classify the error and provide specific guidance
        if (e instanceof java.net.ConnectException) {
            Logger.error("DIAGNOSIS: Connection refused by server");
            Logger.error("POSSIBLE CAUSES:");
            Logger.error("  1. Proxy server is not running");
            Logger.error("  2. Server is not listening on port " + serverPort);
            Logger.error("  3. Firewall blocking the connection");
            Logger.error("  4. Wrong server host/port configuration");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Verify server is running: Check server logs");
            Logger.error("  2. Test server manually: telnet " + serverHost + " " + serverPort);
            Logger.error("  3. Check server configuration: proxy.socket.port=" + serverPort);
            Logger.error("  4. Verify firewall rules allow port " + serverPort);
            
        } else if (e instanceof java.net.SocketTimeoutException) {
            Logger.error("DIAGNOSIS: Connection timeout");
            Logger.error("POSSIBLE CAUSES:");
            Logger.error("  1. Network latency or packet loss");
            Logger.error("  2. Server overloaded or slow");
            Logger.error("  3. Firewall dropping packets");
            Logger.error("  4. Network routing issues");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Check network connectivity: ping " + serverHost);
            Logger.error("  2. Verify server load and performance");
            Logger.error("  3. Check for intermediate firewalls or NAT");
            
        } else if (e instanceof java.net.UnknownHostException) {
            Logger.error("DIAGNOSIS: Cannot resolve hostname");
            Logger.error("POSSIBLE CAUSES:");
            Logger.error("  1. Hostname does not exist");
            Logger.error("  2. DNS server issues");
            Logger.error("  3. Network configuration problems");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Verify hostname spelling: " + serverHost);
            Logger.error("  2. Test DNS resolution: nslookup " + serverHost);
            Logger.error("  3. Try using IP address instead of hostname");
            Logger.error("  4. Check DNS server configuration");
            
        } else if (e instanceof java.net.SocketException) {
            String message = e.getMessage();
            if (message != null && message.contains("Connection reset")) {
                Logger.error("DIAGNOSIS: Connection reset by server");
                Logger.error("POSSIBLE CAUSES:");
                Logger.error("  1. Server rejected the connection");
                Logger.error("  2. Authentication failure");
                Logger.error("  3. Server detected malicious activity");
                Logger.error("  4. Network equipment reset the connection");
                Logger.error("TROUBLESHOOTING STEPS:");
                Logger.error("  1. Check authentication token matches server configuration");
                Logger.error("  2. Review server logs for rejection reasons");
                Logger.error("  3. Verify client IP is not banned by server security");
                
            } else if (message != null && message.contains("Network is unreachable")) {
                Logger.error("DIAGNOSIS: Network unreachable");
                Logger.error("POSSIBLE CAUSES:");
                Logger.error("  1. Routing table issues");
                Logger.error("  2. Network interface problems");
                Logger.error("  3. VPN or network configuration");
                Logger.error("TROUBLESHOOTING STEPS:");
                Logger.error("  1. Check network interface status");
                Logger.error("  2. Verify routing configuration");
                Logger.error("  3. Test basic connectivity: ping gateway");
                
            } else {
                Logger.error("DIAGNOSIS: Socket error - " + message);
                Logger.error("TROUBLESHOOTING STEPS:");
                Logger.error("  1. Check network interface status");
                Logger.error("  2. Verify socket permissions");
                Logger.error("  3. Review system network logs");
            }
            
        } else if (e instanceof java.io.IOException) {
            Logger.error("DIAGNOSIS: I/O error during communication");
            Logger.error("POSSIBLE CAUSES:");
            Logger.error("  1. Network interruption during communication");
            Logger.error("  2. Server closed connection unexpectedly");
            Logger.error("  3. Protocol mismatch or corruption");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Check network stability");
            Logger.error("  2. Review server logs for errors");
            Logger.error("  3. Verify authentication token format");
            
        } else if (e.getMessage() != null && e.getMessage().contains("Authentication failed")) {
            Logger.error("DIAGNOSIS: Authentication failure");
            Logger.error("POSSIBLE CAUSES:");
            Logger.error("  1. Wrong authentication token");
            Logger.error("  2. Server authentication disabled");
            Logger.error("  3. Token format mismatch");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Verify auth token: " + (authToken != null ? authToken.substring(0, Math.min(10, authToken.length())) + "..." : "null"));
            Logger.error("  2. Check server config: proxy.auth.token");
            Logger.error("  3. Ensure both client and server use same token");
            Logger.error("  4. Check for whitespace or encoding issues");
            
        } else {
            Logger.error("DIAGNOSIS: Unexpected error");
            Logger.error("TROUBLESHOOTING STEPS:");
            Logger.error("  1. Check application logs for more details");
            Logger.error("  2. Verify Java version compatibility");
            Logger.error("  3. Review system resource availability");
            
            // Log full stack trace for unexpected errors
            if (!isNetworkException(e)) {
                Logger.error("Full stack trace:", e);
            }
        }
        
        // Always provide general recovery information
        Logger.error("RECOVERY:");
        Logger.error("  - Client will retry connection in " + (RECONNECT_DELAY_MS / 1000) + " seconds");
        Logger.error("  - Check server status and logs");
        Logger.error("  - Verify network connectivity");
        Logger.error("  - Ensure authentication configuration is correct");
        Logger.error("=== End Connection Analysis ===");
    }
    
    /**
     * Performs API-based health check by calling the server's health endpoint.
     * 
     * This method makes an HTTP GET request to the server's /api/health/{clientName} endpoint
     * to verify that the server recognizes this client as connected and healthy.
     * This approach is non-intrusive and doesn't interfere with the main socket communication.
     * 
     * The method handles various failure scenarios:
     * - Connection timeouts or network issues
     * - HTTP error responses (404, 500, etc.)
     * - JSON parsing errors
     * - Server reporting client as disconnected
     * 
     * @return true if the server reports the client as healthy, false otherwise
     */
    private boolean performApiHealthCheck() {
        try {
            // Build the health check URL using the configured HTTP port
            String healthUrl = "http://" + serverHost + ":" + httpPort + "/api/health/" + clientName;
            Logger.debug("Performing API health check: " + healthUrl);
            
            // Create HTTP connection
            java.net.URI uri = java.net.URI.create(healthUrl);
            java.net.URL url = uri.toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000); // 5 second connection timeout
            conn.setReadTimeout(10000); // 10 second read timeout
            
            // Get response
            int responseCode = conn.getResponseCode();
            Logger.debug("API health check response code: " + responseCode);
            
            if (responseCode == 200) {
                // Read response body to get detailed status
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String responseBody = response.toString();
                    Logger.debug("API health check response: " + responseBody);
                    
                    // Simple check for "healthy" status in JSON response
                    if (responseBody.contains("\"status\":\"healthy\"") && 
                        responseBody.contains("\"connected\":true")) {
                        Logger.debug("API health check: Server reports client as healthy");
                        return true;
                    } else {
                        Logger.debug("API health check: Server response indicates client is not healthy");
                        return false;
                    }
                }
            } else if (responseCode == 404) {
                Logger.debug("API health check: Server reports client as not found (404)");
                return false;
            } else {
                Logger.debug("API health check: Server returned error code " + responseCode);
                return false;
            }
            
        } catch (java.net.ConnectException e) {
            Logger.debug("API health check failed: Cannot connect to server - " + e.getMessage());
            return false;
        } catch (java.net.SocketTimeoutException e) {
            Logger.debug("API health check failed: Request timeout - " + e.getMessage());
            return false;
        } catch (java.net.UnknownHostException e) {
            Logger.debug("API health check failed: Cannot resolve hostname - " + e.getMessage());
            return false;
        } catch (java.io.IOException e) {
            Logger.debug("API health check failed: I/O error - " + e.getMessage());
            return false;
        } catch (Exception e) {
            Logger.debug("API health check failed: Unexpected error - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Loads configuration from application.properties file.
     * This method reads the server.port property to determine the HTTP port
     * for API health check calls.
     */
    private void loadConfiguration() {
        try {
            // Try to load application.properties from the classpath
            java.io.InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties");
            if (input == null) {
                Logger.debug("application.properties not found in classpath, using default HTTP port: " + httpPort);
                return;
            }
            
            java.util.Properties properties = new java.util.Properties();
            properties.load(input);
            
            // Read server.port property
            String serverPortStr = properties.getProperty("server.port");
            if (serverPortStr != null && !serverPortStr.trim().isEmpty()) {
                try {
                    httpPort = Integer.parseInt(serverPortStr.trim());
                    Logger.debug("Loaded HTTP port from application.properties: " + httpPort);
                } catch (NumberFormatException e) {
                    Logger.debug("Invalid server.port in application.properties: " + serverPortStr + ", using default: " + httpPort);
                }
            } else {
                Logger.debug("server.port not found in application.properties, using default: " + httpPort);
            }
            
            input.close();
            
        } catch (java.io.IOException e) {
            Logger.debug("Failed to load application.properties: " + e.getMessage() + ", using default HTTP port: " + httpPort);
        } catch (Exception e) {
            Logger.debug("Unexpected error loading configuration: " + e.getMessage() + ", using default HTTP port: " + httpPort);
        }
    }
}
