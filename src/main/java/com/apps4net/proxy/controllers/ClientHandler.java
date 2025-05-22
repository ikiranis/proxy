package com.apps4net.proxy.controllers;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final Map<String, ClientHandler> clients;
    private String clientName;
    private final BlockingQueue<String> responseQueue = new ArrayBlockingQueue<>(1);
    private ObjectOutputStream objectOut;
    private ObjectInputStream objectIn;

    public ClientHandler(Socket socket, Map<String, ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
    }

    public void run() {
        try {
            objectOut = new ObjectOutputStream(socket.getOutputStream());
            objectIn = new ObjectInputStream(socket.getInputStream());
            // First message from client should be its name
            clientName = (String) objectIn.readObject();
            clients.put(clientName, this);
            System.out.println("Client '" + clientName + "' connected.");
            // No request/response loop here; handled by sendRequestAndGetResponse
            // Just keep the thread alive until socket closes
            while (!socket.isClosed()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (clientName != null) clients.remove(clientName);
            try { socket.close(); } catch (java.io.IOException ignored) {}
        }
    }

    // Send request to client and wait for response
    public synchronized ProxyResponse sendRequestAndGetResponse(ProxyRequest proxyRequest) throws Exception {
        System.out.println("Sending ProxyRequest to client '" + clientName + "': " + proxyRequest);
        objectOut.writeObject(proxyRequest);
        objectOut.flush();
        // Wait for ProxyResponse
        ProxyResponse proxyResponse = (ProxyResponse) objectIn.readObject();
        return proxyResponse;
    }
}
