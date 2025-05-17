# Proxy App

This application acts as a proxy between HTTP clients and internal clients connected via sockets. It can run in two modes: **server** and **client**.

## How It Works

- **Server Mode**: 
  - Runs a Spring Boot REST API.
  - Accepts HTTP POST requests at `/api/forward?clientName=...` with the request data in the body.
  - Forwards the request to the specified client (connected via socket) inside a LAN.
  - Waits for the client to process and respond, then returns the response to the original HTTP requester.
  - Manages multiple clients, each identified by a unique name.

- **Client Mode**:
  - Connects to the server via socket.
  - Registers itself with a unique client name.
  - Waits for requests from the server, processes them, and sends back responses.

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

You can forward a request to a client using JavaScript:

```javascript
fetch('http://localhost:9999/api/forward?clientName=YOUR_CLIENT_NAME', {
  method: 'POST',
  headers: {
    'Content-Type': 'text/plain'
  },
  body: 'your request data here'
})
.then(response => response.text())
.then(data => {
  console.log('Response:', data);
})
.catch(error => {
  console.error('Error:', error);
});
```

Replace `YOUR_CLIENT_NAME` and the body as needed.

## Project Structure

- `ProxyApplication.java`: Main entry point, handles mode selection.
- `ProxyClient.java`: Client mode logic.
- `controllers/GeneralController.java`: REST API controller.
- `controllers/ClientHandler.java`: Handles socket communication with a client.
- `services/ProxyService.java`: Business logic and socket server for server mode.

## Notes
- The server must be accessible from the client (ensure firewall and NAT rules allow connection).
- Each client must use a unique name.
- You can extend the client logic in `ProxyClient.java` to perform custom processing.
