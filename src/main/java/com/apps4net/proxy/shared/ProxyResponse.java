package com.apps4net.proxy.shared;

import java.io.Serializable;

/**
 * Represents a response from a proxied HTTP request.
 * This class encapsulates the HTTP status code and response body
 * returned from a client after processing a ProxyRequest.
 */
public class ProxyResponse implements Serializable {
    private int statusCode;
    private String body;

    /**
     * Constructs a ProxyResponse with the given status code and body.
     * 
     * @param statusCode The HTTP status code (e.g., 200, 404, 500)
     * @param body The response body, which may include headers and base64-encoded content
     */
    public ProxyResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    /**
     * Gets the HTTP status code of this response.
     * 
     * @return The HTTP status code
     */
    public int getStatusCode() { return statusCode; }
    
    /**
     * Gets the response body.
     * 
     * @return The response body, potentially containing headers and base64-encoded content
     */
    public String getBody() { return body; }

    /**
     * Sets the HTTP status code for this response.
     * 
     * @param statusCode The HTTP status code to set
     */
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    
    /**
     * Sets the response body.
     * 
     * @param body The response body to set
     */
    public void setBody(String body) { this.body = body; }
}
