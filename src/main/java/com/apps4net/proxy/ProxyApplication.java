package com.apps4net.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.apps4net.proxy.utils.Logger;

/**
 * Main application class for the Java Proxy Server and Client.
 * 
 * This application can run in two modes:
 * 
 * Server Mode (Default)
 * Starts a Spring Boot web server that:
 * - Provides REST API endpoints for forwarding HTTP requests
 * - Manages socket connections from proxy clients
 * - Handles request routing and response processing
 * 
 * Client Mode
 * Starts a proxy client that:
 * - Connects to a proxy server via socket
 * - Forwards HTTP requests to local/LAN web servers
 * - Returns responses back to the proxy server
 * - Includes automatic reconnection and health monitoring
 * 
 * Usage Examples:
 * // Server mode (default)
 * java -jar proxy-0.1.jar
 * 
 * // Client mode with default settings
 * java -jar proxy-0.1.jar client
 * 
 * // Client mode with custom parameters
 * java -jar proxy-0.1.jar client my-client-name server.example.com 5000 my-auth-token
 * 
 * Note: Authentication token must match the server's proxy.auth.token configuration
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class ProxyApplication {

	/**
	 * Main entry point for the proxy application.
	 * 
	 * Command line arguments:
	 * - No arguments: Starts in server mode
	 * - args[0] = "client": Starts in client mode with optional parameters:
	 *   - args[1]: Client name (default: "default-client")
	 *   - args[2]: Server host (default: "localhost")
	 *   - args[3]: Server port (default: 5000)
	 * 
	 * The client mode includes a shutdown hook for graceful termination
	 * when the process receives SIGTERM or SIGINT signals.
	 * 
	 * @param args command line arguments for mode selection and configuration
	 * 
	 * @see ProxyClient
	 * @see org.springframework.boot.SpringApplication#run(Class, String...)
	 */
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("client")) {
			String clientName = (args.length > 1) ? args[1] : "default-client";
			String serverHost = (args.length > 2) ? args[2] : "localhost";
			int serverPort = (args.length > 3) ? Integer.parseInt(args[3]) : 5000;
			String authToken = (args.length > 4) ? args[4] : "apps4net-proxy-secure-token-2025";
			
			Logger.info("=====================================");
			Logger.info("    PROXY CLIENT STARTING");
			Logger.info("=====================================");
			Logger.info("Client Name: " + clientName);
			Logger.info("Server Host: " + serverHost);
			Logger.info("Server Port: " + serverPort);
			Logger.info("Auth Token: " + (authToken != null ? "***configured***" : "none"));
			Logger.info("=====================================");
			
			// Validate basic configuration before starting
			if (serverHost == null || serverHost.trim().isEmpty()) {
				Logger.error("CONFIGURATION ERROR: Server host cannot be empty");
				Logger.error("Usage: java -jar proxy.jar client [client-name] [server-host] [server-port] [auth-token]");
				return;
			}
			
			if (serverPort <= 0 || serverPort > 65535) {
				Logger.error("CONFIGURATION ERROR: Invalid port number: " + serverPort);
				Logger.error("Port must be between 1 and 65535");
				return;
			}
			
			if (authToken == null || authToken.trim().isEmpty()) {
				Logger.error("CONFIGURATION ERROR: Authentication token cannot be empty");
				Logger.error("Token must match server's proxy.auth.token configuration");
				return;
			}
			
			Logger.info("Configuration validation passed - starting client...");
			
			ProxyClient client = new ProxyClient(clientName, serverHost, serverPort, authToken);
			
			// Add shutdown hook for graceful termination
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				Logger.info("=====================================");
				Logger.info("    PROXY CLIENT SHUTTING DOWN");
				Logger.info("=====================================");
				Logger.info("Received shutdown signal, stopping client gracefully...");
				client.stop();
				Logger.info("Client shutdown complete");
			}));
			
			try {
				client.start();
			} catch (Exception e) {
				Logger.error("FATAL ERROR: Client failed to start: " + e.getMessage(), e);
				Logger.error("=====================================");
				Logger.error("    CLIENT STARTUP FAILED");
				Logger.error("=====================================");
				Logger.error("Check the error messages above for troubleshooting guidance");
				System.exit(1);
			}
			return;
		}
		// Default: server mode
		SpringApplication.run(ProxyApplication.class, args);
		Logger.info("Run app on http://localhost:9990");
	}

}
