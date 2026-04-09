package com.whiteboard;

import java.io.*;
import java.sql.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/api/boards/*")
public class BoardServlet extends HttpServlet {
    
    private static final String DB_URL = "jdbc:h2:~/whiteboard_db;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    @Override
    public void init() throws ServletException {
        try {
            Class.forName("org.h2.Driver");
            createTablesIfNotExists();
        } catch (Exception e) {
            throw new ServletException("Database initialization failed", e);
        }
    }
    
    private void createTablesIfNotExists() {
        String createBoardsTable = """
            CREATE TABLE IF NOT EXISTS boards (
                id VARCHAR(255) PRIMARY KEY,
                user_id VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                template VARCHAR(50),
                content TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createBoardsTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\": \"Not authenticated\"}");
            return;
        }
        
        String userId = (String) session.getAttribute("userId");
        String pathInfo = req.getPathInfo();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            if (pathInfo == null || pathInfo.equals("/")) {
                // Get all boards for user
                String sql = "SELECT * FROM boards WHERE user_id = ? ORDER BY updated_at DESC";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                JSONArray boards = new JSONArray();
                while (rs.next()) {
                    JSONObject board = new JSONObject();
                    board.put("id", rs.getString("id"));
                    board.put("name", rs.getString("name"));
                    board.put("template", rs.getString("template"));
                    board.put("content", rs.getString("content"));
                    board.put("created_at", rs.getTimestamp("created_at"));
                    board.put("updated_at", rs.getTimestamp("updated_at"));
                    boards.put(board);
                }
                resp.getWriter().write(boards.toString());
                
            } else {
                // Get specific board
                String boardId = pathInfo.substring(1);
                String sql = "SELECT * FROM boards WHERE id = ? AND user_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, boardId);
                stmt.setString(2, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    JSONObject board = new JSONObject();
                    board.put("id", rs.getString("id"));
                    board.put("name", rs.getString("name"));
                    board.put("template", rs.getString("template"));
                    board.put("content", rs.getString("content"));
                    board.put("created_at", rs.getTimestamp("created_at"));
                    board.put("updated_at", rs.getTimestamp("updated_at"));
                    resp.getWriter().write(board.toString());
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"error\": \"Board not found\"}");
                }
            }
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\": \"Not authenticated\"}");
            return;
        }
        
        String userId = (String) session.getAttribute("userId");
        
        // Read JSON body
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        
        JSONObject data = new JSONObject(sb.toString());
        
        String boardId = UUID.randomUUID().toString();
        String name = data.getString("name");
        String template = data.optString("template", "blank");
        String content = data.optString("content", "{}");
        
        String sql = """
            INSERT INTO boards (id, user_id, name, template, content, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, boardId);
            stmt.setString(2, userId);
            stmt.setString(3, name);
            stmt.setString(4, template);
            stmt.setString(5, content);
            stmt.executeUpdate();
            
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("id", boardId);
            response.put("message", "Board created successfully");
            resp.getWriter().write(response.toString());
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\": \"Not authenticated\"}");
            return;
        }
        
        String userId = (String) session.getAttribute("userId");
        String pathInfo = req.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"Board ID required\"}");
            return;
        }
        
        String boardId = pathInfo.substring(1);
        
        // Read JSON body
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = req.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        
        JSONObject data = new JSONObject(sb.toString());
        
        StringBuilder sql = new StringBuilder("UPDATE boards SET updated_at = CURRENT_TIMESTAMP");
        List<Object> params = new ArrayList<>();
        
        if (data.has("name")) {
            sql.append(", name = ?");
            params.add(data.getString("name"));
        }
        if (data.has("content")) {
            sql.append(", content = ?");
            params.add(data.getString("content"));
        }
        
        sql.append(" WHERE id = ? AND user_id = ?");
        params.add(boardId);
        params.add(userId);
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            
            int updated = stmt.executeUpdate();
            
            JSONObject response = new JSONObject();
            response.put("success", updated > 0);
            response.put("message", updated > 0 ? "Board updated successfully" : "Board not found");
            resp.getWriter().write(response.toString());
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\": \"Not authenticated\"}");
            return;
        }
        
        String userId = (String) session.getAttribute("userId");
        String pathInfo = req.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"Board ID required\"}");
            return;
        }
        
        String boardId = pathInfo.substring(1);
        
        String sql = "DELETE FROM boards WHERE id = ? AND user_id = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, boardId);
            stmt.setString(2, userId);
            int deleted = stmt.executeUpdate();
            
            JSONObject response = new JSONObject();
            response.put("success", deleted > 0);
            response.put("message", deleted > 0 ? "Board deleted successfully" : "Board not found");
            resp.getWriter().write(response.toString());
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}