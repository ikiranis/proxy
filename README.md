# Proxy App

This application acts as a proxy between HTTP clients and internal clients connected via sockets. It can run in two modes: **server** and **client**.

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
java -jar target/proxy-0.1.jar client <clientName> <serverHost> <serverPort>
```
- `clientName`: Unique name for this client (required)
- `serverHost`: Hostname or IP of the server (default: localhost)
- `serverPort`: Port of the server's socket listener (default: 5000)

**Example:**
```
java -jar target/proxy-0.1.jar client myClient public.server.com 5000
```

## Making a POST Request to the Server

Send a POST request to the server's `/api/forward` endpoint with a JSON body:

```
POST http://localhost:9999/api/forward
Content-Type: application/json

{
  "clientName": "myClient",
  "httpMethodType": "GET",
  "url": "http://lan-server/api",
  "body": ""
}
```

The server will forward this request to the specified client, which will perform the API call and return the result.

## Project Structure

- `ProxyApplication.java`: Main entry point, handles mode selection.
- `ProxyClient.java`: Client mode logic.
- `controllers/GeneralController.java`: REST API controller.
- `controllers/ClientHandler.java`: Handles socket communication with a client.
- `services/ProxyService.java`: Business logic and socket server for server mode.
- `shared/ProxyRequest.java`, `shared/ProxyResponse.java`: Serializable request/response objects for robust communication.

## Notes
- The server must be accessible from the client (ensure firewall and NAT rules allow connection).
- Each client must use a unique name.
- All communication between server and client uses Java object serialization (no text-line protocol).
- You can extend the client logic in `ProxyClient.java` to perform custom processing.
- For troubleshooting, detailed debug logging is present in both client and server.
