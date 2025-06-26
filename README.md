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

## Version 1.3 Features

### Connection Logging and Monitoring

- **Comprehensive Connection Tracking**: Logs every client connect/disconnect event with timestamp, client name, and IP address
- **Admin Connection Log API**: Retrieve, filter, and manage connection logs via REST endpoints
- **Connection Statistics**: Track connection patterns, active clients, and historical data
- **Automatic Cleanup**: Scheduled cleanup of unhealthy connections every minute
- **Enhanced Health Validation**: Improved client-server connection health checks to prevent false positives

### Enhanced Admin API

- **Connection Log Management**: New endpoints for retrieving and clearing connection logs
- **Advanced Filtering**: Filter connection logs by event type, client name, or limit results
- **Real-time Statistics**: Access comprehensive connection statistics and metrics
- **Improved Security**: Robust admin authentication for all management endpoints

## Version 1.3 New Features

### Connection Event Logging System

Version 1.3 introduces a comprehensive connection logging system that provides detailed tracking of all client interactions:

#### Key Features

- **Real-time Event Logging**: Every client connect and disconnect is logged with precise timestamps
- **Detailed Metadata**: Logs include client name, IP address, event type, and reason for disconnection
- **Smart Filtering**: Avoid logging "unknown" disconnections for failed handshakes or invalid requests
- **Admin API Integration**: Full REST API access for log management and analysis
- **Memory Efficient**: Automatic log rotation with configurable limits (default: 1000 entries)
- **Statistical Analysis**: Built-in connection statistics and pattern analysis

#### Admin API Endpoints

- `GET /api/admin/connection-logs` - Retrieve logs with filtering options
- `POST /api/admin/connection-logs/clear` - Clear all stored logs
- Filter by event type, client name, or limit results
- Access comprehensive connection statistics

### Enhanced Connection Management

- **Automatic Scheduled Cleanup**: Connection health monitoring and cleanup runs every minute
- **Improved Health Validation**: More accurate connection health checks to prevent false positives
- **Better Error Handling**: Smarter detection of legitimate connection issues vs. security threats
- **Reduced Noise**: Eliminated unnecessary "unknown client" disconnect logs for normal operations

### Spring Boot Integration

- **Scheduled Tasks**: Enabled Spring's `@Scheduled` annotation for automatic maintenance
- **Component-based Architecture**: Connection logging implemented as a Spring `@Component`
- **Dependency Injection**: Full integration with Spring's dependency injection system

## Changelog from v1.2 to v1.3

### Added

- Complete connection event logging system with admin API
- Automatic scheduled cleanup every minute
- Connection statistics and analytics
- Smart filtering to avoid logging invalid connection attempts
- Memory-efficient log management with rotation

### Improved

- Enhanced connection health validation accuracy
- Better error classification and handling
- Reduced false positive disconnection logs
- More comprehensive admin API functionality

### Fixed

- Eliminated "unknown client" logs for normal failed connections
- Improved socket health checking to prevent false positives
- Better handling of connection cleanup during scheduled maintenance

## Client Mode Features

### Automatic Reconnection

The proxy client includes robust reconnection capabilities:

- **Automatic Retry**: Attempts to reconnect every 5 seconds when connection is lost
- **Heartbeat Monitoring**: Sends health checks every 30 seconds to maintain connection
- **Connection Health Detection**: Automatically detects and recovers from connection issues
- **External Control**: Supports external `forceReconnect()` and status checking via `isRunning()`

### Client Connection Management

- **Socket Health Monitoring**: Comprehensive connection testing without interfering with data streams
- **Zombie Connection Prevention**: Automatic detection and cleanup of unhealthy connections
- **Timeout Handling**: 30-second socket timeouts with intelligent error recovery
- **Connection Validation**: Pre-request health checks ensure reliable communication

### Improved Error Handling

- **Connection Classification**: Detailed analysis of connection failures with specific solutions
- **Stream Corruption Recovery**: Automatic detection and recovery from object serialization issues
- **Timeout Management**: Intelligent handling of socket timeouts and network delays
- **Debug Information**: Comprehensive logging for troubleshooting connection issues

### Server Mode Features

### Enhanced Health Monitoring

- **Real-time Client Tracking**: Monitor all connected clients by name and status
- **Connection Health Checks**: Automatic validation of client connections before forwarding requests
- **Zombie Connection Cleanup**: Automatic removal of unhealthy or abandoned connections
- **Administrative Cleanup**: Manual connection cleanup via admin API endpoints
- **Scheduled Maintenance**: Automatic connection cleanup runs every minute to maintain optimal performance

### Advanced Connection Management

- **Client Name Resolution**: Track and manage clients by their registered names
- **Connection Lifecycle Management**: Proper handling of client connect/disconnect events
- **Health Status Reporting**: Detailed health information including client lists and connection counts
- **Automatic Cleanup**: Intelligent removal of failed connections during normal operations
- **Connection Logging**: Comprehensive logging of all client connection events with timestamps and metadata

### Connection Event Logging

- **Real-time Event Tracking**: Every client connect/disconnect is logged with precise timestamps
- **Client Identification**: Track connections by client name and IP address
- **Event Categorization**: Separate logging for successful connections vs. failed attempts
- **Historical Data**: Maintain connection history for analysis and troubleshooting
- **Admin API Access**: Retrieve and manage connection logs via REST endpoints

### Timeout and Error Handling

- **Configurable Timeouts**: 30-second socket timeouts for reliable communication
- **Error Classification**: Detailed analysis of communication failures with appropriate responses
- **Response Size Limits**: 50MB response size limits to prevent resource exhaustion
- **Graceful Degradation**: Continued operation even when individual client connections fail

## Usage

### Build the Application

```bash
mvn clean package
```

### Run in Server Mode

```bash
java -jar target/proxy-1.3.jar server
```

- The server will start the REST API (default port: 9999) and listen for client socket connections (default port: 5000).

### Run in Client Mode

```bash
java -jar target/proxy-1.3.jar client <clientName> <serverHost> <serverPort> <authToken>
```

- `clientName`: Unique name for this client (required)
- `serverHost`: Hostname or IP of the server (default: localhost)
- `serverPort`: Port of the server's socket listener (default: 5000)
- `authToken`: Authentication token matching server configuration (default: apps4net-proxy-secure-token-2025)

**Example:**

```bash
java -jar target/proxy-1.3.jar client myClient public.server.com 5000 my-secure-token-123
```

**Note:** The authentication token must match the `proxy.auth.token` value configured in the server's `application.properties` file.

## Making a POST Request to the Server

Send a POST request to the server's `/api/forward` endpoint with a JSON body. **Authentication is required** - include your admin API key in the Authorization header:

```http
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

```http
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

```http
GET /api/security-status
Authorization: Bearer your-admin-api-key
```

Returns information about banned IPs, threat detection statistics, and security configuration.

#### Server Health Check (No Auth Required)

```http
GET /api/health
```

Returns comprehensive server health information including:

- Server status and uptime
- Total number of connected clients
- List of connected client names (`connectedClientNames`)
- Connection health status

**Example Response:**

```json
{
  "status": "OK",
  "uptime": "2 days, 14 hours, 32 minutes",
  "connectedClients": 3,
  "connectedClientNames": ["client1", "client2", "client3"],
  "timestamp": "2025-01-20T10:30:15Z"
}
```

#### Connection Management (Authentication Required)

```bash
POST /api/cleanup-connections
Authorization: Bearer your-admin-api-key
```

Manually triggers cleanup of unhealthy or zombie connections. The server automatically performs this cleanup every minute, but this endpoint allows for manual intervention when needed.

#### Connection Log Management (Authentication Required)

**Get Connection Logs:**

```bash
GET /api/admin/connection-logs
Authorization: Bearer your-admin-api-key
```

Retrieves connection logs with optional filtering:

- `eventType`: Filter by "CONNECT" or "DISCONNECT"
- `clientName`: Filter by specific client name
- `limit`: Limit number of returned results (most recent entries)

**Example with filters:**

```bash
GET /api/admin/connection-logs?eventType=CONNECT&limit=50
Authorization: Bearer your-admin-api-key
```

**Clear Connection Logs:**

```bash
POST /api/admin/connection-logs/clear
Authorization: Bearer your-admin-api-key
```

Clears all stored connection logs. Use with caution as this action cannot be undone.

**Example Response:**

```json
{
  "success": true,
  "timestamp": "2025-06-26T10:30:15",
  "logs": [
    {
      "event": "CONNECT",
      "timestamp": "2025-06-26T10:29:45.123",
      "clientName": "client1",
      "clientIP": "192.168.1.100",
      "message": "Client 'client1' connected from 192.168.1.100"
    },
    {
      "event": "DISCONNECT",
      "timestamp": "2025-06-26T10:30:10.456",
      "clientName": "client1",
      "clientIP": "192.168.1.100",
      "reason": "Connection terminated",
      "message": "Client 'client1' disconnected from 192.168.1.100 - Reason: Connection terminated"
    }
  ],
  "statistics": {
    "totalLogEntries": 2,
    "totalConnections": 1,
    "totalDisconnections": 1,
    "uniqueClients": 1,
    "uniqueIPs": 1
  },
  "totalReturned": 2,
  "filters": {
    "eventType": null,
    "clientName": null,
    "limit": null
  }
}
```

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

**Check server health:**

```bash
curl -X GET http://localhost:9999/api/health
```

**Cleanup unhealthy connections:**

```bash
curl -X POST http://localhost:9999/api/cleanup-connections \
  -H "Authorization: Bearer your-admin-api-key"
```

**Get connection logs:**

```bash
curl -X GET http://localhost:9999/api/admin/connection-logs \
  -H "Authorization: Bearer your-admin-api-key"
```

**Get filtered connection logs:**

```bash
curl -X GET "http://localhost:9999/api/admin/connection-logs?eventType=CONNECT&limit=10" \
  -H "Authorization: Bearer your-admin-api-key"
```

**Clear connection logs:**

```bash
curl -X POST http://localhost:9999/api/admin/connection-logs/clear \
  -H "Authorization: Bearer your-admin-api-key"
```

## Project Structure

- `ProxyApplication.java`: Main entry point with mode selection and scheduled task configuration.
- `ProxyClient.java`: Client mode logic with enhanced health checking and reconnection capabilities.
- `controllers/GeneralController.java`: REST API controller with comprehensive admin endpoints.
- `controllers/ClientHandler.java`: Socket communication handler with 30-second timeout handling and connection health monitoring.
- `services/ProxyService.java`: Business logic, socket server management, client health tracking, connection cleanup, and scheduled maintenance.
- `utils/Logger.java`: Enhanced logging utility with debug support and detailed error analysis.
- `utils/ConnectionLogger.java`: **New in v1.3** - Comprehensive connection event logging system with filtering and statistics.
- `utils/AdminAuthUtils.java`: Admin authentication and authorization utilities.
- `utils/ServerUtils.java`: Server configuration and utility functions.
- `shared/ProxyRequest.java`, `shared/ProxyResponse.java`: Serializable request/response objects for robust communication.
- `config/CorsConfig.java`: CORS configuration for web API access.

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
- **Timeout Management**: 30-second socket timeouts with automatic retry and recovery
- **Connection Validation**: Pre-request health checks ensure reliable communication
- **Detailed Error Logging**: Comprehensive error classification and troubleshooting information

## Connection Management and Health Monitoring

### Server-Side Connection Management

- **Real-time Client Tracking**: Monitor connected clients via `/api/health` endpoint
- **Automatic Cleanup**: Unhealthy connections are automatically detected and removed every minute via scheduled task
- **Manual Cleanup**: Use `/api/cleanup-connections` endpoint for manual intervention
- **Connection Validation**: Each request validates client connection health before forwarding
- **Zombie Connection Prevention**: Automatic detection and removal of abandoned connections
- **Connection Event Logging**: All client connections and disconnections are logged with timestamps and metadata

### Server Connection Event Logging System

The proxy server now includes a comprehensive connection logging system:

- **Real-time Tracking**: Every client connect/disconnect event is logged immediately
- **Detailed Metadata**: Logs include timestamp, client name, IP address, and event reason
- **Event Filtering**: Filter logs by event type (CONNECT/DISCONNECT) or client name
- **Statistics**: Track connection patterns, unique clients, and historical data
- **Admin Management**: Retrieve, filter, and clear logs via admin API endpoints
- **Memory Management**: Automatic log rotation to prevent memory issues (max 1000 entries)

### Client-Side Connection Management

- **Automatic Reconnection**: Clients automatically reconnect every 5 seconds on connection loss
- **Heartbeat Monitoring**: Health checks every 30 seconds to maintain active connections
- **Connection Health Detection**: Comprehensive socket health testing without data stream interference
- **External Control Interface**: Methods for external systems to trigger reconnection or check status
- **Graceful Failure Handling**: Detailed error analysis with specific troubleshooting guidance

## Debugging and Troubleshooting

### Server-Side Debugging

#### Health Monitoring

Monitor server and client health using the health endpoint:

```bash
curl -X GET http://localhost:9999/api/health
```

This provides:

- Server status and uptime
- Number of connected clients
- List of connected client names
- Real-time connection health status

#### Connection Management

Clean up unhealthy connections manually:

```bash
curl -X POST http://localhost:9999/api/cleanup-connections \
  -H "Authorization: Bearer your-admin-api-key"
```

#### Debug Logging

Enable debug logging by setting the log level in your application configuration. The server provides detailed logging for:

- Client connection/disconnection events
- Request forwarding and response handling
- Connection health checks and cleanup operations
- Error classification and recovery attempts

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

```text
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

1. Verify server is running: `java -jar proxy-1.0.jar server`
2. Test connectivity: `telnet <server-host> <server-port>`
3. Check firewall settings
4. Verify server configuration in `application.properties`
5. Check server health: `curl http://localhost:9999/api/health`

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
4. Check server security status via admin API

#### "DNS resolution failed" Error

**Symptoms**: Cannot resolve server hostname
**Solutions**:

1. Use IP address instead of hostname
2. Check DNS configuration
3. Test with: `nslookup <server-host>`

#### "Socket timeout" Errors

**Symptoms**: Connections timing out after 30 seconds
**Possible Causes**:

- Network latency issues
- Server overload
- Client processing delays

**Solutions**:

1. Check network connectivity between client and server
2. Monitor server load and resource usage
3. Verify client can process requests within timeout window
4. Use the connection cleanup endpoint if connections become stale

#### "Unhealthy connection" Warnings

**Symptoms**: Server reports unhealthy client connections
**Solutions**:

1. Check client reconnection logs for connection issues
2. Manually trigger connection cleanup: `POST /api/cleanup-connections`
3. Monitor client heartbeat and reconnection behavior
4. Verify network stability between client and server

#### Client Reconnection Issues

**Symptoms**: Client cannot maintain stable connection
**Solutions**:

1. Check client logs for detailed reconnection diagnostics
2. Verify network stability and firewall settings
3. Monitor server health endpoint for connection status
4. Ensure client has proper permissions and authentication
5. Check for resource constraints on client or server systems

## Performance and Monitoring

### Key Metrics to Monitor

- **Connected Client Count**: Track via `/api/health` endpoint
- **Connection Health**: Monitor for unhealthy or zombie connections
- **Authentication Failures**: Watch security logs for potential attacks
- **Response Times**: Monitor request/response processing times
- **Reconnection Frequency**: Track client reconnection attempts
- **Connection Event Logs**: Monitor connection patterns via `/api/admin/connection-logs`
- **Connection Statistics**: Track unique clients, total connections, and historical data

### Connection Logging and Analytics

The v1.3 connection logging system provides comprehensive monitoring capabilities:

- **Event Tracking**: Monitor all client connections and disconnections in real-time
- **Historical Analysis**: Access historical connection data for pattern analysis
- **Client Behavior**: Track individual client connection patterns and reliability
- **IP Analysis**: Monitor connections by IP address for security insights
- **Performance Metrics**: Analyze connection duration and frequency patterns

### Best Practices

- **Regular Health Checks**: Monitor the `/api/health` endpoint regularly
- **Connection Log Analysis**: Review `/api/admin/connection-logs` for connection patterns
- **Automated Monitoring**: Set up alerts for unusual connection patterns or high disconnect rates
- **Security Monitoring**: Regular checks of `/api/security-status` for threat detection
- **Log Management**: Periodically clear connection logs via `/api/admin/connection-logs/clear` to manage memory
- **Resource Monitoring**: Track server memory and CPU usage, especially with many connected clients
- **Scheduled Maintenance**: The automatic cleanup runs every minute - monitor its effectiveness
