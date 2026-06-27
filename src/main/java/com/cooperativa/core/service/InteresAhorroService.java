package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
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
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@Service
public class InteresAhorroService {

    @Autowired
    private CuentasAhorrosRepository cuentasAhorrosRepository;

    @Autowired
    private DevengoRegistroRepository devengoRegistroRepository;

    @Autowired
    private CapitalizacionRegistroRepository capitalizacionRegistroRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private TransaccionesLedgerRepository transaccionesLedgerRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    /**
     * Realiza el devengo diario de intereses para todas las cuentas de ahorros activas del tenant.
     * Genera un asiento contable consolidado: Gasto (4.1.01.05) contra Pasivo (2.1.01.10).
     */
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public DevengoRegistro devengarInteresesDiarios(LocalDate fecha, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede devengar intereses sin X-Tenant-ID.");
        }

        // 1. Control de Idempotencia: validar si ya se devengó hoy
        Optional<DevengoRegistro> registroPrevio = devengoRegistroRepository.findByFechaDevengoAndEmpresaId(fecha, tenantId);
        if (registroPrevio.isPresent()) {
            throw new IllegalStateException("Error de Negocio: El devengo de intereses para la fecha " + fecha + " ya fue ejecutado.");
        }

        // 2. Cómputo rápido y consolidado de los intereses devengados
        BigDecimal totalDevengado = cuentasAhorrosRepository.calcularTotalInteresDevengado(tenantId);

        // 3. Ejecución de la actualización atómica masiva de acumuladores en la base de datos
        cuentasAhorrosRepository.ejecutarDevengoInteresesDiarios(tenantId);

        AsientosCabecera asientoGuardado = null;

        // 3. Si hubo devengo mayor a cero, registrar el asiento contable de partida doble
        if (totalDevengado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaPasivo = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.10", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta de pasivos 2.1.01.10 no parametrizada."));

            AsientosCabecera cabecera = new AsientosCabecera();
            cabecera.setNumeroAsiento("AS-DEV-" + System.currentTimeMillis());
            cabecera.setGlosa("Devengo diario automático de intereses de ahorros. Fecha: " + fecha);

            List<AsientosDetalle> detalles = new ArrayList<>();

            // Obtener el devengo agrupado por cuenta de gasto de cada producto
            List<Object[]> devengosAgrupados = cuentasAhorrosRepository.obtenerInteresDevengadoAgrupadoPorGasto(tenantId);
            BigDecimal totalValidado = BigDecimal.ZERO;

            for (Object[] fila : devengosAgrupados) {
                Integer gastoId = (Integer) fila[0];
                BigDecimal montoFila = (BigDecimal) fila[1];

                if (montoFila != null && montoFila.compareTo(BigDecimal.ZERO) > 0) {
                    PlanCuentas cuentaGasto = planCuentasRepository.findById(gastoId)
                            .orElseThrow(() -> new IllegalStateException("Error: Cuenta contable de gasto ID " + gastoId + " no encontrada."));

                    AsientosDetalle dGasto = new AsientosDetalle();
                    dGasto.setPlanCuentas(cuentaGasto);
                    dGasto.setTipoAsiento("DEBITO");
                    dGasto.setMonto(montoFila);
                    detalles.add(dGasto);

                    totalValidado = totalValidado.add(montoFila);
                }
            }

            // Haber: Intereses por Pagar (Pasivo aumenta)
            if (totalValidado.compareTo(BigDecimal.ZERO) > 0) {
                AsientosDetalle dPasivo = new AsientosDetalle();
                dPasivo.setPlanCuentas(cuentaPasivo);
                dPasivo.setTipoAsiento("CREDITO");
                dPasivo.setMonto(totalValidado);
                detalles.add(dPasivo);

                asientoGuardado = contabilidadService.registrarAsientoCuadrado(cabecera, detalles);
            }
        }

        // 4. Guardar registro del devengo para control de idempotencia
        DevengoRegistro devengo = new DevengoRegistro();
        devengo.setFechaDevengo(fecha);
        devengo.setTotalDevengado(totalDevengado);
        devengo.setAsientoCabecera(asientoGuardado);
        DevengoRegistro guardado = devengoRegistroRepository.save(devengo);

        // Resolver ID de actor para auditoría
        Integer adminId = null;
        if (authUsername != null) {
            adminId = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                    .map(UsuariosAdmin::getId)
                    .orElse(null);
        }
        if (adminId == null) {
            List<UsuariosAdmin> admins = usuarioAdminRepository.findByEmpresaId(tenantId);
            if (!admins.isEmpty()) {
                adminId = admins.get(0).getId();
            }
        }

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(adminId);
        log.setAccion("DEVENGO_INTERESES_DIARIO");
        log.setTablaAfectada("devengos_registro");
        log.setRegistroId(guardado.getId());
        log.setDireccionIp("127.0.0.1");
        log.setDispositivoInfo("Batch Engine");
        log.setValorNuevo(java.util.Map.of("fechaDevengo", fecha.toString(), "totalDevengado", totalDevengado));
        logsAuditoriaService.registrarLog(log);

        return guardado;
    }

    /**
     * Capitaliza los intereses devengados acumulados de los socios.
     * Acredita el dinero en el saldo de cada socio, genera registros CREDITO en el Ledger y
     * reversa el pasivo acumulado: Pasivo (2.1.01.10) al Debe contra Ahorros Socios (2.1.01.05) al Haber.
     */
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public CapitalizacionRegistro capitalizarInteresesMensuales(int anio, int mes, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede capitalizar intereses sin X-Tenant-ID.");
        }

        // 1. Control de Idempotencia: validar si ya se capitalizó el periodo
        Optional<CapitalizacionRegistro> registroPrevio = capitalizacionRegistroRepository.findByAnioAndMesAndEmpresaId(anio, mes, tenantId);
        if (registroPrevio.isPresent()) {
            throw new IllegalStateException("Error de Negocio: La capitalización de intereses para el período " + mes + "/" + anio + " ya fue ejecutada.");
        }

        // 2. Obtener todas las cuentas activas
        List<CuentasAhorros> cuentas = cuentasAhorrosRepository.findByEstadoAndTipoAndEmpresaId("ACTIVA", "AHORRO_VISTA", tenantId);
        BigDecimal totalCapitalizado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<PlanCuentas, BigDecimal> capitalizadoPorPasivo = new java.util.HashMap<>();

        for (CuentasAhorros cuenta : cuentas) {
            BigDecimal acumulado = cuenta.getInteresAcumulado();
            if (acumulado.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
            BigDecimal saldoNuevo = saldoAnterior.add(acumulado).setScale(2, RoundingMode.HALF_UP);

            // Acreditar el saldo del socio y reiniciar el acumulador mensual
            cuenta.setSaldo(saldoNuevo);
            cuenta.setInteresAcumulado(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            cuentasAhorrosRepository.save(cuenta);

            // Registrar Ledger
            TransaccionesLedger ledger = new TransaccionesLedger();
            ledger.setCuenta(cuenta);
            ledger.setTipoTransaccion("CREDITO");
            ledger.setMonto(acumulado);
            ledger.setSaldoAnterior(saldoAnterior);
            ledger.setSaldoResultante(saldoNuevo);
            ledger.setCanal("PROCESO_BATCH");
            ledger.setReferencia("REF-CAP-" + anio + "-" + mes + "-" + cuenta.getId() + "-" + System.currentTimeMillis());
            ledger.setDescripcion("Capitalización de intereses acumulados período: " + mes + "/" + anio);
            ledger.setDireccionIp("127.0.0.1");
            ledger.setDispositivoInfo("Batch Engine");
            transaccionesLedgerRepository.save(ledger);

            // Registrar acumulación contable por cuenta de pasivo del producto
            PlanCuentas cuentaPasivoSocio;
            if (cuenta.getProductoAhorro() != null && cuenta.getProductoAhorro().getCuentaContablePasivo() != null) {
                cuentaPasivoSocio = cuenta.getProductoAhorro().getCuentaContablePasivo();
            } else {
                String cod = "APORTACIONES".equals(cuenta.getTipo()) ? "3.1.01.05" : "2.1.01.05";
                cuentaPasivoSocio = planCuentasRepository.findByCodigoContableAndEmpresaId(cod, tenantId)
                        .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta " + cod + " no parametrizada."));
            }

            capitalizadoPorPasivo.put(cuentaPasivoSocio, 
                    capitalizadoPorPasivo.getOrDefault(cuentaPasivoSocio, BigDecimal.ZERO).add(acumulado));

            totalCapitalizado = totalCapitalizado.add(acumulado);
        }

        AsientosCabecera asientoGuardado = null;

        // 3. Registrar el asiento de reversión contable si el total capitalizado es mayor a cero
        if (totalCapitalizado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaPasivoAcumulado = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.10", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta de pasivos 2.1.01.10 no parametrizada."));

            AsientosCabecera cabecera = new AsientosCabecera();
            cabecera.setNumeroAsiento("AS-CAP-" + System.currentTimeMillis());
            cabecera.setGlosa("Capitalización mensual automática de intereses de ahorros. Período: " + mes + "/" + anio);

            List<AsientosDetalle> detalles = new ArrayList<>();

            // Debe: Intereses por Pagar (Pasivo disminuye)
            AsientosDetalle d1 = new AsientosDetalle();
            d1.setPlanCuentas(cuentaPasivoAcumulado);
            d1.setTipoAsiento("DEBITO");
            d1.setMonto(totalCapitalizado);
            detalles.add(d1);

            // Haber: Cuentas de Ahorros de Socios (Pasivo aumenta, agrupado por producto)
            for (Map.Entry<PlanCuentas, BigDecimal> entrada : capitalizadoPorPasivo.entrySet()) {
                AsientosDetalle d2 = new AsientosDetalle();
                d2.setPlanCuentas(entrada.getKey());
                d2.setTipoAsiento("CREDITO");
                d2.setMonto(entrada.getValue());
                detalles.add(d2);
            }

            asientoGuardado = contabilidadService.registrarAsientoCuadrado(cabecera, detalles);
        }

        // 4. Guardar registro de capitalización para control de idempotencia
        CapitalizacionRegistro capitalizacion = new CapitalizacionRegistro();
        capitalizacion.setAnio(anio);
        capitalizacion.setMes(mes);
        capitalizacion.setFechaCapitalizacion(LocalDate.now());
        capitalizacion.setTotalCapitalizado(totalCapitalizado);
        capitalizacion.setAsientoCabecera(asientoGuardado);
        CapitalizacionRegistro guardado = capitalizacionRegistroRepository.save(capitalizacion);

        // Resolver ID de actor para auditoría
        Integer adminId = null;
        if (authUsername != null) {
            adminId = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                    .map(UsuariosAdmin::getId)
                    .orElse(null);
        }
        if (adminId == null) {
            List<UsuariosAdmin> admins = usuarioAdminRepository.findByEmpresaId(tenantId);
            if (!admins.isEmpty()) {
                adminId = admins.get(0).getId();
            }
        }

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(adminId);
        log.setAccion("CAPITALIZACION_INTERESES_MENSUAL");
        log.setTablaAfectada("capitalizaciones_registro");
        log.setRegistroId(guardado.getId());
        log.setDireccionIp("127.0.0.1");
        log.setDispositivoInfo("Batch Engine");
        log.setValorNuevo(java.util.Map.of("anio", anio, "mes", mes, "totalCapitalizado", totalCapitalizado));
        logsAuditoriaService.registrarLog(log);

        return guardado;
    }
}
