package com.apps4net.proxy.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    public static void info(String message) {
        System.out.println(getTimestamp() + " [INFO] " + message);
    }
    
    public static void error(String message) {
        System.err.println(getTimestamp() + " [ERROR] " + message);
    }
    
    public static void error(String message, Exception e) {
        System.err.println(getTimestamp() + " [ERROR] " + message);
        // Only print stack trace for non-network exceptions
        if (isNetworkException(e)) {
            System.err.println(getTimestamp() + " [ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } else {
            e.printStackTrace();
        }
    }
    
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
    
    private static String getTimestamp() {
        return "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "]";
    }
}
