package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.apps4net.proxy.services.ProxyService;
import com.apps4net.proxy.shared.ProxyRequest;
import com.apps4net.proxy.shared.ProxyResponse;

@RestController
public class GeneralController {
    private final ProxyService proxyService;

    @Autowired
    public GeneralController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping(path = "/api/forward", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> forwardToClient(@RequestBody ProxyRequest proxyRequest) {
        // Check if client is connected before forwarding the request
        String clientName = proxyRequest.getClientName();
        if (!proxyService.isClientConnected(clientName)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"Client not connected\", \"clientName\": \"" + clientName + "\"}");
        }
        
        try {
            ProxyResponse response = proxyService.forwardToClient(proxyRequest);
            // If the response body contains headers and base64 body, parse and set headers
            if (response.getBody() != null && response.getBody().startsWith("Headers:\n")) {
                String[] parts = response.getBody().split("\\nBody-Base64:\\n", 2);
                if (parts.length == 2) {
                    String headersBlock = parts[0].replaceFirst("Headers:\\n", "");
                    String base64Body = parts[1];
                    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
                    for (String headerLine : headersBlock.split("\\n")) {
                        int idx = headerLine.indexOf(": ");
                        if (idx > 0) {
                            String headerName = headerLine.substring(0, idx);
                            String headerValue = headerLine.substring(idx + 2);
                            builder.header(headerName, headerValue);
                        }
                    }
                    // Decode base64 body
                    byte[] bodyBytes = java.util.Base64.getDecoder().decode(base64Body);
                    return builder.body(bodyBytes);
                }
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            if (e.getMessage().contains("Client not connected")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"error\": \"Client not connected\", \"message\": \"" + e.getMessage() + "\"}");
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }
}
