package com.crowdfunding.backend.login;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.crowdfunding.backend.services.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public JwtAuthFilter(UserDetailsService userDetailsService, JwtService jwtService) {
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extraer el header "Authorization" del request
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;
        // 2. Verificar que el header exista y siga el formato esperado "Bearer <token>"
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Quitamos el prefijo "Bearer " (7 caracteres) para quedarnos solo con el token
            token = authHeader.substring(7);

            try {
                // Intentamos extraer el username (email) contenido en el token
                username = jwtService.extractUsername(token);
            } catch (Exception e) {
                // Si el token está malformado, expirado, o con firma inválida,
                // extractUsername lanza una excepción (ej. ExpiredJwtException,
                // MalformedJwtException, SignatureException).
                username = null;
            }

            // 3. Solo intentamos autenticar si:
            // - Se pudo extraer un username válido del token, Y
            // - Todavía no hay una autenticación establecida en el contexto
            // (evita re-autenticar en cada filtro si ya se hizo antes)

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Cargamos los detalles del usuario desde la base de datos vía el user details
                // service
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Validamos que el token corresponda a este usuario y no esté expirado
                if (jwtService.validateToken(token, userDetails)) {
                    // Construimos el objeto de autenticación que Spring Security entiende
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null, // no se necesita password aquí, ya fue validado vía el token
                            userDetails.getAuthorities());
                    // Adjuntamos detalles adicionales del request (IP, sessionId, etc.)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Guardamos la autenticación en el contexto de seguridad de esta request,
                    // marcando al usuario como autenticado para el resto del flujo
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        }
        // 4. Siempre continuar la cadena de filtros, autenticado o no.
        // Si el endpoint requiere auth y no se autenticó, Spring Security
        // se encargará de rechazar la petición más adelante en la cadena.
        filterChain.doFilter(request, response);
    }

}
