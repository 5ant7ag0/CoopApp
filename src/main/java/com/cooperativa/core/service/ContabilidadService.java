package com.cooperativa.core.service;

import com.cooperativa.core.model.AsientosCabecera;
import com.cooperativa.core.model.AsientosDetalle;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.repository.AsientosCabeceraRepository;
import com.cooperativa.core.repository.AsientosDetalleRepository;
import com.cooperativa.core.repository.PlanCuentasRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ContabilidadService {

    @Autowired
    private AsientosCabeceraRepository cabeceraRepository;

    @Autowired
    private AsientosDetalleRepository detalleRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    /**
     * Registra un asiento contable completo aplicando validación estricta de Partida Doble.
     */
    @Transactional(rollbackFor = Exception.class)
    public AsientosCabecera registrarAsientoCuadrado(AsientosCabecera cabecera, List<AsientosDetalle> detalles) {

        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("Error Contable: Un asiento debe contener al menos un detalle de movimiento.");
        }

        BigDecimal totalDebitos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCreditos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Validar y acumular montos de la partida doble
        for (AsientosDetalle detalle : detalles) {
            if (detalle.getMonto() == null || detalle.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Error Contable: El monto del asiento debe ser mayor a cero.");
            }

            // Sanitización decimal contable: forzar escala a 2 posiciones con redondeo HALF_UP
            BigDecimal montoRedondeado = detalle.getMonto().setScale(2, RoundingMode.HALF_UP);
            detalle.setMonto(montoRedondeado);

            if ("DEBITO".equals(detalle.getTipoAsiento())) {
                totalDebitos = totalDebitos.add(montoRedondeado);
            } else if ("CREDITO".equals(detalle.getTipoAsiento())) {
                totalCreditos = totalCreditos.add(montoRedondeado);
            } else {
                throw new IllegalArgumentException("Error Contable: El tipo de asiento debe ser DEBITO o CREDITO.");
            }
        }

        // CONTROL NORMATIVO: Validar cuadre matemático absoluto
        if (totalDebitos.compareTo(totalCreditos) != 0) {
            throw new IllegalStateException("Error Crítico de Partida Doble: Asiento descuadrado. Total Débitos ($"
                    + totalDebitos + ") no coincide con Total Créditos ($" + totalCreditos + ").");
        }

        // 1. Guardar la cabecera (Obtiene el ID correlativo)
        AsientosCabecera cabeceraGuardada = cabeceraRepository.save(cabecera);

        // 2. Asociar y guardar cada renglón del detalle contable
        for (AsientosDetalle detalle : detalles) {
            detalle.setAsientoCabecera(cabeceraGuardada);
            detalleRepository.save(detalle);
        }

        return cabeceraGuardada;
    }

    // OBTENER ASIENTOS CONTABLES DEL DIA DE HOY
    public List<AsientosCabecera> obtenerAsientosDeHoy() {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDateTime inicio = hoy.atStartOfDay();
        java.time.LocalDateTime fin = hoy.atTime(23, 59, 59, 999999999);
        return cabeceraRepository.findByEmpresaIdAndFechaAsientoBetweenOrderByFechaAsientoDesc(tenantId, inicio, fin);
    }
}