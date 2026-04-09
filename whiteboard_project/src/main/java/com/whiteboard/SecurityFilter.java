package com.whiteboard;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;

@WebFilter("/*")
public class SecurityFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);
        
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();
        
        // Public paths that don't require authentication
        boolean isPublicPath = path.equals(contextPath + "/") ||
                              path.equals(contextPath + "/login.html") ||
                              path.equals(contextPath + "/register.html") ||
                              path.equals(contextPath + "/index.html") ||
                              path.startsWith(contextPath + "/api/auth/") ||
                              path.equals(contextPath + "/health") ||
                              path.startsWith(contextPath + "/css/") ||
                              path.startsWith(contextPath + "/js/") ||
                              path.startsWith(contextPath + "/fonts/");
        
        boolean isAuthenticated = (session != null && session.getAttribute("userId") != null);
        
        if (isPublicPath || isAuthenticated) {
            chain.doFilter(request, response);
        } else {
            // Redirect to login page for protected resources
            res.sendRedirect(contextPath + "/login.html");
        }
    }
}