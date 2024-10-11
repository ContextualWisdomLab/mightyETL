package com.xtrmetl.gateway.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.ArrayList;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String token = extractToken(request);
        
        if (token != null && validateToken(token)) {
            // Set authentication in SecurityContext
            // This is a simplified version, you should implement proper JWT validation
            SecurityContextHolder.getContext().setAuthentication(createAuthentication(token));
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        // Extract token from Authorization header
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean validateToken(String token) {
        // Implement token validation logic
        return true; // Simplified for this example
    }

    private Authentication createAuthentication(String token) {
        // Create and return Authentication object based on the token
        // This is a simplified version
        return new UsernamePasswordAuthenticationToken("user", null, new ArrayList<>());
    }
}
