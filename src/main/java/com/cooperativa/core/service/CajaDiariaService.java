package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.AperturaCajaDTO;
import com.cooperativa.core.dto.CierreCajaDTO;
import com.cooperativa.core.model.*;
import com.cooperativa.core.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CajaDiariaService {

    @Autowired
    private CajaDiariaRepository cajaDiariaRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private TransaccionesLedgerRepository transaccionesLedgerRepository;

    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    /**
     * Retorna la caja activa (APERTURADA) del cajero en sesión dentro de la cooperativa.
     */
    public Optional<CajaDiaria> obtenerCajaActiva(String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede consultar caja sin X-Tenant-ID.");
        }
        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        return cajaDiariaRepository.findByUsuarioCajeroIdAndEstadoAndEmpresaId(cajero.getId(), "APERTURADA", tenantId);
    }

    /**
     * Valida si la caja del cajero está aperturada para la fecha contable actual.
     * Lanzará una excepción si la caja no existe o está cerrada.
     */
    public void validarCajaAperturada(Integer cajeroId, Integer tenantId) {
        CajaDiaria caja = cajaDiariaRepository
                .findByUsuarioCajeroIdAndEstadoAndEmpresaId(cajeroId, "APERTURADA", tenantId)
                .orElseThrow(() -> new IllegalStateException("Acceso Denegado: Debe aperturar su caja diaria antes de realizar transacciones por ventanilla."));

        if (!caja.getFechaContable().equals(LocalDate.now())) {
            throw new IllegalStateException("Cierre Requerido: Su caja abierta corresponde a una fecha contable anterior (" + caja.getFechaContable() + "). Debe cerrarla para iniciar el nuevo día.");
        }
    }

    /**
     * Realiza la apertura de la caja para el día de hoy con un monto inicial.
     */
    @Transactional(rollbackFor = Exception.class)
    public CajaDiaria aperturarCaja(AperturaCajaDTO dto, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        if (!"CAJERO".equals(authRol)) {
            throw new SecurityException("Error de Seguridad: Privilegios insuficientes. Solo los Cajeros pueden aperturar caja.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede aperturar caja sin X-Tenant-ID.");
        }

        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        // 1. Validar que no tenga otra caja abierta
        Optional<CajaDiaria> cajaActiva = cajaDiariaRepository
                .findByUsuarioCajeroIdAndEstadoAndEmpresaId(cajero.getId(), "APERTURADA", tenantId);
        if (cajaActiva.isPresent()) {
            throw new IllegalStateException("Error: Ya posee una caja APERTURADA para la fecha " + cajaActiva.get().getFechaContable() + ".");
        }

        // 2. Validar que no haya abierto y cerrado otra caja el día de hoy
        LocalDate hoy = LocalDate.now();
        Optional<CajaDiaria> cajaHoy = cajaDiariaRepository
                .findByUsuarioCajeroIdAndFechaContableAndEmpresaId(cajero.getId(), hoy, tenantId);
        if (cajaHoy.isPresent()) {
            throw new IllegalStateException("Error: Ya se registró una actividad de caja el día de hoy (" + hoy + ") para este cajero.");
        }

        CajaDiaria caja = new CajaDiaria();
        caja.setUsuarioCajero(cajero);
        caja.setFechaContable(hoy);
        caja.setMontoApertura(dto.getMontoApertura().setScale(2, RoundingMode.HALF_UP));
        caja.setMontoCierreSistema(dto.getMontoApertura().setScale(2, RoundingMode.HALF_UP));
        caja.setEstado("APERTURADA");

        CajaDiaria guardada = cajaDiariaRepository.save(caja);

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(cajero.getId());
        log.setAccion("APERTURA_CAJA");
        log.setTablaAfectada("cajas_diarias");
        log.setRegistroId(guardada.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorNuevo(Map.of("montoApertura", dto.getMontoApertura(), "fechaContable", hoy.toString()));
        logsAuditoriaService.registrarLog(log);

        return guardada;
    }

    /**
     * Cierra la caja activa realizando la conciliación de movimientos de ventanilla
     * y el arqueo contable con registro de sobrantes/faltantes en partida doble.
     */
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public CajaDiaria cerrarCaja(CierreCajaDTO dto, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede cerrar caja sin X-Tenant-ID.");
        }

        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        CajaDiaria caja = cajaDiariaRepository
                .findByUsuarioCajeroIdAndEstadoAndEmpresaId(cajero.getId(), "APERTURADA", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error: No se encontró una caja APERTURADA para este cajero en la sesión activa."));

        // 1. Obtener movimientos en ventanilla operados por este cajero en la fecha contable
        LocalDateTime inicioDia = caja.getFechaContable().atStartOfDay();
        LocalDateTime finDia = caja.getFechaContable().atTime(23, 59, 59, 999999999);

        List<TransaccionesLedger> transacciones = transaccionesLedgerRepository
                .findByUsuarioAdminIdAndCanalAndFechaContableBetween(cajero.getId(), "VENTANILLA", inicioDia, finDia);

        BigDecimal ingresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal egresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (TransaccionesLedger tx : transacciones) {
            BigDecimal montoTx = tx.getMonto().setScale(2, RoundingMode.HALF_UP);
            if ("CREDITO".equals(tx.getTipoTransaccion())) {
                ingresos = ingresos.add(montoTx);
            } else if ("DEBITO".equals(tx.getTipoTransaccion())) {
                egresos = egresos.add(montoTx);
            }
        }

        // 2. Conciliación matemática
        BigDecimal montoCierreSistema = caja.getMontoApertura().add(ingresos).subtract(egresos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal efectivoReal = dto.getMontoCierreEfectivoReal().setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferencia = efectivoReal.subtract(montoCierreSistema).setScale(2, RoundingMode.HALF_UP);

        caja.setMontoCierreSistema(montoCierreSistema);
        caja.setMontoCierreEfectivoReal(efectivoReal);
        caja.setDiferencia(diferencia);
        caja.setEstado("CERRADA");

        // 3. Obtener cuentas contables
        PlanCuentas cuentaCaja = planCuentasRepository.findByCodigoContableAndEmpresaId("1.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.1.01.05 (Caja General Ventanilla) no parametrizada."));

        PlanCuentas cuentaBoveda = planCuentasRepository.findByCodigoContableAndEmpresaId("1.1.01.01", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.1.01.01 (Bóveda General) no parametrizada."));

        // 4. Asiento Contable
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setNumeroAsiento("AS-CAJA-CIERRE-" + System.currentTimeMillis());
        cabecera.setGlosa("Arqueo contable automático de cierre. Cajero: " + cajero.getNombresCompletos() + ". Fecha: " + caja.getFechaContable());

        List<AsientosDetalle> detalles = new ArrayList<>();

        if (diferencia.compareTo(BigDecimal.ZERO) == 0) {
            // ESCENARIO A: Sin descalce. Consolidar a Bóveda.
            // Debe: Bóveda
            AsientosDetalle d1 = new AsientosDetalle();
            d1.setPlanCuentas(cuentaBoveda);
            d1.setTipoAsiento("DEBITO");
            d1.setMonto(efectivoReal);
            detalles.add(d1);

            // Haber: Caja Ventanilla
            AsientosDetalle d2 = new AsientosDetalle();
            d2.setPlanCuentas(cuentaCaja);
            d2.setTipoAsiento("CREDITO");
            d2.setMonto(efectivoReal);
            detalles.add(d2);

        } else if (diferencia.compareTo(BigDecimal.ZERO) < 0) {
            // ESCENARIO B: Faltante de Efectivo. Cuentas por Cobrar Empleado.
            PlanCuentas cuentaCobrar = planCuentasRepository.findByCodigoContableAndEmpresaId("1.2.99.01", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.2.99.01 (Faltantes de Caja) no parametrizada."));

            BigDecimal valorFaltante = diferencia.abs().setScale(2, RoundingMode.HALF_UP);

            // Debe: Bóveda (por la cantidad física real transferida)
            if (efectivoReal.compareTo(BigDecimal.ZERO) > 0) {
                AsientosDetalle d1 = new AsientosDetalle();
                d1.setPlanCuentas(cuentaBoveda);
                d1.setTipoAsiento("DEBITO");
                d1.setMonto(efectivoReal);
                detalles.add(d1);
            }

            // Debe: Cuentas por Cobrar Empleados
            AsientosDetalle d2 = new AsientosDetalle();
            d2.setPlanCuentas(cuentaCobrar);
            d2.setTipoAsiento("DEBITO");
            d2.setMonto(valorFaltante);
            detalles.add(d2);

            // Haber: Caja General Ventanilla (se descarga por el saldo total del sistema)
            AsientosDetalle d3 = new AsientosDetalle();
            d3.setPlanCuentas(cuentaCaja);
            d3.setTipoAsiento("CREDITO");
            d3.setMonto(montoCierreSistema);
            detalles.add(d3);

        } else {
            // ESCENARIO C: Sobrante de Efectivo. Otros Ingresos Extraordinarios.
            PlanCuentas cuentaSobrantes = planCuentasRepository.findByCodigoContableAndEmpresaId("5.2.99.01", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 5.2.99.01 (Otros Ingresos - Sobrantes) no parametrizada."));

            // Debe: Bóveda (por el dinero físico real que ingresa)
            AsientosDetalle d1 = new AsientosDetalle();
            d1.setPlanCuentas(cuentaBoveda);
            d1.setTipoAsiento("DEBITO");
            d1.setMonto(efectivoReal);
            detalles.add(d1);

            // Haber: Caja General Ventanilla (por el saldo calculado)
            AsientosDetalle d2 = new AsientosDetalle();
            d2.setPlanCuentas(cuentaCaja);
            d2.setTipoAsiento("CREDITO");
            d2.setMonto(montoCierreSistema);
            detalles.add(d2);

            // Haber: Otros Ingresos - Sobrantes de Caja
            AsientosDetalle d3 = new AsientosDetalle();
            d3.setPlanCuentas(cuentaSobrantes);
            d3.setTipoAsiento("CREDITO");
            d3.setMonto(diferencia);
            detalles.add(d3);
        }

        AsientosCabecera asientoGuardado = contabilidadService.registrarAsientoCuadrado(cabecera, detalles);
        caja.setAsientoCabecera(asientoGuardado);

        CajaDiaria cerrada = cajaDiariaRepository.save(caja);

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(cajero.getId());
        log.setAccion("CIERRE_CAJA");
        log.setTablaAfectada("cajas_diarias");
        log.setRegistroId(cerrada.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("estado", "APERTURADA", "montoApertura", caja.getMontoApertura()));
        log.setValorNuevo(Map.of("estado", "CERRADA", "montoCierreEfectivoReal", efectivoReal, "montoCierreSistema", montoCierreSistema, "diferencia", diferencia));
        logsAuditoriaService.registrarLog(log);

        return cerrada;
    }

    /**
     * Retorna la lista de movimientos (TransaccionesLedger) de la caja activa para el cajero en sesión
     * en el día en curso.
     */
    public List<TransaccionesLedger> obtenerMovimientosDiariosCajero(String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede consultar movimientos sin X-Tenant-ID.");
        }
        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        // Obtener la caja activa o la caja del día de hoy
        CajaDiaria caja = cajaDiariaRepository
                .findByUsuarioCajeroIdAndEstadoAndEmpresaId(cajero.getId(), "APERTURADA", tenantId)
                .orElseGet(() -> {
                    // Si no está abierta, buscamos si tiene una caja hoy ya cerrada para mostrar su historial
                    return cajaDiariaRepository.findByUsuarioCajeroIdAndFechaContableAndEmpresaId(cajero.getId(), LocalDate.now(), tenantId)
                            .orElse(null);
                });

        if (caja == null) {
            return new ArrayList<>();
        }

        LocalDateTime inicioDia = caja.getFechaContable().atStartOfDay();
        LocalDateTime finDia = caja.getFechaContable().atTime(23, 59, 59, 999999999);

        return transaccionesLedgerRepository
                .findByUsuarioAdminIdAndCanalAndFechaContableBetween(cajero.getId(), "VENTANILLA", inicioDia, finDia);
    }
}
