package com.apps4net.proxy.services;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
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
    
    @Value("${proxy.auth.token}")
    private String authToken;
    
    @Value("${proxy.socket.port:5000}")
    private int socketPort;

    /**
     * Constructs a new ProxyService.
     * 
     * Socket server initialization is deferred to @PostConstruct to ensure
     * all Spring @Value properties are properly injected first.
     */
    public ProxyService() {
        // Socket server will be started in initializeService() after dependency injection
    }

    /**
     * Initializes the service after Spring dependency injection is complete.
     * 
     * This method is called automatically by Spring after all @Value properties
     * have been injected, ensuring the socket server starts with correct configuration.
     */
    @PostConstruct
    public void initializeService() {
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
            Logger.info("=== SOCKET SERVER INITIALIZATION ===");
            Logger.info("Starting socket server on port " + socketPort);
            Logger.info("Authentication token configured: " + (authToken != null && !authToken.trim().isEmpty() ? "YES" : "NO"));
            
            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(socketPort)) {
                    Logger.info("=== SOCKET SERVER STARTED SUCCESSFULLY ===");
                    Logger.info("Listening on port " + socketPort + " for proxy client connections");
                    Logger.info("Server socket bound to: " + serverSocket.getLocalSocketAddress());
                    Logger.info("Socket server is ready to accept connections");
                    Logger.info("Note: This port is for authenticated Java clients only");
                    Logger.info("==========================================");
                    
                    while (true) {
                        Logger.info("Waiting for client connections...");
                        Socket clientSocket = serverSocket.accept();
                        String clientAddress = clientSocket.getRemoteSocketAddress().toString();
                        Logger.info("=== NEW CLIENT CONNECTION ===");
                        Logger.info("Connection from: " + clientAddress);
                        Logger.info("Local socket: " + clientSocket.getLocalSocketAddress());
                        Logger.info("Connection established at: " + java.time.LocalDateTime.now());
                        Logger.info("Starting ClientHandler thread...");
                        
                        ClientHandler handler = new ClientHandler(clientSocket, clients, authToken);
                        handler.start();
                        
                        Logger.info("ClientHandler thread started for: " + clientAddress);
                        Logger.info("=============================");
                    }
                } catch (IOException e) {
                    Logger.error("=== SOCKET SERVER ERROR ===");
                    Logger.error("Failed to start or maintain socket server on port " + socketPort);
                    Logger.error("Error details: " + e.getMessage());
                    Logger.error("Error type: " + e.getClass().getSimpleName());
                    
                    if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                        Logger.error("DIAGNOSIS: Port " + socketPort + " is already in use");
                        Logger.error("SOLUTIONS:");
                        Logger.error("  1. Check if another proxy server instance is running");
                        Logger.error("  2. Check for other applications using port " + socketPort);
                        Logger.error("  3. Use 'netstat -ln | grep " + socketPort + "' to see what's using the port");
                        Logger.error("  4. Change the port in application.properties: proxy.socket.port=<new-port>");
                    } else if (e.getMessage() != null && e.getMessage().contains("Permission denied")) {
                        Logger.error("DIAGNOSIS: Permission denied - cannot bind to port " + socketPort);
                        Logger.error("SOLUTIONS:");
                        Logger.error("  1. Use a port number > 1024 (non-privileged port)");
                        Logger.error("  2. Run with sudo if port < 1024 is required");
                        Logger.error("  3. Check firewall settings");
                    }
                    
                    Logger.error("Socket server will be marked as failed");
                    Logger.error("Full stack trace:", e);
                    Logger.error("===========================");
                    socketServerStarted = false; // Allow restart on next service creation
                }
            }, "ProxyServer-SocketListener").start();
        } else {
            Logger.info("Socket server already started - skipping initialization");
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
        
        try {
            return client.sendRequestAndGetResponse(proxyRequest);
        } catch (Exception e) {
            // If communication fails due to unhealthy connection, remove client from pool
            if (e.getMessage() != null && e.getMessage().contains("connection is unhealthy")) {
                Logger.error("Removing unhealthy client '" + clientName + "' from connection pool");
                clients.remove(clientName);
            }
            throw e; // Re-throw the original exception
        }
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

    /**
     * Gets the list of currently connected client names.
     * 
     * This method returns a list containing the names of all currently
     * connected proxy clients. It's useful for monitoring which specific
     * clients are active and for health check reporting.
     * 
     * @return a list of client names for all currently connected clients
     * 
     * @see GeneralController#healthCheck()
     */
    public java.util.List<String> getConnectedClientNames() {
        return new java.util.ArrayList<>(clients.keySet());
    }

    /**
     * Performs health checks on all connected clients and removes unhealthy connections.
     * 
     * This method can be called periodically to clean up zombie connections that
     * appear connected but are actually broken, preventing slow timeout errors.
     * 
     * @return the number of unhealthy connections that were removed
     */
    public int cleanupUnhealthyConnections() {
        int removedCount = 0;
        java.util.Iterator<Map.Entry<String, ClientHandler>> iterator = clients.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, ClientHandler> entry = iterator.next();
            String clientName = entry.getKey();
            ClientHandler handler = entry.getValue();
            
            try {
                // Test if the handler's connection is still healthy
                Socket socket = handler.getSocket();
                if (socket == null || socket.isClosed() || 
                    !socket.isConnected() || socket.isInputShutdown() || 
                    socket.isOutputShutdown()) {
                    
                    Logger.info("Removing unhealthy client '" + clientName + "' during cleanup");
                    iterator.remove();
                    removedCount++;
                }
            } catch (Exception e) {
                Logger.error("Error checking health of client '" + clientName + "': " + e.getMessage());
                Logger.info("Removing problematic client '" + clientName + "' during cleanup");
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            Logger.info("Connection cleanup completed: removed " + removedCount + " unhealthy connections");
        }
        
        return removedCount;
    }
}
