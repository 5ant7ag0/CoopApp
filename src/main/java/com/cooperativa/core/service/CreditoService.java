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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(CreditoService.class);

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
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private ProductoCreditoRepository productoCreditoRepository;

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
    private TransaccionesLedgerRepository transaccionesLedgerRepository;

    @Autowired
    private EmpresaService empresaService;

    // ==========================================
    // FUNCIONES CRUD
    // ==========================================

    @Transactional(rollbackFor = Exception.class)
    public Credito crearSolicitud(Credito credito, boolean presencial) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede crear una solicitud de crédito sin un X-Tenant-ID definido.");
        }

        if (credito.getSocio() == null || credito.getSocio().getId() == null) {
            throw new IllegalArgumentException("Error: El socio es obligatorio.");
        }

        if (credito.getProductoCredito() == null || credito.getProductoCredito().getId() == null) {
            throw new IllegalArgumentException("Error: El producto de crédito es obligatorio.");
        }
        
        ProductoCredito producto = productoCreditoRepository.findByIdAndEmpresaId(credito.getProductoCredito().getId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Producto de crédito no encontrado."));
                
        credito.setProductoCredito(producto);
        
        Empresa empresa = empresaService.obtenerMiEmpresa();

        // Validar límites de monto de originación de crédito
        BigDecimal montoSolicitado = credito.getMontoSolicitado();
        if (montoSolicitado == null || 
            montoSolicitado.compareTo(producto.getMontoMinimo()) < 0 || 
            montoSolicitado.compareTo(producto.getMontoMaximo()) > 0) {
            throw new IllegalArgumentException("Error Financiero: El monto del crédito solicitado ($" + montoSolicitado + ") debe estar en el rango de $" + producto.getMontoMinimo() + " a $" + producto.getMontoMaximo() + " USD.");
        }
        
        // Validar límites de plazo
        if (credito.getPlazoMeses() == null ||
            credito.getPlazoMeses() < producto.getPlazoMinimoMeses() ||
            credito.getPlazoMeses() > producto.getPlazoMaximoMeses()) {
            throw new IllegalArgumentException("Error Financiero: El plazo solicitado (" + credito.getPlazoMeses() + " meses) debe estar en el rango de " + producto.getPlazoMinimoMeses() + " a " + producto.getPlazoMaximoMeses() + " meses.");
        }

        // Asignar tasas del producto (Hardcoding erradicado)
        credito.setTasaInteresAnual(producto.getTasaInteresAnual());
        credito.setTasaMoraAnual(producto.getTasaMoraAnual());

        // Buscar cuenta de ahorros a la vista del socio
        List<CuentasAhorros> cuentas = cuentasRepository.findBySocioIdAndEmpresaId(credito.getSocio().getId(), tenantId);
        CuentasAhorros cuentaVista = cuentas.stream()
                .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Error: El socio debe tener una cuenta de ahorros a la vista ACTIVA para procesar la solicitud de crédito."));

        BigDecimal costoTramite = empresa.getCostoTramite().setScale(2, RoundingMode.HALF_UP);
        if (cuentaVista.getSaldo().compareTo(costoTramite) < 0) {
            throw new IllegalStateException("Fondos insuficientes para procesar el trámite. Se requiere un saldo mínimo de $" + costoTramite + " USD en la cuenta de ahorros a la vista.");
        }

        // Debitar el costo del trámite
        BigDecimal saldoAnterior = cuentaVista.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoNuevo = saldoAnterior.subtract(costoTramite).setScale(2, RoundingMode.HALF_UP);
        cuentaVista.setSaldo(saldoNuevo);
        cuentasRepository.save(cuentaVista);

        // Registrar débito en el ledger para cuadre contable
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuentaVista);
        ledger.setTipoTransaccion("DEBITO");
        ledger.setMonto(costoTramite);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoNuevo);
        ledger.setCanal("APP_MOVIL");
        ledger.setReferencia("REF-COST-SOL-" + System.currentTimeMillis());
        ledger.setDescripcion((presencial ? "Originación Presencial - " : "") + "Costo de trámite y consulta de buró de crédito para solicitud N: CRE-" + System.currentTimeMillis() + " (Costo parametrizado: $" + costoTramite + ")");
        ledger.setDireccionIp("127.0.0.1");
        ledger.setDispositivoInfo("Core CoopApp System");
        transaccionesLedgerRepository.save(ledger);

        // Guardar solicitud de crédito
        credito.setNumeroCredito("CRE-" + System.currentTimeMillis());
        credito.setEstado(presencial ? "EN_REVISION" : "SOLICITADO");
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
        List<Credito> creditos = creditoRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
        creditos.forEach(c -> {
            if (c.getCuotas() != null) {
                c.getCuotas().size(); // Force initialization of lazy collection
            }
        });
        return creditos;
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

        Empresa empresa = empresaService.obtenerMiEmpresa();
        BigDecimal tasaDesgravamen = credito.getProductoCredito().getPorcentajeSeguroDesgravamen().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        // Calcular Seguro de Desgravamen (dinámico) y Monto Líquido Acreditado
        BigDecimal seguroDesgravamen = montoSolicitado.multiply(tasaDesgravamen).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoLiquido = montoSolicitado.subtract(seguroDesgravamen).setScale(2, RoundingMode.HALF_UP);

        // 5. Aplicar mutación de saldos en Ledger Bancario (Acreditar únicamente el Monto Líquido)
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoResultante = saldoAnterior.add(montoLiquido).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldoResultante);
        cuentasRepository.save(cuenta);

        // Generar rastro físico inmutable en Ledger antes del asiento contable
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("CREDITO");
        ledger.setMonto(montoLiquido);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoResultante);
        ledger.setCanal("PROCESO_BATCH");
        ledger.setReferencia("REF-CRED-" + System.currentTimeMillis());
        ledger.setDescripcion("Desembolso de Credito Contrato N: " + credito.getNumeroCredito() + 
                " (Seguro Desgravamen del " + credito.getProductoCredito().getPorcentajeSeguroDesgravamen() + "% retenido: $" + seguroDesgravamen + ")");
        ledger.setDireccionIp(ipUsuario);
        ledger.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerGuardado = transaccionesLedgerRepository.save(ledger);

        // 6. REINGENIERÍA CONTABLE: Generación Automática del Asiento de Partida Doble Cuadrado (3 líneas)
        PlanCuentas cuentaCartera = credito.getProductoCredito().getCuentaContableCartera();
        if (cuentaCartera == null) {
            throw new IllegalStateException("Error de Configuración: Cuenta contable para cartera no configurada en los parámetros del producto de crédito.");
        }

        PlanCuentas cuentaObligaciones = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 2.1.01.05 no parametrizada."));

        PlanCuentas cuentaSeguro = credito.getProductoCredito().getCuentaContableSeguro();
        if (credito.getProductoCredito().getPorcentajeSeguroDesgravamen().compareTo(BigDecimal.ZERO) > 0) {
            if (cuentaSeguro == null) {
                throw new IllegalStateException("Error de Configuración: Cuenta contable para seguro de desgravamen no configurada en los parámetros del producto de crédito.");
            }
        }

        // Estructurar Cabecera del Asiento
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable automático por desembolso de crédito N: " + credito.getNumeroCredito() + " con retención de seguro");

        // Estructurar Renglones (Debe y Haber de Partida Doble Cuadrada de 3 Líneas)
        List<AsientosDetalle> detalles = new ArrayList<>();

        // Apunte 1: Al DEBITO (Debe) - Sube el activo de Cartera de créditos (100% de la deuda contractual)
        AsientosDetalle debitoCartera = new AsientosDetalle();
        debitoCartera.setPlanCuentas(cuentaCartera);
        debitoCartera.setTipoAsiento("DEBITO");
        debitoCartera.setMonto(montoSolicitado);
        detalles.add(debitoCartera);

        // Apunte 2: Al CREDITO (Haber) - Aumenta el pasivo por Obligaciones con los Socios (99% Monto Líquido)
        AsientosDetalle creditoPasivo = new AsientosDetalle();
        creditoPasivo.setPlanCuentas(cuentaObligaciones);
        creditoPasivo.setTipoAsiento("CREDITO");
        creditoPasivo.setMonto(montoLiquido);
        detalles.add(creditoPasivo);

        // Apunte 3: Al CREDITO (Haber) - Ingreso por cobro anticipado de Seguro de Desgravamen (si aplica)
        if (seguroDesgravamen.compareTo(BigDecimal.ZERO) > 0) {
            AsientosDetalle creditoSeguro = new AsientosDetalle();
            creditoSeguro.setPlanCuentas(cuentaSeguro);
            creditoSeguro.setTipoAsiento("CREDITO");
            creditoSeguro.setMonto(seguroDesgravamen);
            detalles.add(creditoSeguro);
        }

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
        log.setValorNuevo(Map.of("estado", "DESEMBOLSADO", "saldo_cuenta", saldoResultante, "seguro_desgravamen", seguroDesgravamen, "monto_liquido", montoLiquido));
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
        CajaDiaria cajaActiva = null;
        
        if ("CAJERO".equals(authRol)) {
            UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));
            cajaDiariaService.validarCajaAperturada(cajero.getId(), tenantId);
            cajaActiva = cajaDiariaService.obtenerCajaActiva(authUsername)
                    .orElseThrow(() -> new SecurityException("Error: Caja aperturada no encontrada para el cajero."));
            cajeroId = cajero.getId();
            canalTransaccion = "VENTANILLA";
        }

        if (!"DESEMBOLSADO".equals(credito.getEstado()) && !"EN_MORA".equals(credito.getEstado())) {
            throw new IllegalStateException("Error Financiero: El credito no se encuentra en un estado que permita pagos (DESEMBOLSADO o EN_MORA).");
        }

        // 2. Validar origen de fondos
        CuentasAhorros cuenta = null;
        if ("CUENTA".equals(pagoDTO.getOrigenFondos())) {
            cuenta = cuentasRepository.findById(pagoDTO.getCuentaAhorrosId())
                    .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de ahorros no encontrada."));

            if ("SOCIO".equals(authRol) && !cuenta.getSocio().getIdentificacion().equals(authUsername)) {
                throw new SecurityException("Error de Seguridad: Acceso denegado. La cuenta de origen del pago no pertenece al socio autenticado.");
            }
            if (!cuenta.getEmpresaId().equals(tenantId)) {
                throw new IllegalArgumentException("Error de Seguridad: La cuenta no pertenece a esta institucion.");
            }
        } else if ("EFECTIVO".equals(pagoDTO.getOrigenFondos())) {
            if (cajaActiva == null) {
                throw new SecurityException("Error: Debe tener una caja aperturada para recibir pagos en efectivo.");
            }
        } else {
            throw new IllegalArgumentException("Error: Origen de fondos inválido.");
        }

        return ejecutarPagoCore(credito, cuenta, cajaActiva, pagoDTO.getOrigenFondos(), pagoDTO.getMonto(), canalTransaccion, cajeroId, ipUsuario, dispositivo);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public Credito ejecutarPagoCore(Credito credito, CuentasAhorros cuenta, CajaDiaria caja, String origenFondos, BigDecimal monto, String canalTransaccion, Integer cajeroId, String ipUsuario, String dispositivo) {
        Integer tenantId = TenantContext.getCurrentTenant();

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
        BigDecimal montoAPagar = monto.setScale(2, RoundingMode.HALF_UP);
        if (montoAPagar.compareTo(totalDeuda) > 0) {
            montoAPagar = totalDeuda;
        }

        // 5. Validar origen de fondos y saldo
        BigDecimal saldoAnterior = BigDecimal.ZERO;
        BigDecimal saldoResultante = BigDecimal.ZERO;

        if ("CUENTA".equals(origenFondos)) {
            saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
            if (saldoAnterior.compareTo(montoAPagar) < 0) {
                throw new IllegalStateException("Error Financiero: Fondos insuficientes en la cuenta de ahorros del socio. Saldo disponible: $" + saldoAnterior);
            }
        }

        // 6. Aplicar el pago en cascada sobre las cuotas pendientes
        BigDecimal remanente = montoAPagar;
        BigDecimal totalCapitalPagado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInteresPagado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalMoraPagada = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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
                totalMoraPagada = totalMoraPagada.add(pagoMora).setScale(2, RoundingMode.HALF_UP);
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
            boolean todaMoraPagada = cuota.getMontoMoraPagado().compareTo(cuota.getMontoMoraAcumulado()) >= 0;

            if (todoCapitalPagado && todoInteresPagado && todaMoraPagada) {
                cuota.setEstado("PAGADA");
                cuota.setFechaUltimoPago(LocalDateTime.now());
            } else if (cuota.getDiasAtraso() > 0) {
                cuota.setEstado("EN_MORA");
            } else {
                cuota.setEstado("PENDIENTE");
            }
            cuotasRepository.save(cuota);
        }

        // 7. Mutar saldos y registrar en Ledger o Caja
        TransaccionesLedger ledgerGuardado = null;

        if ("CUENTA".equals(origenFondos)) {
            saldoResultante = saldoAnterior.subtract(montoAPagar).setScale(2, RoundingMode.HALF_UP);
            cuenta.setSaldo(saldoResultante);
            cuentasRepository.save(cuenta);

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
            ledgerGuardado = transaccionesLedgerRepository.save(ledger);
        } else if ("EFECTIVO".equals(origenFondos)) {
            saldoAnterior = BigDecimal.ZERO;
            saldoResultante = montoAPagar;
            
            // For accounting tracking in Ventanilla without altering savings account
            TransaccionesLedger ledger = new TransaccionesLedger();
            ledger.setCuenta(null); // No afecta ninguna cuenta de ahorros
            ledger.setTipoTransaccion("CREDITO"); // CREDITO significa ingreso en la caja (ver CajaDiariaService.cerrarCaja)
            ledger.setMonto(montoAPagar);
            ledger.setSaldoAnterior(saldoAnterior);
            ledger.setSaldoResultante(saldoResultante);
            ledger.setCanal(canalTransaccion);
            ledger.setReferencia("REF-PAGO-" + System.currentTimeMillis());
            ledger.setDescripcion("Pago de cuota en efectivo Contrato N: " + credito.getNumeroCredito());
            ledger.setUsuarioAdminId(cajeroId);
            ledger.setDireccionIp(ipUsuario);
            ledger.setDispositivoInfo(dispositivo);
            ledgerGuardado = transaccionesLedgerRepository.save(ledger);
        }

        // 8. Verificar si se cancelo la totalidad del credito
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

        // 9. REINGENIERÍA CONTABLE: Generar Asiento Contable Cuadrado por Partida Doble
        Empresa empresa = empresaService.obtenerPorId(tenantId);

        // Estructurar Cabecera del Asiento
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-PAG-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable por cobro de cuota de credito N: " + credito.getNumeroCredito());

        List<AsientosDetalle> detalles = new ArrayList<>();

        AsientosDetalle debitoOrigen = new AsientosDetalle();
        if ("CUENTA".equals(origenFondos)) {
            if (cuenta.getProductoAhorro() == null) {
                throw new IllegalStateException("Error de Configuración: La cuenta de ahorros no tiene un producto de ahorro asociado.");
            }
            PlanCuentas cuentaAhorrosPc = cuenta.getProductoAhorro().getCuentaContablePasivo();
            if (cuentaAhorrosPc == null) {
                throw new IllegalStateException("Error de Configuracion: Cuenta contable pasivo no parametrizada en el producto de ahorro.");
            }
            debitoOrigen.setPlanCuentas(cuentaAhorrosPc);
        } else {
            PlanCuentas cuentaCajaPc = empresa.getCuentaContableCaja();
            if (cuentaCajaPc == null) {
                throw new IllegalStateException("Error de Configuracion: Cuenta contable de caja no parametrizada.");
            }
            debitoOrigen.setPlanCuentas(cuentaCajaPc);
        }
        debitoOrigen.setTipoAsiento("DEBITO");
        debitoOrigen.setMonto(montoAPagar);
        detalles.add(debitoOrigen);

        // Apunte 2: Al CREDITO (Haber) - Disminuye el activo de Cartera de creditos (capital amortizado)
        if (totalCapitalPagado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaCarteraPc = credito.getProductoCredito().getCuentaContableCartera();
            if (cuentaCarteraPc == null) throw new IllegalStateException("Error de Configuracion: Cuenta contable Cartera no parametrizada en Producto de Crédito.");

            AsientosDetalle creditoCartera = new AsientosDetalle();
            creditoCartera.setPlanCuentas(cuentaCarteraPc);
            creditoCartera.setTipoAsiento("CREDITO");
            creditoCartera.setMonto(totalCapitalPagado);
            detalles.add(creditoCartera);
        }

        // Apunte 3: Al CREDITO (Haber) - Aumenta el ingreso por intereses de cartera (interes cobrado)
        if (totalInteresPagado.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaIngresosPc = credito.getProductoCredito().getCuentaContableIngresosIntereses();
            if (cuentaIngresosPc == null) throw new IllegalStateException("Error de Configuracion: Cuenta contable Ingresos Intereses no parametrizada en Producto de Crédito.");

            AsientosDetalle creditoIngresos = new AsientosDetalle();
            creditoIngresos.setPlanCuentas(cuentaIngresosPc);
            creditoIngresos.setTipoAsiento("CREDITO");
            creditoIngresos.setMonto(totalInteresPagado);
            detalles.add(creditoIngresos);
        }

        // Apunte 4: Al CREDITO (Haber) - Aumenta el ingreso por mora (si existe)
        if (totalMoraPagada.compareTo(BigDecimal.ZERO) > 0) {
            PlanCuentas cuentaMoraPc = credito.getProductoCredito().getCuentaContableMora();
            if (cuentaMoraPc == null) throw new IllegalStateException("Error de Configuracion: Cuenta contable Mora no parametrizada en Producto de Crédito.");

            AsientosDetalle creditoMora = new AsientosDetalle();
            creditoMora.setPlanCuentas(cuentaMoraPc);
            creditoMora.setTipoAsiento("CREDITO");
            creditoMora.setMonto(totalMoraPagada);
            detalles.add(creditoMora);
        }

        // Registrar y validar contabilidad cuadrada por software
        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // 11. Registro en Bitacora de Auditoria JSONB
        LogsAuditoria logAuditoria = new LogsAuditoria();
        logAuditoria.setSocio(credito.getSocio());
        logAuditoria.setAccion("PAGO_CREDITO");
        logAuditoria.setTablaAfectada("creditos");
        logAuditoria.setRegistroId(credito.getId());
        logAuditoria.setDireccionIp(ipUsuario);
        logAuditoria.setDispositivoInfo(dispositivo);
        logAuditoria.setValorAnterior(Map.of("estado", creditoCancelado ? "DESEMBOLSADO" : "DESEMBOLSADO", "saldo_cuenta", saldoAnterior));
        logAuditoria.setValorNuevo(Map.of("estado", creditoCancelado ? "CANCELADO" : "DESEMBOLSADO", "saldo_cuenta", saldoResultante));
        logsAuditoriaService.registrarLog(logAuditoria);

        return credito;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void ejecutarDebitoAutomaticoDeCuota(Long cuotaId) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar debito sin X-Tenant-ID.");
        }

        CuotasAmortizacion cuota = cuotasRepository.findById(cuotaId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuota de amortizacion no encontrada. ID: " + cuotaId));

        Credito credito = cuota.getCredito();
        if (!"DESEMBOLSADO".equals(credito.getEstado()) && !"EN_MORA".equals(credito.getEstado())) {
            return;
        }

        if ("PAGADA".equals(cuota.getEstado())) {
            return;
        }

        Socio socio = credito.getSocio();

        // Buscar cuenta de ahorros a la vista del socio
        List<CuentasAhorros> cuentas = cuentasRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
        CuentasAhorros cuentaVista = cuentas.stream()
                .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                .findFirst()
                .orElse(null);

        if (cuentaVista == null) {
            return;
        }

        // Calcular monto remanente adeudado en esta cuota exacta
        BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoCuota = moraPendiente.add(interesPendiente).add(capitalPendiente).setScale(2, RoundingMode.HALF_UP);

        if (montoCuota.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // Revisar saldo y pagar
        if (cuentaVista.getSaldo().compareTo(montoCuota) >= 0) {
            ejecutarPagoCore(
                    credito,
                    cuentaVista,
                    null,
                    "CUENTA",
                    montoCuota,
                    "DEBITO_AUTOMATICO",
                    null,
                    "127.0.0.1",
                    "SISTEMA_BATCH"
            );
        }
    }

    public void ejecutarDebitosAutomaticosParaTenant(LocalDate fecha) {
        Integer tenantId = TenantContext.getCurrentTenant();
        log.info("Iniciando ejecucion de debitos automaticos para Tenant ID: {} en la fecha: {}", tenantId, fecha);

        List<CuotasAmortizacion> cuotasExigibles = cuotasRepository.findPendingCuotasExigibles(fecha);
        log.info("Se encontraron {} cuotas exigibles/vencidas para procesar en Tenant ID: {}", cuotasExigibles.size(), tenantId);

        int exitosos = 0;
        int omitidos = 0;

        for (CuotasAmortizacion cuota : cuotasExigibles) {
            try {
                BigDecimal moraPendiente = cuota.getMontoMoraAcumulado().subtract(cuota.getMontoMoraPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal interesPendiente = cuota.getInteresProyectado().subtract(cuota.getInteresPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal capitalPendiente = cuota.getCapitalProyectado().subtract(cuota.getCapitalPagado()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal montoCuota = moraPendiente.add(interesPendiente).add(capitalPendiente).setScale(2, RoundingMode.HALF_UP);

                Socio socio = cuota.getCredito().getSocio();
                List<CuentasAhorros> cuentas = cuentasRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
                CuentasAhorros cuentaVista = cuentas.stream()
                        .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                        .findFirst()
                        .orElse(null);

                if (cuentaVista != null && cuentaVista.getSaldo().compareTo(montoCuota) >= 0) {
                    ejecutarDebitoAutomaticoDeCuota(cuota.getId());
                    exitosos++;
                } else {
                    omitidos++;
                }
            } catch (Exception e) {
                log.error("Error al procesar debito automatico para cuota N: " + cuota.getNumeroCuota() + " de Credito ID: " + cuota.getCredito().getId() + ". Detalle: " + e.getMessage(), e);
            }
        }
        log.info("Ejecucion finalizada para Tenant ID: {}. Resultados: {} debito(s) exitoso(s), {} omitido(s) por falta de fondos o inactividad.", tenantId, exitosos, omitidos);
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

    @Transactional(readOnly = true)
    public List<Credito> obtenerCreditosSocioPorId(Integer socioId) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede obtener créditos sin especificar la institución (X-Tenant-ID).");
        }
        List<Credito> creditos = creditoRepository.findBySocioIdAndEmpresaId(socioId, tenantId);
        creditos.forEach(c -> {
            if (c.getCuotas() != null) {
                c.getCuotas().size(); // Force initialization of lazy collection
            }
        });
        return creditos;
    }
}