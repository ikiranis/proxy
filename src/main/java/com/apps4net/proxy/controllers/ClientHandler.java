package com.apps4net.proxy.controllers;

import java.net.Socket;
import java.util.Map;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import com.apps4net.proxy.utils.Logger;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

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
    private String clientName;
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;

    /**
     * Creates a new ClientHandler for managing communication with a connected client.
     * 
     * @param socket the socket connection to the client
     * @param clients the shared map of all connected clients for registration management
     */
    public ClientHandler(Socket socket, Map<String, ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
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
        try {
            // First, detect if this is an HTTP request by reading the first few bytes
            socket.getInputStream().mark(4);
            byte[] buffer = new byte[4];
            int bytesRead = socket.getInputStream().read(buffer);
            
            if (bytesRead >= 3) {
                String header = new String(buffer, 0, Math.min(bytesRead, 4));
                if (header.startsWith("GET") || header.startsWith("POST") || 
                    header.startsWith("PUT") || header.startsWith("HEAD") || 
                    header.startsWith("DELE") || header.startsWith("OPTI")) {
                    
                    // This is an HTTP request, not a proxy client
                    handleHttpRequest();
                    return;
                }
            }
            
            // Reset the stream to the beginning for normal object stream processing
            socket.getInputStream().reset();
            
            // Initialize object streams for proxy client communication
            objectOut = new ObjectOutputStream(socket.getOutputStream());
            objectIn = new ObjectInputStream(socket.getInputStream());
            
            // First message from client should be its name
            clientName = (String) objectIn.readObject();
            clients.put(clientName, this);
            Logger.info("Client '" + clientName + "' connected.");
            
            // No request/response loop here; handled by sendRequestAndGetResponse
            // Just keep the thread alive until socket closes
            while (!socket.isClosed()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        } catch (java.io.StreamCorruptedException e) {
            // Handle the specific case where someone tries to send HTTP to the socket port
            if (e.getMessage() != null && e.getMessage().contains("invalid stream header")) {
                Logger.error("Invalid protocol detected - HTTP request sent to socket server port");
                handleHttpRequest();
            } else {
                Logger.error("Stream corruption error", e);
            }
        } catch (Exception e) {
            Logger.error("Client handler error", e);
        } finally {
            if (clientName != null) clients.remove(clientName);
            try { socket.close(); } catch (java.io.IOException ignored) {}
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
    // Send request to client and wait for response
    public synchronized ProxyResponse sendRequestAndGetResponse(ProxyRequest proxyRequest) throws Exception {
        Logger.info("Sending ProxyRequest to client '" + clientName + "': " + proxyRequest);
        objectOut.writeObject(proxyRequest);
        objectOut.flush();
        // Wait for ProxyResponse
        ProxyResponse proxyResponse = (ProxyResponse) objectIn.readObject();
        return proxyResponse;
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
                "  \"message\": \"This port (5000) is for proxy client connections using Java object serialization.\",\n" +
                "  \"instructions\": \"For HTTP requests, use the REST API at /api/forward on the web server port (9990).\",\n" +
                "  \"example\": \"POST http://localhost:9990/api/forward\"\n" +
                "}\n";
            
            socket.getOutputStream().write(httpResponse.getBytes());
            socket.getOutputStream().flush();
            Logger.info("Responded to HTTP request with protocol error message");
        } catch (Exception e) {
            Logger.error("Failed to send HTTP error response", e);
        }
    }
}
