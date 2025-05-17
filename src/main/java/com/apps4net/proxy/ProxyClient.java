package com.apps4net.proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ProxyClient {
    private final String clientName;
    private final String serverHost;
    private final int serverPort;

    public ProxyClient(String clientName, String serverHost, int serverPort) {
        this.clientName = clientName;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }

    public void start() {
        try (Socket socket = new Socket(serverHost, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Send client name as first message
            out.println(clientName);
            System.out.println("Connected to server as '" + clientName + "'. Waiting for requests...");

            String request;
            while ((request = in.readLine()) != null) {
                System.out.println("Received request: " + request);
                // Here you can add your business logic to process the request
                String response = "Echo: " + request; // Example: echo back
                out.println(response);
                System.out.println("Sent response: " + response);
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
