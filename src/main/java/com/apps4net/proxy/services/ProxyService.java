package com.apps4net.proxy.services;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import com.apps4net.proxy.controllers.ClientHandler;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import com.apps4net.proxy.utils.Logger;

/**
 * Core service for managing proxy operations and client connections.
 * 
 * This service handles:
 * - Starting and managing the socket server for client connections
 * - Maintaining a registry of connected clients
 * - Forwarding requests to appropriate clients
 * - Providing connection status information
 * 
 * The service uses a singleton pattern for the socket server to ensure
 * only one server instance runs regardless of multiple service instances.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@Service
public class ProxyService {
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static boolean socketServerStarted = false;

    /**
     * Constructs a new ProxyService and ensures the socket server is started.
     * 
     * The socket server initialization is thread-safe and will only occur once
     * across all instances of this service.
     */
    public ProxyService() {
        startSocketServerOnce();
    }

    /**
     * Starts the socket server for accepting client connections.
     * 
     * This method ensures that only one socket server instance is created
     * across all ProxyService instances. The server listens on port 5000 and
     * creates a new ClientHandler thread for each incoming connection.
     * 
     * The socket server runs in a separate daemon thread to avoid blocking
     * the main application. Each client connection is handled asynchronously.
     * 
     * @see ClientHandler
     */
    private synchronized void startSocketServerOnce() {
        if (!socketServerStarted) {
            socketServerStarted = true;
            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(5000)) {
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket, clients);
                        handler.start();
                    }
                } catch (IOException e) {
                    Logger.error("Socket server error", e);
                }
            }).start();
        }
    }

    /**
     * Forwards a proxy request to the specified client and returns the response.
     * 
     * This method performs the core proxy functionality by:
     * 1. Looking up the target client by name
     * 2. Validating that the client is connected
     * 3. Delegating the request to the client's handler
     * 4. Returning the response from the client
     * 
     * @param proxyRequest the request to forward, containing client name, HTTP method, URL, and body
     * @return the response from the target client after processing the request
     * @throws Exception if the client is not connected or communication fails
     * 
     * @see ProxyRequest
     * @see ProxyResponse
     * @see ClientHandler#sendRequestAndGetResponse(ProxyRequest)
     */
    public ProxyResponse forwardToClient(ProxyRequest proxyRequest) throws Exception {
        String clientName = proxyRequest.getClientName();
        ClientHandler client = clients.get(clientName);

        Logger.info("Forwarding request to client '" + clientName + "': " + proxyRequest);

        if (client == null) {
            throw new Exception("Client not connected");
        }
        return client.sendRequestAndGetResponse(proxyRequest);
    }

    /**
     * Checks if a client with the specified name is currently connected.
     * 
     * This method provides a quick way to validate client availability
     * before attempting to forward requests. It's used by the REST controller
     * to provide early validation and appropriate error responses.
     * 
     * @param clientName the name of the client to check
     * @return true if the client is connected and available, false otherwise
     * 
     * @see GeneralController#forwardToClient(ProxyRequest)
     */
    public boolean isClientConnected(String clientName) {
        return clients.containsKey(clientName);
    }

    /**
     * Gets the current number of connected clients.
     * 
     * This method provides a count of all currently connected and active
     * proxy clients. It's useful for monitoring server load and capacity,
     * as well as for health check reporting.
     * 
     * @return the number of currently connected clients
     * 
     * @see GeneralController#healthCheck()
     */
    public int getConnectedClientCount() {
        return clients.size();
    }
}
