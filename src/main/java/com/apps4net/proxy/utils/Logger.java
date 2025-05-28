package com.apps4net.proxy.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for logging messages with timestamps.
 * Provides different log levels and intelligent exception handling.
 */
public class Logger {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Logs an informational message with timestamp.
     * 
     * @param message The message to log
     */
    public static void info(String message) {
        System.out.println(getTimestamp() + " [INFO] " + message);
    }
    
    /**
     * Logs an error message with timestamp.
     * 
     * @param message The error message to log
     */
    public static void error(String message) {
        System.err.println(getTimestamp() + " [ERROR] " + message);
    }
    
    /**
     * Logs an error message with exception details.
     * For network-related exceptions, only logs the exception type and message.
     * For other exceptions, prints the full stack trace.
     * 
     * @param message The error message to log
     * @param e The exception that occurred
     */
    public static void error(String message, Exception e) {
        System.err.println(getTimestamp() + " [ERROR] " + message);
        // Only print stack trace for non-network exceptions
        if (isNetworkException(e)) {
            System.err.println(getTimestamp() + " [ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } else {
            e.printStackTrace();
        }
    }
    
    /**
     * Determines if an exception is network-related to avoid verbose logging.
     * 
     * @param e The exception to check
     * @return true if the exception is network-related, false otherwise
     */
    private static boolean isNetworkException(Exception e) {
        String className = e.getClass().getSimpleName();
        String message = e.getMessage();
        return className.equals("SocketException") || 
               className.equals("ConnectException") ||
               className.equals("SocketTimeoutException") ||
               (message != null && (message.contains("Connection reset") || 
                                   message.contains("Connection refused") ||
                                   message.contains("Broken pipe") ||
                                   message.contains("Network is unreachable")));
    }
    
    /**
     * Generates a formatted timestamp string for log entries.
     * 
     * @return Formatted timestamp string in brackets
     */
    private static String getTimestamp() {
        return "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "]";
    }
}
