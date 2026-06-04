package com.cooperativa.core.config;

public class TenantContext {

    // ThreadLocal aísla el valor por cada hilo de petición (Request) de forma segura
    private static final ThreadLocal<Integer> currentTenant = new ThreadLocal<>();

    public static void setCurrentTenant(Integer tenantId) {
        currentTenant.set(tenantId);
    }

    public static Integer getCurrentTenant() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove(); // Evita fugas de memoria (Memory Leaks) en el servidor TomCat
    }
}
