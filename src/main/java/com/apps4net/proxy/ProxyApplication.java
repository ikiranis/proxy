package com.apps4net.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
 * java -jar proxy-0.1.jar client my-client-name server.example.com 5000
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
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
			Logger.info("Starting in CLIENT mode as '" + clientName + "' connecting to " + serverHost + ":" + serverPort);
			
			ProxyClient client = new ProxyClient(clientName, serverHost, serverPort);
			
			// Add shutdown hook for graceful termination
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				Logger.info("Shutting down client gracefully...");
				client.stop();
			}));
			
			client.start();
			return;
		}
		// Default: server mode
		SpringApplication.run(ProxyApplication.class, args);
		Logger.info("Run app on http://localhost:8444");
	}

}
