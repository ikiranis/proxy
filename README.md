# Proxy App

This application acts as a proxy between HTTP clients and internal clients connected via sockets. It can run in two modes: **server** and **client**.

![proxy](https://github.com/user-attachments/assets/2d7f2b50-1850-4869-a601-525a81949a34)

## How It Works

- **Server Mode**:
  - Runs a Spring Boot REST API.
  - Accepts HTTP POST requests at `/api/forward` with a JSON body specifying `clientName`, `httpMethodType`, `url`, and (optionally) `body`.
  - Forwards the request as a `ProxyRequest` object to the specified client (connected via socket).
  - Waits for the client to process and respond with a `ProxyResponse` object, then returns the response to the original HTTP requester.
  - Manages multiple clients, each identified by a unique name.

- **Client Mode**:
  - Connects to the server via socket.
  - Registers itself with a unique client name (sent as the first object).
  - Waits for `ProxyRequest` objects from the server, processes them (performs the API call in its LAN), and sends back `ProxyResponse` objects.

## Usage

### Build the Application

```
mvn clean package
```

### Run in Server Mode

```
java -jar target/proxy-0.1.jar server
```
- The server will start the REST API (default port: 9999) and listen for client socket connections (default port: 5000).

### Run in Client Mode

```
java -jar target/proxy-0.1.jar client <clientName> <serverHost> <serverPort> <authToken>
```
- `clientName`: Unique name for this client (required)
- `serverHost`: Hostname or IP of the server (default: localhost)
- `serverPort`: Port of the server's socket listener (default: 5000)
- `authToken`: Authentication token matching server configuration (default: apps4net-proxy-secure-token-2025)

**Example:**
```
java -jar target/proxy-0.1.jar client myClient public.server.com 5000 my-secure-token-123
```

**Note:** The authentication token must match the `proxy.auth.token` value configured in the server's `application.properties` file.

## Making a POST Request to the Server

Send a POST request to the server's `/api/forward` endpoint with a JSON body. **Authentication is required** - include your admin API key in the Authorization header:

```
POST http://localhost:9999/api/forward
Authorization: Bearer your-admin-api-key
Content-Type: application/json

{
  "clientName": "myClient",
  "httpMethodType": "GET",
  "url": "http://lan-server/api",
  "body": ""
}
```

The server will forward this request to the specified client, which will perform the API call and return the result.

## Admin API

The server provides administrative endpoints for security management and monitoring. These endpoints require authentication with an admin API key.

### Authentication

Admin endpoints require a valid API key in the `Authorization` header. Configure the admin API key in your `application.properties`:

```properties
proxy.admin.api.key=your-secure-admin-key-here
```

### Admin Endpoints

#### Security Management
```
POST /api/admin/security
Authorization: Bearer your-admin-api-key
Content-Type: application/json

{
  "action": "ban",
  "ip": "192.168.1.100"
}
```

Available actions:
- `ban`: Ban an IP address
- `unban`: Remove an IP from the ban list and clear all tracking data
- `status`: Get current security status
- `check`: Check the ban status and auto-ban criteria for a specific IP

#### Security Status (Authentication Required)
```
GET /api/security-status
Authorization: Bearer your-admin-api-key
```

Returns information about banned IPs, threat detection statistics, and security configuration.

#### Server Health Check (No Auth Required)
```
GET /api/health
```

Returns server health information including connected clients and uptime.

### Example Admin Requests

**Ban an IP:**
```bash
curl -X POST http://localhost:9999/api/admin/security \
  -H "Authorization: Bearer your-admin-api-key" \
  -H "Content-Type: application/json" \
  -d '{"action": "ban", "ip": "192.168.1.100"}'
```

**Unban an IP:**
```bash
curl -X POST http://localhost:9999/api/admin/security \
  -H "Authorization: Bearer your-admin-api-key" \
  -H "Content-Type: application/json" \
  -d '{"action": "unban", "ip": "192.168.1.100"}'
```

**Check IP ban status and auto-ban criteria:**
```bash
curl -X POST http://localhost:9999/api/admin/security \
  -H "Authorization: Bearer your-admin-api-key" \
  -H "Content-Type: application/json" \
  -d '{"action": "check", "ip": "192.168.1.100"}'
```

**Get security status:**
```bash
curl -X GET http://localhost:9999/api/security-status \
  -H "Authorization: Bearer your-admin-api-key"
```

**Forward a request to a client:**
```bash
curl -X POST http://localhost:9999/api/forward \
  -H "Authorization: Bearer your-admin-api-key" \
  -H "Content-Type: application/json" \
  -d '{"clientName": "myClient", "httpMethodType": "GET", "url": "http://lan-server/api", "body": ""}'
```

## Project Structure

- `ProxyApplication.java`: Main entry point, handles mode selection.
- `ProxyClient.java`: Client mode logic.
- `controllers/GeneralController.java`: REST API controller.
- `controllers/ClientHandler.java`: Handles socket communication with a client.
- `services/ProxyService.java`: Business logic and socket server for server mode.
- `shared/ProxyRequest.java`, `shared/ProxyResponse.java`: Serializable request/response objects for robust communication.

## Security System

The proxy server includes an advanced security system to protect against malicious activity:

### Auto-Ban Protection
- **Automatic IP Banning**: IPs are automatically banned after 5 authentication failures within a 15-minute window
- **Progressive Penalties**: After 15 total authentication failures across all time, IPs receive permanent bans
- **Grace Period System**: Recently unbanned IPs receive a 30-minute grace period before auto-ban rules reapply
- **Manual Override**: Administrators can manually ban/unban specific IPs via the admin API

### Security Configuration
The security system operates with the following default thresholds:
- Max auth failures before ban: 5 attempts
- Time window for tracking: 15 minutes
- Permanent ban threshold: 15 total failures
- Grace period for unbanned IPs: 30 minutes

### Enhanced Error Handling
The proxy system includes robust error handling for reliable communication:
- **Serialization Protection**: Automatic detection and recovery from object stream corruption
- **Response Size Limits**: 50MB limit to prevent oversized responses from causing issues
- **Socket Health Monitoring**: Intelligent connection health checks without stream interference
- **Detailed Error Logging**: Comprehensive error classification and troubleshooting information

## Debugging and Troubleshooting

### Enhanced Client Debugging

The proxy client now includes comprehensive debugging information to help troubleshoot connection issues:

#### Pre-Connection Diagnostics
When starting, the client performs automatic diagnostics:
- Configuration validation (host, port, auth token)
- DNS resolution testing
- Host reachability checks (ICMP ping)
- Port connectivity testing
- Network configuration verification

#### Connection Failure Analysis
When connection failures occur, the client provides detailed analysis:
- **Connection Refused**: Server not running, wrong port, or firewall blocking
- **Timeout Issues**: Network latency, server overload, or routing problems
- **DNS Problems**: Hostname resolution failures or DNS configuration issues
- **Authentication Failures**: Token mismatches or server configuration errors
- **Network Errors**: Connection resets, unreachable networks, or socket issues

#### Detailed Logging
The client now logs extensive information during operation:
- Socket connection establishment details
- Authentication protocol steps
- Object stream creation and communication
- Request/response processing with timing
- LAN webserver communication details
- Error classification with troubleshooting steps

#### Example Debug Output
```
[2025-05-29 10:30:15.123] [INFO] =====================================
[2025-05-29 10:30:15.124] [INFO]     PROXY CLIENT STARTING
[2025-05-29 10:30:15.125] [INFO] =====================================
[2025-05-29 10:30:15.126] [INFO] Client Name: my-client
[2025-05-29 10:30:15.127] [INFO] Server Host: proxy.example.com
[2025-05-29 10:30:15.128] [INFO] Server Port: 5000
[2025-05-29 10:30:15.129] [INFO] Auth Token: ***configured***
[2025-05-29 10:30:15.130] [INFO] =====================================
[2025-05-29 10:30:15.131] [INFO] Configuration validation passed - starting client...
[2025-05-29 10:30:15.132] [INFO] === ProxyClient Starting ===
[2025-05-29 10:30:15.133] [INFO] === Pre-Connection Diagnostics ===
[2025-05-29 10:30:15.150] [INFO] DIAGNOSTIC: DNS resolution successful - proxy.example.com -> 192.168.1.100
[2025-05-29 10:30:15.200] [INFO] DIAGNOSTIC: Host is reachable via ICMP
[2025-05-29 10:30:15.250] [INFO] DIAGNOSTIC: Port 5000 is accessible
[2025-05-29 10:30:15.251] [INFO] === Diagnostics Complete ===
```

### Common Issues and Solutions

#### "Connection refused" Error
**Symptoms**: Client cannot connect to server
**Possible Causes**:
- Proxy server is not running
- Wrong server host or port
- Firewall blocking connection
- Server listening on wrong interface

**Solutions**:
1. Verify server is running: `java -jar proxy-1.0.jar`
2. Test connectivity: `telnet <server-host> <server-port>`
3. Check firewall settings
4. Verify server configuration in `application.properties`

#### "Authentication failed" Error
**Symptoms**: Client connects but authentication fails
**Possible Causes**:
- Token mismatch between client and server
- Whitespace or encoding issues in token
- Server authentication disabled

**Solutions**:
1. Check client token matches `proxy.auth.token` in server config
2. Ensure no extra spaces or characters in token
3. Verify server has authentication enabled

#### "DNS resolution failed" Error
**Symptoms**: Cannot resolve server hostname
**Solutions**:
1. Use IP address instead of hostname
2. Check DNS configuration
3. Test with: `nslookup <server-host>`
