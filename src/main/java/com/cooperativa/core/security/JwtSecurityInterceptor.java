package com.cooperativa.core.security;

import com.cooperativa.core.config.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Interceptor de seguridad que valida los tokens JWT en las cabeceras HTTP.
 * Garantiza el cumplimiento de aislamiento multi-tenant y roles autorizados.
 */
@Component
public class JwtSecurityInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TenantStateCache tenantStateCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        
        // Si no es un metodo de controlador (ej. recursos estaticos), dejar pasar
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 1. Validar si el endpoint es publico (anotacion @PublicEndpoint)
        // Revisar a nivel de metodo o a nivel de clase controladora
        if (handlerMethod.hasMethodAnnotation(PublicEndpoint.class) ||
            handlerMethod.getBeanType().isAnnotationPresent(PublicEndpoint.class)) {
            return true; 
        }

        // 2. Extraer cabecera Authorization
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Error: Falta token de autorizacion (Bearer Token).");
            return false;
        }

        String token = authHeader.substring(7); // Quitar el prefijo "Bearer "
        try {
            // 3. Validar token y obtener claims
            Map<String, Object> claims = jwtUtil.validateTokenAndGetClaims(token);
            String username = (String) claims.get("username");
            String rol = (String) claims.get("rol");
            Integer tokenTenantId = (Integer) claims.get("empresaId");

            // 4. Validar privilegios de Super Administrador Global si aplica
            if (handlerMethod.hasMethodAnnotation(SuperAdminOnly.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(SuperAdminOnly.class)) {
                
                if (!"SUPER_ADMIN_SAAS".equals(rol)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("Error: Acceso Prohibido. Se requieren privilegios de Super Administrador Global.");
                    return false;
                }
            }

            // Validar privilegios específicos de roles si aplica
            if (handlerMethod.hasMethodAnnotation(RequiresRoles.class) ||
                handlerMethod.getBeanType().isAnnotationPresent(RequiresRoles.class)) {
                
                RequiresRoles ann = handlerMethod.getMethodAnnotation(RequiresRoles.class);
                if (ann == null) {
                    ann = handlerMethod.getBeanType().getAnnotation(RequiresRoles.class);
                }
                
                String[] requiredRoles = ann.value();
                boolean hasRole = false;
                for (String r : requiredRoles) {
                    if (r.equals(rol)) {
                        hasRole = true;
                        break;
                    }
                }
                
                if (!hasRole) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("Error: Acceso denegado. Permisos insuficientes (rol no autorizado).");
                    return false;
                }
            }

            // 5. Validar alineacion con el inquilino (Tenant ID)
            Integer currentTenant = TenantContext.getCurrentTenant();

            if (currentTenant == null) {
                // Si el cliente no envio la cabecera X-Tenant-ID, la deducimos del token JWT automaticamente
                TenantContext.setCurrentTenant(tokenTenantId);
            } else if (!currentTenant.equals(tokenTenantId)) {
                // Alerta de seguridad: Intento de acceso cruzado entre tenants
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Error de Aislamiento: El inquilino del token no coincide con el X-Tenant-ID solicitado.");
                return false;
            }

            // 6. Kill Switch: Verificar si el tenant está suspendido en caché
            if (tenantStateCache.isSuspended(tokenTenantId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Error: La cooperativa se encuentra suspendida. Contacte con el soporte técnico.");
                return false;
            }

            // Inyectar datos del usuario logueado en los atributos de peticion para el controlador
            request.setAttribute("authUsername", username);
            request.setAttribute("authRol", rol);
            request.setAttribute("authTenantId", tokenTenantId);

            return true; // Peticion autorizada

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Error: Token de autorizacion invalido o expirado. " + e.getMessage());
            return false;
        }
    }
}
