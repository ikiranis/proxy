package com.apps4net.proxy;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
             ObjectOutputStream objectOut = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream objectIn = new ObjectInputStream(socket.getInputStream())) {

            // First message from client should be its name
            objectOut.writeObject(clientName);
            objectOut.flush();
            System.out.println("[CLIENT] Sent client name: " + clientName);
            while (true) {
                // Wait for ProxyRequest from server
                Object obj = objectIn.readObject();
                if (!(obj instanceof com.apps4net.proxy.shared.ProxyRequest)) {
                    System.err.println("[CLIENT] Received unknown object from server: " + obj);
                    continue;
                }
                com.apps4net.proxy.shared.ProxyRequest proxyRequest = (com.apps4net.proxy.shared.ProxyRequest) obj;
                System.out.println("[CLIENT] Received ProxyRequest: " + proxyRequest.getHttpMethodType() + " " + proxyRequest.getUrl());
                // Perform the API call
                String responseBody = forwardToLanWebserver(proxyRequest.getHttpMethodType(), proxyRequest.getUrl(), proxyRequest.getBody());
                // For demo, always return 200
                com.apps4net.proxy.shared.ProxyResponse proxyResponse = new com.apps4net.proxy.shared.ProxyResponse(200, responseBody);
                objectOut.writeObject(proxyResponse);
                objectOut.flush();
                System.out.println("[CLIENT] Sent ProxyResponse to server");
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Forwards an HTTP request to the LAN webserver using the provided method, URL, and body.
     * Returns the response body as a string, or an error message if the request fails.
     *
     * @param httpMethodType The HTTP method (GET, POST, etc.)
     * @param url The URL to forward the request to
     * @param body The request body (may be null or empty for GET)
     * @return The response body from the LAN webserver, or an error message
     */
    private String forwardToLanWebserver(String httpMethodType, String url, String body) {
        // Disable SSL certificate validation (INSECURE: for development only)
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("[CLIENT] Failed to disable SSL validation: " + e.getMessage());
        }
        System.out.println("Forwarding request to LAN webserver: " + httpMethodType + " " + url);
        try {
            // Use the provided local API endpoint
            java.net.URL apiUrl = new java.net.URL(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod(httpMethodType);
            conn.setRequestProperty("Content-Type", "application/json");
            if (!httpMethodType.equals("GET")) {
                conn.setDoOutput(true);
                try (java.io.PrintWriter writer = new java.io.PrintWriter(conn.getOutputStream())) {
                    writer.print(body);
                }
            }
            System.out.println("Request method: " + httpMethodType);
            System.out.println("Request body: " + body);

            // Read the response
            int status = conn.getResponseCode();

            System.out.println("Response code: " + status);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()
            ));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            reader.close();
            return response.toString().trim();
        } catch (Exception e) {
            return "LAN webserver error: " + e.getMessage();
        }
    }
}
