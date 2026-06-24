package com.cooperativa.core.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String tenantHeader = request.getHeader(TENANT_HEADER);

        // Si la petición viene de los canales digitales, el encabezado de empresa es obligatorio
        if (tenantHeader != null && !tenantHeader.trim().isEmpty()) { //debe pertenecer únicamente a este ID y no puede ser nulo o vacío
            try {
                Integer tenantId = Integer.parseInt(tenantHeader);
                TenantContext.setCurrentTenant(tenantId); // Seteamos el Tenant ID en memoria
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Error: El formato del X-Tenant-ID debe ser numerico.");
                return false;
            }
        } else {
            // Permitimos peticiones públicas pero vaciamos el contexto previo
            TenantContext.clear();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Al terminar la transacción, limpiamos el hilo de ejecución obligatoriamente
        TenantContext.clear();
    }
}
