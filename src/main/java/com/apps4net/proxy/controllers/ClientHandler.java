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
     * 1. Initializes object streams for communication
     * 2. Receives and registers the client name
     * 3. Keeps the connection alive for request/response operations
     * 4. Handles cleanup when the connection is terminated
     * 
     * The actual request/response handling is performed by the
     * {@link #sendRequestAndGetResponse(ProxyRequest)} method.
     */
    public void run() {
        try {
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
}
