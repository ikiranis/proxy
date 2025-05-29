package com.apps4net.proxy.shared;

import java.io.Serializable;

/**
 * Represents a request to be forwarded through the proxy system.
 * This class encapsulates all the information needed to make an HTTP request
 * on behalf of a client connected to the proxy server.
 */
public class ProxyRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String httpMethodType;
    private String url;
    private String body;
    private String clientName;

    /**
     * Constructs a ProxyRequest with all required parameters.
     * 
     * @param clientName The name of the client making the request
     * @param httpMethodType The HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param url The target URL for the request
     * @param body The request body (can be null for GET requests)
     */
    public ProxyRequest(String clientName, String httpMethodType, String url, String body) {
        this.clientName = clientName;
        this.httpMethodType = httpMethodType;
        this.url = url;
        this.body = body;
    }

    /**
     * Default no-argument constructor required for JSON deserialization.
     */
    public ProxyRequest() {
    }

    /**
     * Gets the HTTP method type for this request.
     * 
     * @return The HTTP method (e.g., "GET", "POST")
     */
    public String getHttpMethodType() { return httpMethodType; }
    
    /**
     * Gets the target URL for this request.
     * 
     * @return The URL to make the request to
     */
    public String getUrl() { return url; }
    
    /**
     * Gets the request body.
     * 
     * @return The request body, or null if no body is present
     */
    public String getBody() { return body; }
    
    /**
     * Gets the name of the client making this request.
     * 
     * @return The client name
     */
    public String getClientName() { return clientName; }

    /**
     * Sets the HTTP method type for this request.
     * 
     * @param httpMethodType The HTTP method to set
     */
    public void setHttpMethodType(String httpMethodType) { this.httpMethodType = httpMethodType; }
    
    /**
     * Sets the target URL for this request.
     * 
     * @param url The URL to set
     */
    public void setUrl(String url) { this.url = url; }
    
    /**
     * Sets the request body.
     * 
     * @param body The request body to set
     */
    public void setBody(String body) { this.body = body; }
    
    /**
     * Sets the client name for this request.
     * 
     * @param clientName The client name to set
     */
    public void setClientName(String clientName) { this.clientName = clientName; }
}
