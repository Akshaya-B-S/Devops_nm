package com.whiteboard;

import java.io.*;
import java.sql.*;
import java.lang.management.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import org.json.JSONObject;

@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {
    
    private static final String DB_URL = "jdbc:h2:~/whiteboard_db;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    private long startTime;
    
    @Override
    public void init() throws ServletException {
        startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        JSONObject health = new JSONObject();
        JSONObject checks = new JSONObject();
        
        boolean overallHealthy = true;
        
        // Check Database
        JSONObject dbCheck = checkDatabase();
        checks.put("database", dbCheck);
        if (!dbCheck.getBoolean("healthy")) {
            overallHealthy = false;
        }
        
        // Check Memory
        JSONObject memoryCheck = checkMemory();
        checks.put("memory", memoryCheck);
        if (!memoryCheck.getBoolean("healthy")) {
            overallHealthy = false;
        }
        
        // Check Disk Space
        JSONObject diskCheck = checkDiskSpace();
        checks.put("disk_space", diskCheck);
        if (!diskCheck.getBoolean("healthy")) {
            overallHealthy = false;
        }
        
        // Check Uptime
        checks.put("uptime_seconds", (System.currentTimeMillis() - startTime) / 1000);
        
        // Check Active Sessions
        checks.put("active_sessions", getActiveSessions());
        
        health.put("status", overallHealthy ? "UP" : "DEGRADED");
        health.put("timestamp", System.currentTimeMillis());
        health.put("checks", checks);
        health.put("version", getServletContext().getInitParameter("app.version"));
        health.put("environment", System.getProperty("app.environment", "development"));
        
        // Set HTTP status code
        resp.setStatus(overallHealthy ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        
        PrintWriter out = resp.getWriter();
        out.write(health.toString());
    }
    
    private JSONObject checkDatabase() {
        JSONObject result = new JSONObject();
        
        try {
            Class.forName("org.h2.Driver");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 Statement stmt = conn.createStatement()) {
                
                // Test query
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    result.put("healthy", true);
                    result.put("message", "Database connection successful");
                    result.put("type", "H2");
                } else {
                    result.put("healthy", false);
                    result.put("message", "Database query failed");
                }
            }
        } catch (ClassNotFoundException e) {
            result.put("healthy", false);
            result.put("message", "Database driver not found: " + e.getMessage());
        } catch (SQLException e) {
            result.put("healthy", false);
            result.put("message", "Database connection failed: " + e.getMessage());
        } catch (Exception e) {
            result.put("healthy", false);
            result.put("message", "Unexpected error: " + e.getMessage());
        }
        
        return result;
    }
    
    private JSONObject checkMemory() {
        JSONObject result = new JSONObject();
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double usedPercent = (usedMemory * 100.0) / maxMemory;
        double freePercent = (freeMemory * 100.0) / maxMemory;
        
        result.put("max_memory_mb", maxMemory / (1024 * 1024));
        result.put("used_memory_mb", usedMemory / (1024 * 1024));
        result.put("free_memory_mb", freeMemory / (1024 * 1024));
        result.put("used_percent", Math.round(usedPercent));
        result.put("free_percent", Math.round(freePercent));
        result.put("healthy", usedPercent < 90); // Alert if more than 90% used
        
        if (usedPercent >= 90) {
            result.put("message", "Memory usage critical: " + Math.round(usedPercent) + "%");
        } else if (usedPercent >= 75) {
            result.put("message", "Memory usage high: " + Math.round(usedPercent) + "%");
        } else {
            result.put("message", "Memory usage normal");
        }
        
        return result;
    }
    
    private JSONObject checkDiskSpace() {
        JSONObject result = new JSONObject();
        
        File[] roots = File.listRoots();
        for (File root : roots) {
            long freeBytes = root.getFreeSpace();
            long totalBytes = root.getTotalSpace();
            long usedBytes = totalBytes - freeBytes;
            
            if (totalBytes > 0) {
                double freePercent = (freeBytes * 100.0) / totalBytes;
                double usedPercent = (usedBytes * 100.0) / totalBytes;
                
                result.put("path", root.getPath());
                result.put("total_gb", totalBytes / (1024 * 1024 * 1024));
                result.put("free_gb", freeBytes / (1024 * 1024 * 1024));
                result.put("used_gb", usedBytes / (1024 * 1024 * 1024));
                result.put("free_percent", Math.round(freePercent));
                result.put("used_percent", Math.round(usedPercent));
                result.put("healthy", freePercent > 10); // Alert if less than 10% free
                
                if (freePercent <= 10) {
                    result.put("message", "Critical disk space: only " + Math.round(freePercent) + "% free");
                } else if (freePercent <= 20) {
                    result.put("message", "Low disk space: " + Math.round(freePercent) + "% free");
                } else {
                    result.put("message", "Disk space adequate");
                }
                
                break; // Check only first root
            }
        }
        
        return result;
    }
    
    private int getActiveSessions() {
        try {
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName("Catalina:type=Manager,context=/*,host=localhost");
            
            try {
                Integer activeSessions = (Integer) mBeanServer.getAttribute(objectName, "activeSessions");
                return activeSessions != null ? activeSessions : 0;
            } catch (Exception e) {
                // Fallback - return 0 if can't get sessions
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        // Simple HEAD request for load balancers
        JSONObject health = checkDatabase();
        if (health.getBoolean("healthy")) {
            resp.setStatus(HttpServletResponse.SC_OK);
        } else {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        resp.setContentType("application/json");
    }
}