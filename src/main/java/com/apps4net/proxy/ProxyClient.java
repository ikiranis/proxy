package com.apps4net.proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;

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
                // Forward the request to the LAN webserver and get the response
                String response = forwardToLanWebserver(request);
                out.println(response);
                System.out.println("Sent response: " + response);
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String forwardToLanWebserver(String requestData) {
        System.out.println("Forwarding request to LAN webserver: " + requestData);
        try {
            // Change the URL to your LAN webserver endpoint
            URL url = new URL("http://0.0.0.0:7777/api/general/appAlive");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            // Extract HTTP method type from requestData (e.g., "GET", "POST", etc.)
            String httpMethodType = "POST"; // default
            if (requestData != null && !requestData.isEmpty()) {
                String[] parts = requestData.split("\\s+");
                if (parts.length > 0) {
                    httpMethodType = parts[0].toUpperCase();
                }
            }
            conn.setRequestMethod(httpMethodType);
            conn.setRequestProperty("Content-Type", "application/json");
            if (!httpMethodType.equals("GET")) {
                conn.setDoOutput(true);
                try (PrintWriter writer = new PrintWriter(conn.getOutputStream())) {
                    writer.print(requestData);
                }
            }
            int status = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()
            ));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            return response.toString();
        } catch (Exception e) {
            return "LAN webserver error: " + e.getMessage();
        }
    }
}
