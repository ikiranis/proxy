package com.apps4net.proxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import com.apps4net.proxy.utils.Logger;

public class ProxyClient {
    private final String clientName;
    private final String serverHost;
    private final int serverPort;
    private volatile boolean isRunning = false;
    private static final int RECONNECT_DELAY_MS = 5000; // 5 seconds
    private static final int HEARTBEAT_INTERVAL_MS = 30000; // 30 seconds

    public ProxyClient(String clientName, String serverHost, int serverPort) {
        this.clientName = clientName;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

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

    public void stop() {
        isRunning = false;
    }

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
