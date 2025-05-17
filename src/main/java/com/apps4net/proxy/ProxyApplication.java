package com.apps4net.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProxyApplication {

	public static void main(String[] args) {
		if (args.length > 0 && args[0].equalsIgnoreCase("client")) {
			String clientName = (args.length > 1) ? args[1] : "default-client";
			String serverHost = (args.length > 2) ? args[2] : "localhost";
			int serverPort = (args.length > 3) ? Integer.parseInt(args[3]) : 5000;
			System.out.println("Starting in CLIENT mode as '" + clientName + "' connecting to " + serverHost + ":" + serverPort);
			new ProxyClient(clientName, serverHost, serverPort).start();
			return;
		}
		// Default: server mode
		SpringApplication.run(ProxyApplication.class, args);
		System.out.println("Run app on http://localhost:9999");
	}

}
