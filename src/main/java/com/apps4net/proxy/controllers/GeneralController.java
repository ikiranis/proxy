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

/**
 * REST controller responsible for handling HTTP requests to the proxy server.
 * 
 * This controller provides endpoints for forwarding HTTP requests to connected
 * proxy clients. It validates client connections before forwarding requests and
 * returns appropriate JSON responses for success and error cases.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
@RestController
public class GeneralController {
    private final ProxyService proxyService;

    /**
     * Constructs a new GeneralController with the specified ProxyService.
     * 
     * @param proxyService the service that handles proxy operations and client management
     */
    @Autowired
    public GeneralController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Forwards HTTP requests to connected proxy clients.
     * 
     * This endpoint accepts JSON-formatted proxy requests and forwards them to the
     * specified client. It performs connection validation before forwarding and handles
     * response parsing including HTTP headers and base64-encoded binary content.
     * 
     * Response handling supports:
     * - Plain text responses
     * - Structured responses with HTTP headers
     * - Base64-encoded binary content (e.g., PDFs, images)
     * 
     * @param proxyRequest the request to forward, containing client name, HTTP method, URL, and body
     * @return ResponseEntity containing the response from the target client, or error information
     *         - 200: Successful response from client
     *         - 404: Client not connected
     *         - 500: Internal server error during forwarding
     * 
     * @see ProxyRequest
     * @see ProxyResponse
     */
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
