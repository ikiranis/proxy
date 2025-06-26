package com.apps4net.proxy.utils;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Utility class for logging client connection and disconnection events.
 * 
 * This class maintains a thread-safe log of all client connection events including:
 * - Connection establishments with timestamps and client details
 * - Disconnection events with timestamps and reasons
 * - Client IP addresses and connection durations
 * 
 * The log is kept in memory with a configurable maximum size to prevent
 * memory issues in long-running deployments.
 * 
 * @author Apps4Net
 * @version 1.2
 * @since 1.2
 */
@Component
public class ConnectionLogger {
    
    private static final int MAX_LOG_ENTRIES = 1000; // Maximum number of log entries to keep
    private final List<Map<String, Object>> connectionLogs = new CopyOnWriteArrayList<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    /**
     * Logs a successful client connection.
     * 
     * @param clientName the name of the connected client
     * @param clientIP the IP address of the client
     */
    public void logConnection(String clientName, String clientIP) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("event", "CONNECT");
        logEntry.put("timestamp", LocalDateTime.now().format(formatter));
        logEntry.put("clientName", clientName);
        logEntry.put("clientIP", clientIP);
        logEntry.put("message", "Client '" + clientName + "' connected from " + clientIP);
        
        addLogEntry(logEntry);
        Logger.info("CONNECTION_LOG: " + logEntry.get("message"));
    }
    
    /**
     * Logs a client disconnection.
     * 
     * @param clientName the name of the disconnected client (can be null if connection failed before registration)
     * @param clientIP the IP address of the client
     * @param reason the reason for disconnection (optional)
     */
    public void logDisconnection(String clientName, String clientIP, String reason) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("event", "DISCONNECT");
        logEntry.put("timestamp", LocalDateTime.now().format(formatter));
        logEntry.put("clientName", clientName != null ? clientName : "UNKNOWN");
        logEntry.put("clientIP", clientIP);
        
        String message;
        if (clientName != null) {
            message = "Client '" + clientName + "' disconnected from " + clientIP;
        } else {
            message = "Unknown client disconnected from " + clientIP + " (connection failed before registration)";
        }
        
        if (reason != null && !reason.trim().isEmpty()) {
            message += " - Reason: " + reason;
            logEntry.put("reason", reason);
        }
        
        logEntry.put("message", message);
        
        addLogEntry(logEntry);
        Logger.info("CONNECTION_LOG: " + message);
    }
    
    /**
     * Gets all connection log entries.
     * 
     * @return a list of log entries, each containing event details
     */
    public List<Map<String, Object>> getAllLogs() {
        return new ArrayList<>(connectionLogs);
    }
    
    /**
     * Gets connection log entries filtered by event type.
     * 
     * @param eventType the event type to filter by ("CONNECT" or "DISCONNECT")
     * @return a list of filtered log entries
     */
    public List<Map<String, Object>> getLogsByEventType(String eventType) {
        return connectionLogs.stream()
            .filter(entry -> eventType.equals(entry.get("event")))
            .collect(ArrayList::new, (list, entry) -> list.add(new HashMap<>(entry)), ArrayList::addAll);
    }
    
    /**
     * Gets connection log entries for a specific client.
     * 
     * @param clientName the name of the client to filter logs for
     * @return a list of log entries for the specified client
     */
    public List<Map<String, Object>> getLogsForClient(String clientName) {
        return connectionLogs.stream()
            .filter(entry -> clientName.equals(entry.get("clientName")))
            .collect(ArrayList::new, (list, entry) -> list.add(new HashMap<>(entry)), ArrayList::addAll);
    }
    
    /**
     * Gets connection statistics including total connections, disconnections, and unique clients.
     * 
     * @return a map containing connection statistics
     */
    public Map<String, Object> getConnectionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long connectCount = connectionLogs.stream()
            .filter(entry -> "CONNECT".equals(entry.get("event")))
            .count();
            
        long disconnectCount = connectionLogs.stream()
            .filter(entry -> "DISCONNECT".equals(entry.get("event")))
            .count();
            
        long uniqueClients = connectionLogs.stream()
            .map(entry -> (String) entry.get("clientName"))
            .filter(name -> name != null && !"UNKNOWN".equals(name))
            .distinct()
            .count();
        
        stats.put("totalConnections", connectCount);
        stats.put("totalDisconnections", disconnectCount);
        stats.put("uniqueClients", uniqueClients);
        stats.put("totalLogEntries", connectionLogs.size());
        stats.put("maxLogEntries", MAX_LOG_ENTRIES);
        
        return stats;
    }
    
    /**
     * Clears all connection logs.
     * This method is primarily for administrative purposes.
     */
    public void clearLogs() {
        connectionLogs.clear();
        Logger.info("CONNECTION_LOG: All connection logs cleared");
    }
    
    /**
     * Adds a log entry to the connection log, maintaining the maximum size limit.
     * 
     * @param logEntry the log entry to add
     */
    private void addLogEntry(Map<String, Object> logEntry) {
        connectionLogs.add(logEntry);
        
        // Remove oldest entries if we exceed the maximum size
        while (connectionLogs.size() > MAX_LOG_ENTRIES) {
            connectionLogs.remove(0);
        }
    }
}
