package com.whiteboard;

import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    
    private static final String DB_URL = "jdbc:h2:~/whiteboard_db;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    @Override
    public void init() throws ServletException {
        try {
            Class.forName("org.h2.Driver");
            createTables();
        } catch (Exception e) {
            throw new ServletException("Database initialization failed", e);
        }
    }
    
    private void createTables() {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(255) PRIMARY KEY,
                username VARCHAR(100) UNIQUE NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_login TIMESTAMP,
                active BOOLEAN DEFAULT TRUE
            )
        """;
        
        String createBoardsTable = """
            CREATE TABLE IF NOT EXISTS boards (
                id VARCHAR(255) PRIMARY KEY,
                user_id VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                template VARCHAR(50),
                content TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createBoardsTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        // Read JSON body
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        
        JSONObject requestData = new JSONObject(sb.toString());
        JSONObject response = new JSONObject();
        
        try {
            if ("/register".equals(path)) {
                handleRegister(requestData, response, resp);
            } else if ("/login".equals(path)) {
                handleLogin(requestData, response, req, resp);
            } else if ("/logout".equals(path)) {
                handleLogout(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.put("error", "Endpoint not found");
                resp.getWriter().write(response.toString());
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.put("error", e.getMessage());
            resp.getWriter().write(response.toString());
        }
    }
    
    private void handleRegister(JSONObject data, JSONObject response, HttpServletResponse resp) 
            throws SQLException {
        
        String username = data.getString("username");
        String email = data.getString("email");
        String password = data.getString("password");
        
        // Validate input
        if (username == null || username.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.length() < 6) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.put("error", "Invalid input. Password must be at least 6 characters");
            resp.getWriter().write(response.toString());
            return;
        }
        
        // Hash password
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        String userId = UUID.randomUUID().toString();
        
        String sql = "INSERT INTO users (id, username, email, password_hash) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, email);
            stmt.setString(4, passwordHash);
            stmt.executeUpdate();
            
            response.put("success", true);
            response.put("message", "Registration successful");
            response.put("userId", userId);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(response.toString());
            
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                response.put("error", "Username or email already exists");
            } else {
                throw e;
            }
            resp.getWriter().write(response.toString());
        }
    }
    
    private void handleLogin(JSONObject data, JSONObject response, HttpServletRequest req, HttpServletResponse resp) 
            throws SQLException, IOException {
        
        String username = data.getString("username");
        String password = data.getString("password");
        
        String sql = "SELECT * FROM users WHERE username = ? AND active = TRUE";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                
                if (BCrypt.checkpw(password, storedHash)) {
                    // Update last login
                    String updateSql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, rs.getString("id"));
                        updateStmt.executeUpdate();
                    }
                    
                    // Create session
                    HttpSession session = req.getSession();
                    session.setAttribute("userId", rs.getString("id"));
                    session.setAttribute("username", rs.getString("username"));
                    session.setAttribute("email", rs.getString("email"));
                    session.setMaxInactiveInterval(3600); // 1 hour
                    
                    response.put("success", true);
                    response.put("message", "Login successful");
                    response.put("username", rs.getString("username"));
                    response.put("userId", rs.getString("id"));
                    resp.getWriter().write(response.toString());
                    
                } else {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.put("error", "Invalid credentials");
                    resp.getWriter().write(response.toString());
                }
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.put("error", "User not found");
                resp.getWriter().write(response.toString());
            }
        }
    }
    
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) 
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        
        JSONObject response = new JSONObject();
        response.put("success", true);
        response.put("message", "Logged out successfully");
        resp.getWriter().write(response.toString());
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        String path = req.getPathInfo();
        resp.setContentType("application/json");
        
        if ("/session".equals(path)) {
            HttpSession session = req.getSession(false);
            JSONObject response = new JSONObject();
            
            if (session != null && session.getAttribute("userId") != null) {
                response.put("authenticated", true);
                response.put("username", session.getAttribute("username"));
                response.put("userId", session.getAttribute("userId"));
            } else {
                response.put("authenticated", false);
            }
            
            resp.getWriter().write(response.toString());
        }
    }
}