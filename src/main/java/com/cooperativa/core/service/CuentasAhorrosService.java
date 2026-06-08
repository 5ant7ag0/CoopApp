package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.CuentasAhorros;
import com.cooperativa.core.repository.CuentasAhorrosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CuentasAhorrosService {

    @Autowired
    private CuentasAhorrosRepository cuentasAhorrosRepository;

    // CREAR UNA NUEVA CUENTA DE AHORROS
    @Transactional
    public CuentasAhorros crearCuenta(CuentasAhorros cuenta) {
        Integer tenantId = TenantContext.getCurrentTenant();

        // Regla de Negocio: No duplicar el número de cuenta en la misma institución
        if (cuentasAhorrosRepository.findByNumeroCuentaAndEmpresaId(cuenta.getNumeroCuenta(), tenantId).isPresent()) {
            throw new IllegalStateException("Error: El numero de cuenta " + cuenta.getNumeroCuenta() + " ya se encuentra registrado.");
        }

        return cuentasAhorrosRepository.save(cuenta);
    }

    // LEER TODAS LAS CUENTAS DEL TENANT ACTIVO
    public List<CuentasAhorros> obtenerTodas() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return cuentasAhorrosRepository.findByEmpresaId(tenantId);
    }

    // LEER CUENTA POR ID
    public CuentasAhorros obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        return cuentasAhorrosRepository.findById(id)
                .filter(c -> c.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de ahorros no encontrada en esta institucion."));
    }

    // ACTUALIZAR CUENTA (Cambio de tipo o estado administrativo)
    @Transactional
    public CuentasAhorros actualizarCuenta(Integer id, CuentasAhorros datosNuevos) {
        CuentasAhorros cuentaExistente = obtenerPorId(id);

        cuentaExistente.setTipo(datosNuevos.getTipo());
        cuentaExistente.setEstado(datosNuevos.getEstado());
        // El saldo NO se actualiza por aquí; eso es exclusivo del módulo transaccional/Ledger

        return cuentasAhorrosRepository.save(cuentaExistente);
    }

    // ELIMINACIÓN LÓGICA (Inactivación de la cuenta por seguridad de auditoría)
    @Transactional
    public void eliminarLogico(Integer id) {
        CuentasAhorros cuenta = obtenerPorId(id);
        cuenta.setEstado("INACTIVA");
        cuentasAhorrosRepository.save(cuenta);
    }
}