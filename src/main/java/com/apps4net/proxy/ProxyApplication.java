package com.apps4net.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.apps4net.proxy.utils.Logger;

@SpringBootApplication
public class ProxyApplication {

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
