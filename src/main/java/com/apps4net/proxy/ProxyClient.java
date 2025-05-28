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
    private volatile boolean isRunning = false;
    private static final int RECONNECT_DELAY_MS = 5000; // 5 seconds
    private static final int HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    /**
     * Creates a new ProxyClient with the specified connection parameters.
     * 
     * @param clientName unique identifier for this client instance
     * @param serverHost hostname or IP address of the proxy server
     * @param serverPort port number of the proxy server
     */
    public ProxyClient(String clientName, String serverHost, int serverPort) {
        this.clientName = clientName;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
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
        while (isRunning) {
            try {
                connectAndCommunicate();
            } catch (Exception e) {
                if (isNetworkException(e)) {
                    Logger.error("Connection failed: " + e.getMessage());
                } else {
                    Logger.error("Connection failed: " + e.getMessage(), e);
                }
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
        Logger.info("ProxyClient stopped");
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
     * @see #isSocketHealthy(Socket)
     * @see #forwardToLanWebserver(String, String, String)
     */
    private void connectAndCommunicate() throws Exception {
        try (Socket socket = new Socket(serverHost, serverPort);
             ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream objectIn = new ObjectInputStream(socket.getInputStream())) {

            Logger.info("Connected to server " + serverHost + ":" + serverPort);
            
            // First message from client should be its name
            objectOut.writeObject(clientName);
            objectOut.flush();
            Logger.info("[CLIENT] Sent client name: " + clientName);
            
            // Start heartbeat thread
            Thread heartbeatThread = startHeartbeatThread(socket);
            
            try {
                while (isRunning && !socket.isClosed()) {
                    // Check if socket is still connected
                    if (!isSocketHealthy(socket)) {
                        Logger.error("Socket connection is unhealthy, breaking communication loop");
                        break;
                    }
                    
                    // Wait for ProxyRequest from server with timeout
                    socket.setSoTimeout(1000); // 1 second timeout for checking isRunning
                    Object obj;
                    try {
                        obj = objectIn.readObject();
                    } catch (java.net.SocketTimeoutException e) {
                        // Timeout is expected, continue loop to check isRunning and socket health
                        continue;
                    }
                    
                    if (!(obj instanceof com.apps4net.proxy.shared.ProxyRequest)) {
                        Logger.error("[CLIENT] Received unknown object from server: " + obj);
                        continue;
                    }
                    com.apps4net.proxy.shared.ProxyRequest proxyRequest = (com.apps4net.proxy.shared.ProxyRequest) obj;
                    Logger.info("[CLIENT] Received ProxyRequest: " + proxyRequest.getHttpMethodType() + " " + proxyRequest.getUrl());
                    // Perform the API call
                    String responseBody = forwardToLanWebserver(proxyRequest.getHttpMethodType(), proxyRequest.getUrl(), proxyRequest.getBody());
                    // For demo, always return 200
                    com.apps4net.proxy.shared.ProxyResponse proxyResponse = new com.apps4net.proxy.shared.ProxyResponse(200, responseBody);
                    objectOut.writeObject(proxyResponse);
                    objectOut.flush();
                    Logger.info("[CLIENT] Sent ProxyResponse to server");
                }
            } finally {
                heartbeatThread.interrupt();
            }
        }
    }

    /**
     * Starts a daemon thread that monitors connection health via periodic heartbeats.
     * 
     * The heartbeat thread:
     * - Runs every 30 seconds
     * - Performs socket health checks
     * - Logs connection status
     * - Terminates if connection becomes unhealthy
     * - Handles interruption gracefully during shutdown
     * 
     * @param socket the socket connection to monitor
     * @return the heartbeat thread instance for lifecycle management
     * 
     * @see #isSocketHealthy(Socket)
     */
    private Thread startHeartbeatThread(Socket socket) {
        Thread heartbeatThread = new Thread(() -> {
            while (isRunning && !socket.isClosed()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    if (!isSocketHealthy(socket)) {
                        Logger.error("Heartbeat: Socket connection is unhealthy");
                        break;
                    }
                    Logger.info("Heartbeat: Connection is healthy");
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
     * Performs a health check on the socket connection.
     * 
     * This method verifies connection health by:
     * 1. Checking if the socket is open and connected
     * 2. Attempting to write a test byte to the output stream
     * 3. Flushing the stream to detect write failures
     * 
     * The health check is non-intrusive and should not affect normal
     * communication flow.
     * 
     * @param socket the socket connection to check
     * @return true if the socket is healthy and writable, false otherwise
     */
    private boolean isSocketHealthy(Socket socket) {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            return false;
        }
        
        try {
            // Try to send a test byte and check if socket is writable
            socket.getOutputStream().write(0);
            socket.getOutputStream().flush();
            return true;
        } catch (java.io.IOException e) {
            Logger.error("Socket health check failed: " + e.getMessage());
            return false;
        }
    }

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
        } catch (Exception e) {
            Logger.error("[CLIENT] Failed to disable SSL validation: " + e.getMessage());
        }
        Logger.info("Forwarding request to LAN webserver: " + httpMethodType + " " + url);
        try {
            java.net.URI uri = java.net.URI.create(url);
            java.net.URL apiUrl = uri.toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod(httpMethodType);
            conn.setRequestProperty("Content-Type", "application/json");
            if (!httpMethodType.equals("GET")) {
                conn.setDoOutput(true);
                try (java.io.PrintWriter writer = new java.io.PrintWriter(conn.getOutputStream())) {
                    writer.print(body);
                }
            }
            Logger.info("Request method: " + httpMethodType);
            Logger.info("Request body: " + body);

            int status = conn.getResponseCode();
            Logger.info("Response code: " + status);

            // Collect headers
            StringBuilder headers = new StringBuilder();
            for (int i = 1;; i++) {
                String headerKey = conn.getHeaderFieldKey(i);
                String headerValue = conn.getHeaderField(i);
                if (headerKey == null && headerValue == null) break;
                if (headerKey != null && headerValue != null) {
                    headers.append(headerKey).append(": ").append(headerValue).append("\n");
                }
            }

            // Read the response as bytes (for binary data like PDF)
            java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            is.close();
            // Encode the bytes as Base64 to safely transmit binary data
            String base64Body = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
            // Return both headers and base64-encoded body
            return "Headers:\n" + headers + "\nBody-Base64:\n" + base64Body;
        } catch (Exception e) {
            return "LAN webserver error: " + e.getMessage();
        }
    }
}
