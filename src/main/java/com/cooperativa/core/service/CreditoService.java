package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.CuotaSimuladaDTO;
import com.cooperativa.core.dto.PagoRequestDTO;
import com.cooperativa.core.model.*;
import com.cooperativa.core.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CreditoService {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    @Autowired
    private CreditoRepository creditoRepository;

    @Autowired
    private CuotasAmortizacionRepository cuotasRepository;

    @Autowired
    private CuentasAhorrosRepository cuentasRepository;

    @Autowired
    private LogsAuditoriaRepository logsRepository;

    // Inyección de los nuevos componentes de la reingeniería contable
    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private TransaccionesLedgerRepository transaccionesLedgerRepository;

    // ==========================================
    // FUNCIONES CRUD
    // ==========================================

    @Transactional
    public Credito crearSolicitud(Credito credito) {
        credito.setNumeroCredito("CRE-" + System.currentTimeMillis());
        credito.setEstado("SOLICITADO");
        credito.setMontoDesembolsado(BigDecimal.ZERO);
        return creditoRepository.save(credito);
    }

    public List<Credito> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return creditoRepository.findByEmpresaId(tenantId);
    }

    public Credito obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        return creditoRepository.findById(id)
                .filter(c -> c.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Credito no encontrado en esta institucion."));
    }

    @Transactional
    public Credito aprobarCredito(Integer id) {
        Credito credito = obtenerPorId(id);
        if (!"SOLICITADO".equals(credito.getEstado()) && !"EN_REVISION".equals(credito.getEstado())) {
            throw new IllegalStateException("Error: Solo se pueden aprobar creditos en estado SOLICITADO o EN_REVISION.");
        }
        credito.setEstado("APROBADO");
        return creditoRepository.save(credito);
    }

    // ==========================================
    // MOTOR MATEMÁTICO DE AMORTIZACIÓN
    // ==========================================

    public List<CuotaSimuladaDTO> simularTablaAmortizacion(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual, String sistema) {
        List<CuotaSimuladaDTO> tabla = new ArrayList<>();
        BigDecimal tasaMensual = tasaAnual.divide(BigDecimal.valueOf(12), MC).divide(BigDecimal.valueOf(100), MC);
        BigDecimal saldoPendiente = monto;
        LocalDate fechaIteracion = LocalDate.now();

        switch (sistema.toUpperCase()) {
            case "FRANCES":
                BigDecimal unoMasI = BigDecimal.ONE.add(tasaMensual);
                BigDecimal factorPotencia = unoMasI.pow(plazoMeses, MC);
                BigDecimal numerador = tasaMensual.multiply(factorPotencia, MC);
                BigDecimal denominador = factorPotencia.subtract(BigDecimal.ONE);
                BigDecimal cuotaFijaTotal = monto.multiply(numerador.divide(denominador, MC), MC).setScale(2, RoundingMode.HALF_EVEN);

                for (int i = 1; i <= plazoMeses; i++) {
                    fechaIteracion = fechaIteracion.plusMonths(1);
                    BigDecimal interesCuota = saldoPendiente.multiply(tasaMensual, MC).setScale(2, RoundingMode.HALF_EVEN);
                    BigDecimal capitalCuota = cuotaFijaTotal.subtract(interesCuota).setScale(2, RoundingMode.HALF_EVEN);

                    if (i == plazoMeses) {
                        capitalCuota = saldoPendiente;
                        cuotaFijaTotal = capitalCuota.add(interesCuota);
                    }

                    saldoPendiente = saldoPendiente.subtract(capitalCuota);
                    tabla.add(new CuotaSimuladaDTO(i, fechaIteracion, capitalCuota, interesCuota, cuotaFijaTotal, saldoPendiente.max(BigDecimal.ZERO)));
                }
                break;

            case "ALEMAN":
                BigDecimal capitalFijo = monto.divide(BigDecimal.valueOf(plazoMeses), 2, RoundingMode.HALF_EVEN);
                for (int i = 1; i <= plazoMeses; i++) {
                    fechaIteracion = fechaIteracion.plusMonths(1);
                    BigDecimal interesCuota = saldoPendiente.multiply(tasaMensual, MC).setScale(2, RoundingMode.HALF_EVEN);
                    if (i == plazoMeses) {
                        capitalFijo = saldoPendiente;
                    }
                    BigDecimal cuotaVariable = capitalFijo.add(interesCuota);
                    saldoPendiente = saldoPendiente.subtract(capitalFijo);
                    tabla.add(new CuotaSimuladaDTO(i, fechaIteracion, capitalFijo, interesCuota, cuotaVariable, saldoPendiente.max(BigDecimal.ZERO)));
                }
                break;

            case "AMERICANO":
                BigDecimal interesFijoPeriodico = monto.multiply(tasaMensual, MC).setScale(2, RoundingMode.HALF_EVEN);
                for (int i = 1; i <= plazoMeses; i++) {
                    fechaIteracion = fechaIteracion.plusMonths(1);
                    BigDecimal capitalCuota = (i == plazoMeses) ? monto : BigDecimal.ZERO;
                    BigDecimal cuotaTotal = capitalCuota.add(interesFijoPeriodico);
                    if (i == plazoMeses) {
                        saldoPendiente = BigDecimal.ZERO;
                    }
                    tabla.add(new CuotaSimuladaDTO(i, fechaIteracion, capitalCuota, interesFijoPeriodico, cuotaTotal, saldoPendiente));
                }
                break;
            default:
                throw new IllegalArgumentException("Error Financiero: El sistema '" + sistema + "' no esta soportado.");
        }
        return tabla;
    }

    // ==========================================
    // DESEMBOLSO TRANSACCIONAL CON PARTIDA DOBLE
    // ==========================================

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public Credito desembolsarCredito(Integer creditoId, Integer cuentaAhorrosId, String ipUsuario, String dispositivo) {
        Integer tenantId = TenantContext.getCurrentTenant();

        // 1. Validar crédito
        Credito credito = creditoRepository.findById(creditoId).orElseThrow(() -> new IllegalArgumentException("Error: Contrato no encontrado."));
        if (!"APROBADO".equals(credito.getEstado())) {
            throw new IllegalStateException("Error Financiero: El credito debe estar APROBADO.");
        }

        // 2. Validar cuenta destino de ahorros
        CuentasAhorros cuenta = cuentasRepository.findById(cuentaAhorrosId).orElseThrow(() -> new IllegalArgumentException("Error: Cuenta no encontrada."));

        // 3. Generar tabla física de cuotas en BD
        List<CuotaSimuladaDTO> cuotasCalculadas = simularTablaAmortizacion(credito.getMontoSolicitado(), credito.getPlazoMeses(), credito.getTasaInteresAnual(), credito.getTipoAmortizacion());
        for (CuotaSimuladaDTO cDTO : cuotasCalculadas) {
            CuotasAmortizacion cuotaFisica = new CuotasAmortizacion();
            cuotaFisica.setCredito(credito);
            cuotaFisica.setNumeroCuota(cDTO.getNumeroCuota());
            cuotaFisica.setFechaVencimiento(cDTO.getFechaVencimiento());
            cuotaFisica.setCapitalProyectado(cDTO.getCapital());
            cuotaFisica.setInteresProyectado(cDTO.getInteres());
            cuotaFisica.setEstado("PENDIENTE");
            cuotasRepository.save(cuotaFisica);
        }

        // 4. Actualizar estado contractual del crédito
        credito.setEstado("DESEMBOLSADO");
        credito.setMontoDesembolsado(credito.getMontoSolicitado());
        credito.setFechaDesembolso(LocalDateTime.now());
        creditoRepository.save(credito);

        // 5. Aplicar mutación de saldos en Ledger Bancario
        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoResultante = saldoAnterior.add(credito.getMontoSolicitado());
        cuenta.setSaldo(saldoResultante);
        cuentasRepository.save(cuenta);

        // Generar rastro físico inmutable en Ledger antes del asiento contable
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("CREDITO");
        ledger.setMonto(credito.getMontoSolicitado());
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoResultante);
        ledger.setCanal("PROCESO_BATCH");
        ledger.setReferencia("REF-CRED-" + System.currentTimeMillis());
        ledger.setDescripcion("Desembolso de Credito Contrato N: " + credito.getNumeroCredito());
        ledger.setDireccionIp(ipUsuario);
        ledger.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerGuardado = transaccionesLedgerRepository.save(ledger);

        // 6. REINGENIERÍA CONTABLE: Generación Automática del Asiento de Partida Doble Cuadrado
        PlanCuentas cuentaCartera = planCuentasRepository.findByCodigoContableAndEmpresaId("1.4.01", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.4.01 no parametrizada."));

        PlanCuentas cuentaObligaciones = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 2.1.01.05 no parametrizada."));

        // Estructurar Cabecera del Asiento
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable automático por desembolso de crédito N: " + credito.getNumeroCredito());

        // Estructurar Renglones (Debe y Haber)
        List<AsientosDetalle> detalles = new ArrayList<>();

        // Apunte 1: Al DEBITO (Debe) - Sube el activo de Cartera de créditos
        AsientosDetalle debitoCartera = new AsientosDetalle();
        debitoCartera.setPlanCuentas(cuentaCartera);
        debitoCartera.setTipoAsiento("DEBITO");
        debitoCartera.setMonto(credito.getMontoSolicitado());
        detalles.add(debitoCartera);

        // Apunte 2: Al CREDITO (Haber) - Aumenta el pasivo por Obligaciones con los Socios
        AsientosDetalle creditoPasivo = new AsientosDetalle();
        creditoPasivo.setPlanCuentas(cuentaObligaciones);
        creditoPasivo.setTipoAsiento("CREDITO");
        creditoPasivo.setMonto(credito.getMontoSolicitado());
        detalles.add(creditoPasivo);

        // Registrar y validar contabilidad cuadrada por software
        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // 7. Registro en Bitácora de Auditoría JSONB (Exigencia del ISTQ)
        LogsAuditoria log = new LogsAuditoria();
        log.setSocio(credito.getSocio());
        log.setAccion("DESEMBOLSO_CREDITO");
        log.setTablaAfectada("creditos");
        log.setRegistroId(credito.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("estado", "APROBADO", "saldo_cuenta", saldoAnterior));
        log.setValorNuevo(Map.of("estado", "DESEMBOLSADO", "saldo_cuenta", saldoResultante));
        logsRepository.save(log);

        return credito;
    }

    /**
     * Registra el pago en cascada de las cuotas de un credito, debitando de la cuenta del socio
     * y asentando la partida doble contable correspondiente (cartera e ingresos por interes).
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public Credito registrarPago(PagoRequestDTO pagoDTO, String ipUsuario, String dispositivo) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede registrar un pago sin especificar la institucion (X-Tenant-ID).");
        }

        // 1. Validar credito
        Credito credito = creditoRepository.findById(pagoDTO.getCreditoId())
                .orElseThrow(() -> new IllegalArgumentException("Error: Contrato de credito no encontrado."));

        if (!credito.getEmpresaId().equals(tenantId)) {
            throw new IllegalArgumentException("Error de Seguridad: El credito no pertenece a esta institucion.");
        }

        if (!"DESEMBOLSADO".equals(credito.getEstado()) && !"EN_MORA".equals(credito.getEstado())) {
            throw new IllegalStateException("Error Financiero: El credito no se encuentra en un estado que permita pagos (DESEMBOLSADO o EN_MORA).");
        }

        // 2. Validar cuenta de ahorros de origen
        CuentasAhorros cuenta = cuentasRepository.findById(pagoDTO.getCuentaAhorrosId())
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de ahorros no encontrada."));

        if (!cuenta.getEmpresaId().equals(tenantId)) {
            throw new IllegalArgumentException("Error de Seguridad: La cuenta no pertenece a esta institucion.");
        }

        // 3. Cargar tabla de amortizacion del credito
        List<CuotasAmortizacion> cuotas = cuotasRepository.findByCreditoIdOrderByNumeroCuotaAsc(credito.getId());

        // 4. Calcular deuda total pendiente para evitar cobros en exceso
        BigDecimal totalDeuda = BigDecimal.ZERO;
        for (CuotasAmortizacion cuota : cuotas) {
            if (!"PAGADA".equals(cuota.getEstado())) {
                BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado());
                BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado());
                BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado());
                totalDeuda = totalDeuda.add(moraPendiente).add(interesPendiente).add(capitalPendiente);
            }
        }

        if (totalDeuda.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Error Financiero: El credito ya se encuentra completamente cancelado.");
        }

        // Ajustar monto a cobrar si se ingresa un valor superior a la deuda remanente
        BigDecimal montoAPagar = pagoDTO.getMonto();
        if (montoAPagar.compareTo(totalDeuda) > 0) {
            montoAPagar = totalDeuda;
        }

        // 5. Validar fondos en cuenta de ahorros
        BigDecimal saldoAnterior = cuenta.getSaldo();
        if (saldoAnterior.compareTo(montoAPagar) < 0) {
            throw new IllegalStateException("Error Financiero: Fondos insuficientes en la cuenta de ahorros del socio. Saldo disponible: $" + saldoAnterior);
        }

        // 6. Aplicar el pago en cascada sobre las cuotas pendientes
        BigDecimal remanente = montoAPagar;
        BigDecimal totalCapitalPagado = BigDecimal.ZERO;
        BigDecimal totalInteresPagado = BigDecimal.ZERO;

        for (CuotasAmortizacion cuota : cuotas) {
            if (remanente.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if ("PAGADA".equals(cuota.getEstado())) {
                continue;
            }

            // A. Cobrar Mora (Recargos por mora acumulada)
            BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado());
            if (moraPendiente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoMora = remanente.min(moraPendiente);
                cuota.setMontoMoraPagado(cuota.getMontoMoraPagado().add(pagoMora));
                remanente = remanente.subtract(pagoMora);
                totalInteresPagado = totalInteresPagado.add(pagoMora);
            }

            // B. Cobrar Interes Ordinario Proyectado
            BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado());
            if (interesPendiente.compareTo(BigDecimal.ZERO) > 0 && remanente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoInteres = remanente.min(interesPendiente);
                cuota.setInteresPagado(cuota.getInteresPagado().add(pagoInteres));
                remanente = remanente.subtract(pagoInteres);
                totalInteresPagado = totalInteresPagado.add(pagoInteres);
            }

            // C. Cobrar Capital Proyectado (Amortizacion directa)
            BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado());
            if (capitalPendiente.compareTo(BigDecimal.ZERO) > 0 && remanente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoCapital = remanente.min(capitalPendiente);
                cuota.setCapitalPagado(cuota.getCapitalPagado().add(pagoCapital));
                remanente = remanente.subtract(pagoCapital);
                totalCapitalPagado = totalCapitalPagado.add(pagoCapital);
            }

            // Verificar si la cuota quedo completamente liquidada
            boolean todoCapitalPagado = cuota.getCapitalPagado().compareTo(cuota.getCapitalProyectado()) >= 0;
            boolean todoInteresPagado = cuota.getInteresPagado().compareTo(cuota.getInteresProyectado()) >= 0;

            if (todoCapitalPagado && todoInteresPagado) {
                cuota.setEstado("PAGADA");
                cuota.setFechaUltimoPago(LocalDateTime.now());
            } else if (cuota.getDiasAtraso() > 0) {
                cuota.setEstado("EN_MORA");
            } else {
                cuota.setEstado("PENDIENTE");
            }
            cuotasRepository.save(cuota);
        }

        // 7. Mutar el saldo de la cuenta de ahorros
        BigDecimal saldoResultante = saldoAnterior.subtract(montoAPagar);
        cuenta.setSaldo(saldoResultante);
        cuentasRepository.save(cuenta);

        // 8. Crear registro inmutable en el Ledger
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("DEBITO");
        ledger.setMonto(montoAPagar);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoResultante);
        ledger.setCanal("APP_MOVIL");
        ledger.setReferencia("REF-PAGO-" + System.currentTimeMillis());
        ledger.setDescripcion("Pago de cuota de credito Contrato N: " + credito.getNumeroCredito());
        ledger.setDireccionIp(ipUsuario);
        ledger.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerGuardado = transaccionesLedgerRepository.save(ledger);

        // 9. Verificar si se cancelo la totalidad del credito
        boolean creditoCancelado = true;
        for (CuotasAmortizacion cuota : cuotas) {
            if (!"PAGADA".equals(cuota.getEstado())) {
                creditoCancelado = false;
                break;
            }
        }
        if (creditoCancelado) {
            credito.setEstado("CANCELADO");
            creditoRepository.save(credito);
        }

        // 10. REINGENIERÍA CONTABLE: Generar Asiento Contable Cuadrado por Partida Doble
        PlanCuentas cuentaAhorrosPc = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuracion: Cuenta contable 2.1.01.05 no parametrizada."));

        // Estructurar Cabecera del Asiento
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-PAG-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable por cobro de cuota de credito N: " + credito.getNumeroCredito());

        List<AsientosDetalle> detalles = new ArrayList<>();

        // Apunte 1: Al DEBITO (Debe) - Disminuye el pasivo de cuentas de ahorros (salida de fondos)
        AsientosDetalle debitoAhorros = new AsientosDetalle();
        debitoAhorros.setPlanCuentas(cuentaAhorrosPc);
        debitoAhorros.setTipoAsiento("DEBITO");
        debitoAhorros.setMonto(montoAPagar);
        detalles.add(debitoAhorros);

        // Apunte 2: Al CREDITO (Haber) - Disminuye el activo de Cartera de creditos (capital amortizado)
        if (totalCapitalPagado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaCarteraPc = planCuentasRepository.findByCodigoContableAndEmpresaId("1.4.01", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuracion: Cuenta contable 1.4.01 no parametrizada."));

            AsientosDetalle creditoCartera = new AsientosDetalle();
            creditoCartera.setPlanCuentas(cuentaCarteraPc);
            creditoCartera.setTipoAsiento("CREDITO");
            creditoCartera.setMonto(totalCapitalPagado);
            detalles.add(creditoCartera);
        }

        // Apunte 3: Al CREDITO (Haber) - Aumenta el ingreso por intereses de cartera (interes cobrado)
        if (totalInteresPagado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaIngresosPc = planCuentasRepository.findByCodigoContableAndEmpresaId("5.1.01.05", tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuracion: Cuenta contable 5.1.01.05 no parametrizada."));

            AsientosDetalle creditoIngresos = new AsientosDetalle();
            creditoIngresos.setPlanCuentas(cuentaIngresosPc);
            creditoIngresos.setTipoAsiento("CREDITO");
            creditoIngresos.setMonto(totalInteresPagado);
            detalles.add(creditoIngresos);
        }

        // Registrar y validar contabilidad cuadrada por software
        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // 11. Registro en Bitacora de Auditoria JSONB
        LogsAuditoria log = new LogsAuditoria();
        log.setSocio(credito.getSocio());
        log.setAccion("PAGO_CREDITO");
        log.setTablaAfectada("creditos");
        log.setRegistroId(credito.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("estado", creditoCancelado ? "DESEMBOLSADO" : "DESEMBOLSADO", "saldo_cuenta", saldoAnterior));
        log.setValorNuevo(Map.of("estado", creditoCancelado ? "CANCELADO" : "DESEMBOLSADO", "saldo_cuenta", saldoResultante));
        logsRepository.save(log);

        return credito;
    }
}