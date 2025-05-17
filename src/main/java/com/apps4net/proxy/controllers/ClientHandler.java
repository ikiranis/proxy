package com.apps4net.proxy.controllers;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final Map<String, ClientHandler> clients;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    private CompletableFuture<String> responseFuture;

    public ClientHandler(Socket socket, Map<String, ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
    }

    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            // First message from client should be its name
            clientName = in.readLine();
            clients.put(clientName, this);
            String line;
            while ((line = in.readLine()) != null) {
                if (responseFuture != null) {
                    responseFuture.complete(line);
                    responseFuture = null;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (clientName != null) clients.remove(clientName);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // Send request to client and wait for response
    public synchronized String sendRequestAndGetResponse(String request) throws Exception {
        responseFuture = new CompletableFuture<>();
        out.println(request);
        return responseFuture.get();
    }
}
