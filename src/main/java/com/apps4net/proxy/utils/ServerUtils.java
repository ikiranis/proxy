package com.apps4net.proxy.utils;

/**
 * Utility class for server-related operations and calculations.
 * 
 * This class provides helper methods for server monitoring and status reporting,
 * including uptime calculations and other server metrics.
 * 
 * @author Apps4Net
 * @version 1.0
 * @since 1.0
 */
public class ServerUtils {
    
    // Track server start time for uptime calculation
    private static final long startTime = System.currentTimeMillis();
    
    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private ServerUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Gets the server uptime information.
     * 
     * This method calculates how long the server has been running since startup.
     * It provides a simple uptime metric for monitoring purposes.
     * 
     * @return a string describing the server uptime in a human-readable format
     */
    public static String getServerUptime() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d hours, %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
    
    /**
     * Gets the server start time in milliseconds since epoch.
     * 
     * @return the timestamp when the server was started
     */
    public static long getServerStartTime() {
        return startTime;
    }
    
    /**
     * Gets the server uptime in milliseconds.
     * 
     * @return the number of milliseconds since server startup
     */
    public static long getUptimeMilliseconds() {
        return System.currentTimeMillis() - startTime;
    }
}
