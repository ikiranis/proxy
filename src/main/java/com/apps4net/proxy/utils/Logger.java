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
        e.printStackTrace();
    }
    
    private static String getTimestamp() {
        return "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "]";
    }
}
