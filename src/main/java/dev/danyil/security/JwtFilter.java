package dev.danyil.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import dev.danyil.auth.CustomUserDetails;
import dev.danyil.auth.CustomUserDetailsService;
import dev.danyil.security.exceptions.JwtNoExistException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserService;
    private final HandlerExceptionResolver resolver;

    public JwtFilter(
            JwtService jwtService,
            CustomUserDetailsService customUserService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtService = jwtService;
        this.customUserService = customUserService;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        String uri = request.getRequestURI();
        String method = request.getMethod();

        
        
        boolean sendCsrfOnly = (uri.equals("/api/v1/auth/me") &&
                method.equals(HttpMethod.GET.name()) &&
                (token == null || token.isBlank()));

        if (!sendCsrfOnly) {

            try {
                jwtService.validateJwtToken(token);
                setCustomUserDetailsToSecurityContextHolder(token);
            } catch (JwtNoExistException exc) {
                resolver.resolveException(request, response, null, exc);
                return;
            } catch (ExpiredJwtException exc) {
                resolver.resolveException(request, response, null, exc);
                return;
            } catch (JwtException exc) {
                resolver.resolveException(request, response, null, exc);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "access_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    private void setCustomUserDetailsToSecurityContextHolder(String token) {
        String username = jwtService.getUsernameFromToken(token);

        CustomUserDetails customUserDetails = customUserService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                customUserDetails,
                null,
                customUserDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

}

