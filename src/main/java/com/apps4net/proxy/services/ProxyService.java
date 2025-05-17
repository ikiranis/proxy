package com.apps4net.proxy.services;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import com.apps4net.proxy.controllers.ClientHandler;

@Service
public class ProxyService {
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static boolean socketServerStarted = false;

    public ProxyService() {
        startSocketServerOnce();
    }

    private synchronized void startSocketServerOnce() {
        if (!socketServerStarted) {
            socketServerStarted = true;
            new Thread(() -> {
                try (ServerSocket serverSocket = new ServerSocket(5000)) {
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket, clients);
                        handler.start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    public String forwardToClient(String clientName, String requestData) throws Exception {
        ClientHandler client = clients.get(clientName);
        if (client == null) {
            throw new Exception("Client not connected");
        }
        return client.sendRequestAndGetResponse(requestData);
    }

    public boolean isClientConnected(String clientName) {
        return clients.containsKey(clientName);
    }
}
