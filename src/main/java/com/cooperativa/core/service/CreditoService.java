package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.CuotaSimuladaDTO;
import com.cooperativa.core.dto.PagoRequestDTO;
import com.cooperativa.core.model.*;
import com.cooperativa.core.repository.*;
import com.cooperativa.core.amortizacion.AmortizacionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;

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
    private AmortizacionFactory amortizacionFactory;

    @Autowired
    private CreditoRepository creditoRepository;

    @Autowired
    private CuotasAmortizacionRepository cuotasRepository;

    @Autowired
    private CuentasAhorrosRepository cuentasRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private LogsAuditoriaRepository logsRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private CajaDiariaService cajaDiariaService;

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

    @Transactional(rollbackFor = Exception.class)
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

    // LEER TODOS LOS CRÉDITOS DEL SOCIO AUTENTICADO
    public List<Credito> obtenerCreditosSocio(String username) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede obtener créditos sin especificar la institución (X-Tenant-ID).");
        }
        Socio socio = socioRepository.findByIdentificacionAndEmpresaId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta institución."));
        return creditoRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
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
        return amortizacionFactory.getEstrategia(sistema).calcular(monto, plazoMeses, tasaAnual);
    }

    // ==========================================
    // DESEMBOLSO TRANSACCIONAL CON PARTIDA DOBLE
    // ==========================================

    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
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

        // Sanitización decimal contable: forzar escala a 2 posiciones con redondeo HALF_UP
        BigDecimal montoSolicitado = credito.getMontoSolicitado().setScale(2, RoundingMode.HALF_UP);

        // 3. Generar tabla física de cuotas en BD
        List<CuotaSimuladaDTO> cuotasCalculadas = simularTablaAmortizacion(montoSolicitado, credito.getPlazoMeses(), credito.getTasaInteresAnual(), credito.getTipoAmortizacion());
        for (CuotaSimuladaDTO cDTO : cuotasCalculadas) {
            CuotasAmortizacion cuotaFisica = new CuotasAmortizacion();
            cuotaFisica.setCredito(credito);
            cuotaFisica.setNumeroCuota(cDTO.getNumeroCuota());
            cuotaFisica.setFechaVencimiento(cDTO.getFechaVencimiento());
            cuotaFisica.setCapitalProyectado(cDTO.getCapital().setScale(2, RoundingMode.HALF_UP));
            cuotaFisica.setInteresProyectado(cDTO.getInteres().setScale(2, RoundingMode.HALF_UP));
            cuotaFisica.setEstado("PENDIENTE");
            cuotasRepository.save(cuotaFisica);
        }

        // 4. Actualizar estado contractual del crédito
        credito.setEstado("DESEMBOLSADO");
        credito.setMontoDesembolsado(montoSolicitado);
        credito.setFechaDesembolso(LocalDateTime.now());
        creditoRepository.save(credito);

        // 5. Aplicar mutación de saldos en Ledger Bancario
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoResultante = saldoAnterior.add(montoSolicitado).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldoResultante);
        cuentasRepository.save(cuenta);

        // Generar rastro físico inmutable en Ledger antes del asiento contable
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("CREDITO");
        ledger.setMonto(montoSolicitado);
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
        debitoCartera.setMonto(montoSolicitado);
        detalles.add(debitoCartera);

        // Apunte 2: Al CREDITO (Haber) - Aumenta el pasivo por Obligaciones con los Socios
        AsientosDetalle creditoPasivo = new AsientosDetalle();
        creditoPasivo.setPlanCuentas(cuentaObligaciones);
        creditoPasivo.setTipoAsiento("CREDITO");
        creditoPasivo.setMonto(montoSolicitado);
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
        logsAuditoriaService.registrarLog(log);

        return credito;
    }

    /**
     * Registra el pago en cascada de las cuotas de un credito, debitando de la cuenta del socio
     * y asentando la partida doble contable correspondiente (cartera e ingresos por interes).
     */
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public Credito registrarPago(PagoRequestDTO pagoDTO, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }

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

        // Enlace al control de caja si el operador es cajero
        Integer cajeroId = null;
        String canalTransaccion = "APP_MOVIL";
        if ("CAJERO".equals(authRol)) {
            UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));
            cajaDiariaService.validarCajaAperturada(cajero.getId(), tenantId);
            cajeroId = cajero.getId();
            canalTransaccion = "VENTANILLA";
        }

        if (!"DESEMBOLSADO".equals(credito.getEstado()) && !"EN_MORA".equals(credito.getEstado())) {
            throw new IllegalStateException("Error Financiero: El credito no se encuentra en un estado que permita pagos (DESEMBOLSADO o EN_MORA).");
        }

        // 2. Validar cuenta de ahorros de origen
        CuentasAhorros cuenta = cuentasRepository.findById(pagoDTO.getCuentaAhorrosId())
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de ahorros no encontrada."));

        // BLINDAJE DE PROPIEDAD: Si el rol es SOCIO, validar que la cuenta de origen le pertenezca
        if ("SOCIO".equals(authRol) && !cuenta.getSocio().getIdentificacion().equals(authUsername)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado. La cuenta de origen del pago no pertenece al socio autenticado.");
        }

        if (!cuenta.getEmpresaId().equals(tenantId)) {
            throw new IllegalArgumentException("Error de Seguridad: La cuenta no pertenece a esta institucion.");
        }

        // 3. Cargar tabla de amortizacion del credito
        List<CuotasAmortizacion> cuotas = cuotasRepository.findByCreditoIdOrderByNumeroCuotaAsc(credito.getId());

        // 4. Calcular deuda total pendiente para evitar cobros en exceso
        BigDecimal totalDeuda = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CuotasAmortizacion cuota : cuotas) {
            if (!"PAGADA".equals(cuota.getEstado())) {
                BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado()).setScale(2, RoundingMode.HALF_UP);
                totalDeuda = totalDeuda.add(moraPendiente).add(interesPendiente).add(capitalPendiente);
            }
        }

        if (totalDeuda.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Error Financiero: El credito ya se encuentra completamente cancelado.");
        }

        // Ajustar monto a cobrar si se ingresa un valor superior a la deuda remanente
        BigDecimal montoAPagar = pagoDTO.getMonto().setScale(2, RoundingMode.HALF_UP);
        if (montoAPagar.compareTo(totalDeuda) > 0) {
            montoAPagar = totalDeuda;
        }

        // 5. Validar fondos en cuenta de ahorros
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        if (saldoAnterior.compareTo(montoAPagar) < 0) {
            throw new IllegalStateException("Error Financiero: Fondos insuficientes en la cuenta de ahorros del socio. Saldo disponible: $" + saldoAnterior);
        }

        // 6. Aplicar el pago en cascada sobre las cuotas pendientes
        BigDecimal remanente = montoAPagar;
        BigDecimal totalCapitalPagado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInteresPagado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (CuotasAmortizacion cuota : cuotas) {
            if (remanente.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if ("PAGADA".equals(cuota.getEstado())) {
                continue;
            }

            // A. Cobrar Mora (Recargos por mora acumulada)
            BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado()).setScale(2, RoundingMode.HALF_UP);
            if (moraPendiente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoMora = remanente.min(moraPendiente).setScale(2, RoundingMode.HALF_UP);
                cuota.setMontoMoraPagado(cuota.getMontoMoraPagado().add(pagoMora).setScale(2, RoundingMode.HALF_UP));
                remanente = remanente.subtract(pagoMora).setScale(2, RoundingMode.HALF_UP);
                totalInteresPagado = totalInteresPagado.add(pagoMora).setScale(2, RoundingMode.HALF_UP);
            }

            // B. Cobrar Interes Ordinario Proyectado
            BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado()).setScale(2, RoundingMode.HALF_UP);
            if (interesPendiente.compareTo(BigDecimal.ZERO) > 0 && remanente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoInteres = remanente.min(interesPendiente).setScale(2, RoundingMode.HALF_UP);
                cuota.setInteresPagado(cuota.getInteresPagado().add(pagoInteres).setScale(2, RoundingMode.HALF_UP));
                remanente = remanente.subtract(pagoInteres).setScale(2, RoundingMode.HALF_UP);
                totalInteresPagado = totalInteresPagado.add(pagoInteres).setScale(2, RoundingMode.HALF_UP);
            }

            // C. Cobrar Capital Proyectado (Amortizacion directa)
            BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado()).setScale(2, RoundingMode.HALF_UP);
            if (capitalPendiente.compareTo(BigDecimal.ZERO) > 0 && remanente.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pagoCapital = remanente.min(capitalPendiente).setScale(2, RoundingMode.HALF_UP);
                cuota.setCapitalPagado(cuota.getCapitalPagado().add(pagoCapital).setScale(2, RoundingMode.HALF_UP));
                remanente = remanente.subtract(pagoCapital).setScale(2, RoundingMode.HALF_UP);
                totalCapitalPagado = totalCapitalPagado.add(pagoCapital).setScale(2, RoundingMode.HALF_UP);
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
        BigDecimal saldoResultante = saldoAnterior.subtract(montoAPagar).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldoResultante);
        cuentasRepository.save(cuenta);

        // 8. Crear registro inmutable en el Ledger
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("DEBITO");
        ledger.setMonto(montoAPagar);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoResultante);
        ledger.setCanal(canalTransaccion);
        ledger.setReferencia("REF-PAGO-" + System.currentTimeMillis());
        ledger.setDescripcion("Pago de cuota de credito Contrato N: " + credito.getNumeroCredito());
        ledger.setUsuarioAdminId(cajeroId);
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
        logsAuditoriaService.registrarLog(log);

        return credito;
    }

    // OBTENER CRONOGRAMA DE CUOTAS DE AMORTIZACION CON VALIDACION DE SEGURIDAD
    public List<CuotasAmortizacion> obtenerAmortizacion(Integer creditoId, String authUsername, String authRol) {
        // Validar que el crédito exista y pertenezca al Tenant activo
        Credito credito = obtenerPorId(creditoId);

        // Blindaje de propiedad: si el rol es SOCIO, el crédito debe pertenecerle
        if ("SOCIO".equals(authRol) && !credito.getSocio().getIdentificacion().equals(authUsername)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado. No es propietario de este crédito.");
        }

        return cuotasRepository.findByCreditoIdOrderByNumeroCuotaAsc(credito.getId());
    }

    @Transactional
    public Credito revisarCredito(Integer id, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        Credito credito = obtenerPorId(id);
        if ("SOLICITADO".equals(credito.getEstado())) {
            credito.setEstado("EN_REVISION");
            if (authUsername != null) {
                UsuariosAdmin oficial = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId).orElse(null);
                if (oficial != null) {
                    credito.setUsuarioOficialId(oficial.getId());
                }
            }
            return creditoRepository.save(credito);
        }
        return credito;
    }

    @Transactional
    public Credito rechazarCredito(Integer id, String motivo) {
        Credito credito = obtenerPorId(id);
        if (!"SOLICITADO".equals(credito.getEstado()) && !"EN_REVISION".equals(credito.getEstado())) {
            throw new IllegalStateException("Error: Solo se pueden rechazar créditos en estado SOLICITADO o EN_REVISION.");
        }
        credito.setEstado("RECHAZADO");
        credito.setMotivoRechazo(motivo);
        return creditoRepository.save(credito);
    }
}