package com.cooperativa.core.service;

import com.cooperativa.core.model.AsientosCabecera;
import com.cooperativa.core.model.AsientosDetalle;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.CierreAnual;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.AsientosCabeceraRepository;
import com.cooperativa.core.repository.AsientosDetalleRepository;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.CierreAnualRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContabilidadService {

    @Autowired
    private AsientosCabeceraRepository cabeceraRepository;

    @Autowired
    private AsientosDetalleRepository detalleRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private CierreAnualRepository cierreAnualRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    /**
     * Registra un asiento contable completo aplicando validación estricta de Partida Doble.
     */
    @Transactional(rollbackFor = Exception.class)
    public AsientosCabecera registrarAsientoCuadrado(AsientosCabecera cabecera, List<AsientosDetalle> detalles) {

        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se pueden registrar asientos sin un X-Tenant-ID definido.");
        }
        int anio = cabecera.getFechaAsiento().getYear();
        if (cierreAnualRepository.existsByAnioFiscalAndEmpresaId(anio, tenantId)) {
            throw new IllegalStateException("Error de Seguridad: El año fiscal " + anio + " ya se encuentra cerrado. No se pueden registrar o modificar movimientos.");
        }

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

        // Auto-asignar referencia si no se encuentra definida
        if (cabecera.getReferencia() == null) {
            if (cabecera.getTransaccionLedger() != null && cabecera.getTransaccionLedger().getReferencia() != null) {
                cabecera.setReferencia(cabecera.getTransaccionLedger().getReferencia());
            } else {
                cabecera.setReferencia(cabecera.getNumeroAsiento());
            }
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

    // OBTENER PLAN DE CUENTAS COMPLETO
    public List<PlanCuentas> obtenerPlanCuentas() {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        return planCuentasRepository.findByEmpresaIdOrderByCodigoContableAsc(tenantId);
    }

    // OBTENER MOVIMIENTOS DEL LIBRO DIARIO POR RANGO DE FECHAS DE FORMA PAGINADA
    public java.util.Map<String, Object> obtenerLibroDiarioPaginado(String desdeStr, String hastaStr, int page, int size) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        java.time.LocalDateTime inicio;
        java.time.LocalDateTime fin;

        if (desdeStr == null || desdeStr.isEmpty()) {
            inicio = java.time.LocalDate.now().minusMonths(1).atStartOfDay(); // Default 1 month
        } else {
            inicio = java.time.LocalDate.parse(desdeStr).atStartOfDay();
        }

        if (hastaStr == null || hastaStr.isEmpty()) {
            fin = java.time.LocalDate.now().atTime(23, 59, 59, 999999999);
        } else {
            fin = java.time.LocalDate.parse(hastaStr).atTime(23, 59, 59, 999999999);
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            page, 
            size, 
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "fechaAsiento")
        );

        org.springframework.data.domain.Page<AsientosCabecera> pageResult = cabeceraRepository.findByEmpresaIdAndFechaAsientoBetween(
            tenantId, 
            inicio, 
            fin, 
            pageable
        );

        List<java.util.Map<String, Object>> mappedContent = pageResult.getContent().stream().map(c -> {
            List<java.util.Map<String, Object>> detallesList = c.getDetalles().stream().map(d -> {
                java.util.Map<String, Object> det = new java.util.HashMap<>();
                det.put("id", d.getId());
                det.put("codigoContable", d.getPlanCuentas().getCodigoContable());
                det.put("nombreCuenta", d.getPlanCuentas().getNombreCuenta());
                det.put("tipoAsiento", d.getTipoAsiento());
                det.put("monto", d.getMonto());
                return det;
            }).toList();

            // Calculate total amount of the entry (sum of DEBITs)
            java.math.BigDecimal totalAsiento = c.getDetalles().stream()
                .filter(d -> "DEBITO".equals(d.getTipoAsiento()))
                .map(AsientosDetalle::getMonto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId());
            map.put("numeroAsiento", c.getNumeroAsiento());
            map.put("glosa", c.getGlosa());
            map.put("fechaAsiento", c.getFechaAsiento().toString());
            map.put("referencia", c.getReferencia() != null ? c.getReferencia() : "");
            map.put("totalAsiento", totalAsiento);
            map.put("detalles", detallesList);
            return map;
        }).toList();

        return java.util.Map.of(
            "content", mappedContent,
            "totalElements", pageResult.getTotalElements(),
            "totalPages", pageResult.getTotalPages(),
            "size", pageResult.getSize(),
            "number", pageResult.getNumber()
        );
    }

    // OBTENER LIBRO MAYOR (ANÁLISIS POR CUENTA CON PAGINACIÓN Y ARRASTRE DE SALDOS)
    public java.util.Map<String, Object> obtenerLibroMayor(Integer cuentaId, String desdeStr, String hastaStr, Integer page, Integer size) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        PlanCuentas cuenta = planCuentasRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Error: La cuenta contable no existe."));

        if (!cuenta.getEmpresaId().equals(tenantId)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado a esta cuenta contable.");
        }

        java.time.LocalDateTime desde;
        java.time.LocalDateTime hasta;

        if (desdeStr == null || desdeStr.isEmpty()) {
            desde = java.time.LocalDate.now().minusMonths(1).atStartOfDay(); // Default 1 month
        } else {
            desde = java.time.LocalDate.parse(desdeStr).atStartOfDay();
        }

        if (hastaStr == null || hastaStr.isEmpty()) {
            hasta = java.time.LocalDate.now().atTime(23, 59, 59, 999999999);
        } else {
            hasta = java.time.LocalDate.parse(hastaStr).atTime(23, 59, 59, 999999999);
        }

        // Naturaleza contable: Deudora ('ACTIVO', 'GASTO') vs Acreedora ('PASIVO', 'PATRIMONIO', 'INGRESO')
        String tipo = cuenta.getTipoCuenta();
        boolean esDeudora = "ACTIVO".equals(tipo) || "GASTO".equals(tipo);

        // 1. Calcular Saldo Inicial
        List<AsientosDetalle> histPrevio = detalleRepository.findBeforeDate(cuentaId, tenantId, desde);
        BigDecimal saldoInicial = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (AsientosDetalle d : histPrevio) {
            if ("DEBITO".equals(d.getTipoAsiento())) {
                if (esDeudora) {
                    saldoInicial = saldoInicial.add(d.getMonto());
                } else {
                    saldoInicial = saldoInicial.subtract(d.getMonto());
                }
            } else if ("CREDITO".equals(d.getTipoAsiento())) {
                if (esDeudora) {
                    saldoInicial = saldoInicial.subtract(d.getMonto());
                } else {
                    saldoInicial = saldoInicial.add(d.getMonto());
                }
            }
        }

        // 2. Sumatoria completa de Débitos y Créditos del rango de fechas
        BigDecimal totalDebitoPeriodo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCreditoPeriodo = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<Object[]> sumsPeriod = detalleRepository.sumMovementsPeriod(cuentaId, tenantId, desde, hasta);
        if (sumsPeriod != null && !sumsPeriod.isEmpty() && sumsPeriod.get(0) != null) {
            Object[] row = sumsPeriod.get(0);
            if (row[0] != null) totalDebitoPeriodo = toBigDecimal(row[0]);
            if (row[1] != null) totalCreditoPeriodo = toBigDecimal(row[1]);
        }

        // 3. Saldo Final del Periodo Completo
        BigDecimal saldoFinal;
        if (esDeudora) {
            saldoFinal = saldoInicial.add(totalDebitoPeriodo).subtract(totalCreditoPeriodo);
        } else {
            saldoFinal = saldoInicial.subtract(totalDebitoPeriodo).add(totalCreditoPeriodo);
        }

        // 4. Carga de movimientos (Paginados o Completos)
        List<AsientosDetalle> movsPeriodo;
        int totalPages = 1;
        long totalElements = 0;
        int sizeVal = 0;
        int pageVal = 0;

        if (page != null && size != null) {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<AsientosDetalle> pageResult = detalleRepository.findBetweenDatesPaged(cuentaId, tenantId, desde, hasta, pageable);
            movsPeriodo = pageResult.getContent();
            totalPages = pageResult.getTotalPages();
            totalElements = pageResult.getTotalElements();
            sizeVal = pageResult.getSize();
            pageVal = pageResult.getNumber();
        } else {
            movsPeriodo = detalleRepository.findBetweenDates(cuentaId, tenantId, desde, hasta);
            totalElements = movsPeriodo.size();
            sizeVal = movsPeriodo.size();
        }

        // 5. Calcular Saldo de Arrastre para la página actual
        BigDecimal saldoDeArrastre = saldoInicial;
        if (page != null && page > 0 && !movsPeriodo.isEmpty()) {
            AsientosDetalle firstDetail = movsPeriodo.get(0);
            List<Object[]> sumsBefore = detalleRepository.sumMovementsBefore(
                cuentaId, tenantId, desde, firstDetail.getAsientoCabecera().getFechaAsiento(), firstDetail.getId()
            );
            BigDecimal debPrev = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal credPrev = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (sumsBefore != null && !sumsBefore.isEmpty() && sumsBefore.get(0) != null) {
                Object[] row = sumsBefore.get(0);
                if (row[0] != null) debPrev = toBigDecimal(row[0]);
                if (row[1] != null) credPrev = toBigDecimal(row[1]);
            }
            if (esDeudora) {
                saldoDeArrastre = saldoInicial.add(debPrev).subtract(credPrev);
            } else {
                saldoDeArrastre = saldoInicial.subtract(debPrev).add(credPrev);
            }
        }

        // 6. Generar lista con saldos acumulados dinámicos
        BigDecimal saldoCorriente = saldoDeArrastre;
        java.util.List<java.util.Map<String, Object>> movimientosList = new java.util.ArrayList<>();

        for (AsientosDetalle d : movsPeriodo) {
            BigDecimal debe = "DEBITO".equals(d.getTipoAsiento()) ? d.getMonto() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal haber = "CREDITO".equals(d.getTipoAsiento()) ? d.getMonto() : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if ("DEBITO".equals(d.getTipoAsiento())) {
                if (esDeudora) {
                    saldoCorriente = saldoCorriente.add(d.getMonto());
                } else {
                    saldoCorriente = saldoCorriente.subtract(d.getMonto());
                }
            } else if ("CREDITO".equals(d.getTipoAsiento())) {
                if (esDeudora) {
                    saldoCorriente = saldoCorriente.subtract(d.getMonto());
                } else {
                    saldoCorriente = saldoCorriente.add(d.getMonto());
                }
            }

            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", d.getId());
            m.put("fecha", d.getAsientoCabecera().getFechaAsiento().toString());
            m.put("numeroAsiento", d.getAsientoCabecera().getNumeroAsiento());
            m.put("referencia", d.getAsientoCabecera().getReferencia() != null ? d.getAsientoCabecera().getReferencia() : "");
            m.put("concepto", d.getAsientoCabecera().getGlosa());
            m.put("debe", debe);
            m.put("haber", haber);
            m.put("saldoAcumulado", saldoCorriente);
            movimientosList.add(m);
        }

        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("cuentaId", cuenta.getId());
        res.put("codigoContable", cuenta.getCodigoContable());
        res.put("nombreCuenta", cuenta.getNombreCuenta());
        res.put("tipoCuenta", tipo);
        res.put("saldoInicial", saldoInicial);
        res.put("totalDebitoPeriodo", totalDebitoPeriodo);
        res.put("totalCreditoPeriodo", totalCreditoPeriodo);
        res.put("saldoFinal", saldoFinal);
        res.put("movimientos", movimientosList);
        res.put("totalPages", totalPages);
        res.put("totalElements", totalElements);
        res.put("size", sizeVal);
        res.put("number", pageVal);
        return res;
    }

    // OBTENER ASIENTO ESPECÍFICO POR NÚMERO
    public java.util.Map<String, Object> obtenerAsientoPorNumero(String numeroAsiento) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        AsientosCabecera c = cabeceraRepository.findByEmpresaIdAndNumeroAsiento(tenantId, numeroAsiento)
                .orElseThrow(() -> new IllegalArgumentException("Error: El asiento contable no existe."));

        List<java.util.Map<String, Object>> detallesList = c.getDetalles().stream().map(d -> {
            java.util.Map<String, Object> det = new java.util.HashMap<>();
            det.put("id", d.getId());
            det.put("codigoContable", d.getPlanCuentas().getCodigoContable());
            det.put("nombreCuenta", d.getPlanCuentas().getNombreCuenta());
            det.put("tipoAsiento", d.getTipoAsiento());
            det.put("monto", d.getMonto());
            return det;
        }).toList();

        java.math.BigDecimal totalAsiento = c.getDetalles().stream()
            .filter(d -> "DEBITO".equals(d.getTipoAsiento()))
            .map(AsientosDetalle::getMonto)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", c.getId());
        map.put("numeroAsiento", c.getNumeroAsiento());
        map.put("glosa", c.getGlosa());
        map.put("fechaAsiento", c.getFechaAsiento().toString());
        map.put("referencia", c.getReferencia() != null ? c.getReferencia() : "");
        map.put("totalAsiento", totalAsiento);
        map.put("detalles", detallesList);
        return map;
    }

    /**
     * Crea una nueva subcuenta bajo una cuenta padre autogenerando el código contable correlativo.
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanCuentas crearSubcuenta(Integer padreId, String nombreCuenta, Boolean esMovimiento) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        if (nombreCuenta == null || nombreCuenta.trim().isEmpty()) {
            throw new IllegalArgumentException("Error: El nombre de la cuenta no puede estar vacío.");
        }

        PlanCuentas padre = planCuentasRepository.findById(padreId)
                .orElseThrow(() -> new IllegalArgumentException("Error: La cuenta padre no existe."));

        if (!padre.getEmpresaId().equals(tenantId)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado a la cuenta padre.");
        }

        // Si el padre actualmente es de movimiento, debemos verificar si tiene transacciones en el Libro Diario
        if (padre.getEsMovimiento()) {
            boolean tieneTransacciones = detalleRepository.existsByPlanCuentasId(padreId);
            if (tieneTransacciones) {
                throw new IllegalStateException("Error Contable: No se puede crear una subcuenta porque la cuenta padre ya registra movimientos en el Libro Diario.");
            }
        }

        // Obtener todas las cuentas del inquilino para buscar los hijos directos
        List<PlanCuentas> todasLasCuentas = planCuentasRepository.findByEmpresaId(tenantId);
        String padreCodigo = padre.getCodigoContable();
        long padreDots = countDots(padreCodigo);

        List<PlanCuentas> hijosDirectos = todasLasCuentas.stream()
                .filter(c -> c.getCodigoContable().startsWith(padreCodigo + "."))
                .filter(c -> countDots(c.getCodigoContable()) == padreDots + 1)
                .toList();

        int nextValue = 1;
        int formatLength = (padreDots == 0) ? 1 : 2;

        if (!hijosDirectos.isEmpty()) {
            int maxVal = 0;
            for (PlanCuentas hijo : hijosDirectos) {
                String hijoCodigo = hijo.getCodigoContable();
                String suffixStr = hijoCodigo.substring(hijoCodigo.lastIndexOf('.') + 1);
                try {
                    int val = Integer.parseInt(suffixStr);
                    if (val > maxVal) {
                        maxVal = val;
                    }
                    formatLength = suffixStr.length(); // Usar el mismo formato que los hijos existentes
                } catch (NumberFormatException e) {
                    // Ignorar si no es numérico
                }
            }
            nextValue = maxVal + 1;
        }

        String nextSuffix = String.format("%0" + formatLength + "d", nextValue);
        String nuevoCodigo = padreCodigo + "." + nextSuffix;

        // Crear la nueva cuenta
        PlanCuentas nuevaCuenta = new PlanCuentas();
        nuevaCuenta.setCodigoContable(nuevoCodigo);
        nuevaCuenta.setNombreCuenta(nombreCuenta);
        nuevaCuenta.setTipoCuenta(padre.getTipoCuenta());
        nuevaCuenta.setEsMovimiento(esMovimiento);
        nuevaCuenta.setEstado("ACTIVO");

        PlanCuentas guardada = planCuentasRepository.save(nuevaCuenta);

        // Si el padre era de movimiento, ahora pasa a ser agrupador (esMovimiento = false)
        if (padre.getEsMovimiento()) {
            padre.setEsMovimiento(false);
            planCuentasRepository.save(padre);
        }

        return guardada;
    }

    /**
     * Activa o desactiva una cuenta contable según la regla de negocio (validando transacciones en Libro Diario).
     */
    @Transactional(rollbackFor = Exception.class)
    public PlanCuentas cambiarEstadoCuenta(Integer cuentaId, String nuevoEstado) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        if (!"ACTIVO".equals(nuevoEstado) && !"INACTIVO".equals(nuevoEstado)) {
            throw new IllegalArgumentException("Error: Estado inválido. Debe ser ACTIVO o INACTIVO.");
        }

        PlanCuentas cuenta = planCuentasRepository.findById(cuentaId)
                .orElseThrow(() -> new IllegalArgumentException("Error: La cuenta contable no existe."));

        if (!cuenta.getEmpresaId().equals(tenantId)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado a esta cuenta contable.");
        }

        // Si se va a desactivar, verificar si tiene transacciones en el Libro Diario
        if ("INACTIVO".equals(nuevoEstado)) {
            boolean tieneTransacciones = detalleRepository.existsByPlanCuentasId(cuentaId);
            if (tieneTransacciones) {
                throw new IllegalStateException("Error Contable: No se puede desactivar esta cuenta porque ya registra movimientos en el Libro Diario.");
            }
        }

        cuenta.setEstado(nuevoEstado);
        return planCuentasRepository.save(cuenta);
    }

    private long countDots(String s) {
        if (s == null) return 0;
        return s.chars().filter(ch -> ch == '.').count();
    }

    // HELPER: Calcula los saldos acumulados de las cuentas contables (a nivel de movimiento e integrando de forma consolidada hacia las agrupadoras)
    private java.util.Map<String, BigDecimal> calcularSaldosCuentas(
            Integer tenantId,
            List<PlanCuentas> plan,
            java.time.LocalDateTime desde,
            java.time.LocalDateTime hasta,
            boolean acumuladoDesdeInicio) {

        List<Object[]> rawSums;
        if (acumuladoDesdeInicio) {
            rawSums = detalleRepository.sumGroupedByCuentaBefore(tenantId, hasta);
        } else {
            rawSums = detalleRepository.sumGroupedByCuenta(tenantId, desde, hasta);
        }

        java.util.Map<Integer, BigDecimal[]> rawBalancesMap = new java.util.HashMap<>();
        for (Object[] row : rawSums) {
            Integer cuentaId = (Integer) row[0];
            BigDecimal debe = toBigDecimal(row[1]);
            BigDecimal haber = toBigDecimal(row[2]);
            rawBalancesMap.put(cuentaId, new BigDecimal[]{debe, haber});
        }

        java.util.Map<String, BigDecimal> individualBalances = new java.util.HashMap<>();
        for (PlanCuentas acc : plan) {
            BigDecimal balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (acc.getEsMovimiento()) {
                BigDecimal[] dh = rawBalancesMap.get(acc.getId());
                if (dh != null) {
                    BigDecimal debe = dh[0];
                    BigDecimal haber = dh[1];
                    String tipo = acc.getTipoCuenta();
                    boolean esDeudora = "ACTIVO".equals(tipo) || "GASTO".equals(tipo);
                    if (esDeudora) {
                        balance = debe.subtract(haber);
                    } else {
                        balance = haber.subtract(debe);
                    }
                }
            }
            individualBalances.put(acc.getCodigoContable(), balance);
        }

        java.util.Map<String, BigDecimal> rolledUpBalances = new java.util.HashMap<>();
        for (PlanCuentas parent : plan) {
            BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            String parentCode = parent.getCodigoContable();
            
            for (PlanCuentas leaf : plan) {
                if (leaf.getEsMovimiento()) {
                    String leafCode = leaf.getCodigoContable();
                    if (leafCode.equals(parentCode) || leafCode.startsWith(parentCode + ".")) {
                        sum = sum.add(individualBalances.get(leafCode));
                    }
                }
            }
            rolledUpBalances.put(parentCode, sum.setScale(2, RoundingMode.HALF_UP));
        }

        return rolledUpBalances;
    }

    private boolean esCuentaVisible(PlanCuentas acc, List<PlanCuentas> plan, java.util.Map<String, BigDecimal> rolledUpBalances, BigDecimal virtualResult) {
        String prefix = acc.getCodigoContable();
        if (virtualResult != null && virtualResult.compareTo(BigDecimal.ZERO) != 0) {
            if ("3.99".equals(prefix) || "3.99".startsWith(prefix + ".")) {
                return true;
            }
        }
        for (PlanCuentas other : plan) {
            if (other.getEsMovimiento()) {
                String otherCode = other.getCodigoContable();
                if (otherCode.equals(prefix) || otherCode.startsWith(prefix + ".")) {
                    BigDecimal bal = rolledUpBalances.get(otherCode);
                    if (bal != null && bal.compareTo(BigDecimal.ZERO) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // GENERAR ESTADO DE RESULTADOS
    public java.util.Map<String, Object> obtenerEstadoResultados(String desdeStr, String hastaStr) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        java.time.LocalDateTime desde;
        java.time.LocalDateTime hasta;

        if (desdeStr == null || desdeStr.isEmpty()) {
            desde = java.time.LocalDate.now().minusMonths(1).atStartOfDay();
        } else {
            desde = java.time.LocalDate.parse(desdeStr).atStartOfDay();
        }

        if (hastaStr == null || hastaStr.isEmpty()) {
            hasta = java.time.LocalDate.now().atTime(23, 59, 59, 999999999);
        } else {
            hasta = java.time.LocalDate.parse(hastaStr).atTime(23, 59, 59, 999999999);
        }

        List<PlanCuentas> plan = planCuentasRepository.findByEmpresaIdOrderByCodigoContableAsc(tenantId);
        java.util.Map<String, BigDecimal> rolledUpBalances = calcularSaldosCuentas(tenantId, plan, desde, hasta, false);

        List<java.util.Map<String, Object>> ingresosList = new java.util.ArrayList<>();
        List<java.util.Map<String, Object>> gastosList = new java.util.ArrayList<>();
        BigDecimal totalIngresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalGastos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (PlanCuentas acc : plan) {
            if (esCuentaVisible(acc, plan, rolledUpBalances, null)) {
                BigDecimal balance = rolledUpBalances.get(acc.getCodigoContable());
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("codigoContable", acc.getCodigoContable());
                map.put("nombreCuenta", acc.getNombreCuenta());
                map.put("tipoCuenta", acc.getTipoCuenta());
                map.put("esMovimiento", acc.getEsMovimiento());
                map.put("balance", balance != null ? balance : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

                if ("INGRESO".equals(acc.getTipoCuenta())) {
                    ingresosList.add(map);
                    if (!acc.getCodigoContable().contains(".")) {
                        totalIngresos = totalIngresos.add(balance != null ? balance : BigDecimal.ZERO);
                    }
                } else if ("GASTO".equals(acc.getTipoCuenta())) {
                    gastosList.add(map);
                    if (!acc.getCodigoContable().contains(".")) {
                        totalGastos = totalGastos.add(balance != null ? balance : BigDecimal.ZERO);
                    }
                }
            }
        }

        BigDecimal resultadoNeto = totalIngresos.subtract(totalGastos);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("ingresos", ingresosList);
        response.put("gastos", gastosList);
        response.put("totalIngresos", totalIngresos);
        response.put("totalGastos", totalGastos);
        response.put("resultadoNeto", resultadoNeto);
        return response;
    }

    // GENERAR BALANCE GENERAL
    public java.util.Map<String, Object> obtenerBalanceGeneral(String corteStr) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }

        java.time.LocalDateTime corte;
        if (corteStr == null || corteStr.isEmpty()) {
            corte = java.time.LocalDate.now().atTime(23, 59, 59, 999999999);
        } else {
            corte = java.time.LocalDate.parse(corteStr).atTime(23, 59, 59, 999999999);
        }

        List<PlanCuentas> plan = planCuentasRepository.findByEmpresaIdOrderByCodigoContableAsc(tenantId);
        java.util.Map<String, BigDecimal> rolledUpBalances = calcularSaldosCuentas(tenantId, plan, null, corte, true);

        // Calcular el Excedente/Pérdida del Ejercicio hasta la fecha de corte
        BigDecimal totalIngresosCorte = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalGastosCorte = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (PlanCuentas acc : plan) {
            if (acc.getEsMovimiento()) {
                BigDecimal balance = rolledUpBalances.get(acc.getCodigoContable());
                if (balance != null) {
                    if ("INGRESO".equals(acc.getTipoCuenta())) {
                        totalIngresosCorte = totalIngresosCorte.add(balance);
                    } else if ("GASTO".equals(acc.getTipoCuenta())) {
                        totalGastosCorte = totalGastosCorte.add(balance);
                    }
                }
            }
        }
        BigDecimal resultadoEjercicio = totalIngresosCorte.subtract(totalGastosCorte);

        List<java.util.Map<String, Object>> activosList = new java.util.ArrayList<>();
        List<java.util.Map<String, Object>> pasivosList = new java.util.ArrayList<>();
        List<java.util.Map<String, Object>> patrimonioList = new java.util.ArrayList<>();

        BigDecimal totalActivos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPasivos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPatrimonioSinResultado = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (PlanCuentas acc : plan) {
            if (esCuentaVisible(acc, plan, rolledUpBalances, resultadoEjercicio)) {
                BigDecimal balance = rolledUpBalances.get(acc.getCodigoContable());
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("codigoContable", acc.getCodigoContable());
                map.put("nombreCuenta", acc.getNombreCuenta());
                map.put("tipoCuenta", acc.getTipoCuenta());
                map.put("esMovimiento", acc.getEsMovimiento());
                map.put("balance", balance != null ? balance : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

                if ("ACTIVO".equals(acc.getTipoCuenta())) {
                    activosList.add(map);
                    if (!acc.getCodigoContable().contains(".")) {
                        totalActivos = totalActivos.add(balance != null ? balance : BigDecimal.ZERO);
                    }
                } else if ("PASIVO".equals(acc.getTipoCuenta())) {
                    pasivosList.add(map);
                    if (!acc.getCodigoContable().contains(".")) {
                        totalPasivos = totalPasivos.add(balance != null ? balance : BigDecimal.ZERO);
                    }
                } else if ("PATRIMONIO".equals(acc.getTipoCuenta())) {
                    patrimonioList.add(map);
                    if (!acc.getCodigoContable().contains(".")) {
                        totalPatrimonioSinResultado = totalPatrimonioSinResultado.add(balance != null ? balance : BigDecimal.ZERO);
                    }
                }
            }
        }

        // Añadir el item virtual del "Resultado del Ejercicio" bajo el Patrimonio
        if (resultadoEjercicio.compareTo(BigDecimal.ZERO) != 0) {
            java.util.Map<String, Object> virtualRes = new java.util.HashMap<>();
            virtualRes.put("codigoContable", "3.99");
            virtualRes.put("nombreCuenta", "RESULTADO DEL EJERCICIO (UTILIDAD/PÉRDIDA)");
            virtualRes.put("tipoCuenta", "PATRIMONIO");
            virtualRes.put("esMovimiento", true);
            virtualRes.put("balance", resultadoEjercicio);
            patrimonioList.add(virtualRes);
        }

        BigDecimal totalPatrimonio = totalPatrimonioSinResultado.add(resultadoEjercicio);
        BigDecimal totalPasivoPatrimonio = totalPasivos.add(totalPatrimonio);

        boolean cuadrado = totalActivos.subtract(totalPasivoPatrimonio).abs().compareTo(new BigDecimal("0.05")) <= 0;

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("activos", activosList);
        response.put("pasivos", pasivosList);
        response.put("patrimonio", patrimonioList);
        response.put("totalActivos", totalActivos);
        response.put("totalPasivos", totalPasivos);
        response.put("totalPatrimonio", totalPatrimonio);
        response.put("resultadoEjercicio", resultadoEjercicio);
        response.put("totalPasivoPatrimonio", totalPasivoPatrimonio);
        response.put("cuadrado", cuadrado);
        return response;
    }

    /**
     * Verifica si un año fiscal ya ha sido cerrado para el inquilino especificado.
     */
    public boolean esAnioFiscalCerrado(int anio, Integer empresaId) {
        return cierreAnualRepository.existsByAnioFiscalAndEmpresaId(anio, empresaId);
    }

    /**
     * Obtiene el historial de cierres anuales realizados en el inquilino activo.
     */
    public List<java.util.Map<String, Object>> obtenerCierresHistoricos() {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        List<CierreAnual> cierres = cierreAnualRepository.findByEmpresaIdOrderByAnioFiscalDesc(tenantId);
        return cierres.stream().map(c -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", c.getId());
            map.put("anioFiscal", c.getAnioFiscal());
            map.put("totalIngresos", c.getTotalIngresos());
            map.put("totalGastos", c.getTotalGastos());
            map.put("totalProvisiones", c.getTotalProvisiones());
            map.put("excedenteNeto", c.getExcedenteNeto());
            map.put("fechaCierre", c.getFechaCierre().toString());
            map.put("usuarioAdminId", c.getUsuarioAdminId());

            String userName = usuarioAdminRepository.findById(c.getUsuarioAdminId())
                    .map(u -> u.getNombresCompletos())
                    .orElse("Desconocido");
            map.put("usuarioNombre", userName);
            return map;
        }).toList();
    }

    /**
     * Proceso de Cierre Fiscal Anual.
     * Liquidación de ingresos/gastos y traslado a la cuenta patrimonial indicada.
     */
    @Transactional(rollbackFor = Exception.class)
    public CierreAnual ejecutarCierreAnual(int anioFiscal, Integer cuentaPatrimonialId, String username) {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede ejecutar el cierre sin un X-Tenant-ID definido.");
        }

        // 1. Validar que no se haya cerrado antes
        if (cierreAnualRepository.existsByAnioFiscalAndEmpresaId(anioFiscal, tenantId)) {
            throw new IllegalStateException("Error Contable: El año fiscal " + anioFiscal + " ya se encuentra cerrado.");
        }

        // 2. Obtener usuario administrador
        UsuariosAdmin usuario = usuarioAdminRepository.findByUsernameAndEmpresaId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error de Seguridad: Usuario ejecutor no encontrado."));

        // 3. Obtener y validar cuenta patrimonial de destino
        PlanCuentas cuentaPatrimonial = planCuentasRepository.findById(cuentaPatrimonialId)
                .orElseThrow(() -> new IllegalArgumentException("Error Contable: La cuenta patrimonial de destino no existe."));

        if (!cuentaPatrimonial.getEmpresaId().equals(tenantId)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado a la cuenta patrimonial seleccionada.");
        }
        if (!"PATRIMONIO".equals(cuentaPatrimonial.getTipoCuenta())) {
            throw new IllegalArgumentException("Error Contable: La cuenta de destino debe pertenecer al grupo de PATRIMONIO.");
        }

        // 4. Obtener saldos del periodo
        java.time.LocalDateTime desde = java.time.LocalDate.of(anioFiscal, 1, 1).atStartOfDay();
        java.time.LocalDateTime hasta = java.time.LocalDate.of(anioFiscal, 12, 31).atTime(23, 59, 59, 999999999);

        List<Object[]> rawBalances = detalleRepository.sumGroupedByCuenta(tenantId, desde, hasta);

        BigDecimal totalIngresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalGastos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        java.util.List<AsientosDetalle> detallesAsiento = new java.util.ArrayList<>();

        // 5. Liquidar cada cuenta de ingresos y gastos
        for (Object[] row : rawBalances) {
            Integer cuentaId = (Integer) row[0];
            BigDecimal debeSum = toBigDecimal(row[1]);
            BigDecimal haberSum = toBigDecimal(row[2]);

            PlanCuentas cuenta = planCuentasRepository.findById(cuentaId)
                    .orElseThrow(() -> new IllegalStateException("Cuenta contable no encontrada: " + cuentaId));

            if ("INGRESO".equals(cuenta.getTipoCuenta())) {
                BigDecimal saldoNeto = haberSum.subtract(debeSum).setScale(2, RoundingMode.HALF_UP);
                if (saldoNeto.compareTo(BigDecimal.ZERO) != 0) {
                    totalIngresos = totalIngresos.add(saldoNeto);
                    AsientosDetalle det = new AsientosDetalle();
                    det.setPlanCuentas(cuenta);
                    if (saldoNeto.compareTo(BigDecimal.ZERO) > 0) {
                        det.setTipoAsiento("DEBITO");
                        det.setMonto(saldoNeto);
                    } else {
                        det.setTipoAsiento("CREDITO");
                        det.setMonto(saldoNeto.abs());
                    }
                    detallesAsiento.add(det);
                }
            } else if ("GASTO".equals(cuenta.getTipoCuenta())) {
                BigDecimal saldoNeto = debeSum.subtract(haberSum).setScale(2, RoundingMode.HALF_UP);
                if (saldoNeto.compareTo(BigDecimal.ZERO) != 0) {
                    totalGastos = totalGastos.add(saldoNeto);
                    AsientosDetalle det = new AsientosDetalle();
                    det.setPlanCuentas(cuenta);
                    if (saldoNeto.compareTo(BigDecimal.ZERO) > 0) {
                        det.setTipoAsiento("CREDITO");
                        det.setMonto(saldoNeto);
                    } else {
                        det.setTipoAsiento("DEBITO");
                        det.setMonto(saldoNeto.abs());
                    }
                    detallesAsiento.add(det);
                }
            }
        }

        // Si no hay movimientos de ingresos/gastos en el año, no hay nada que cerrar
        if (totalIngresos.compareTo(BigDecimal.ZERO) == 0 && totalGastos.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Error Contable: No existen saldos de ingresos o gastos para cerrar en el año " + anioFiscal + ".");
        }

        // 6. Registrar excedente o pérdida neta
        BigDecimal excedenteNeto = totalIngresos.subtract(totalGastos).setScale(2, RoundingMode.HALF_UP);

        if (excedenteNeto.compareTo(BigDecimal.ZERO) != 0) {
            AsientosDetalle detPatrimonio = new AsientosDetalle();
            detPatrimonio.setPlanCuentas(cuentaPatrimonial);
            if (excedenteNeto.compareTo(BigDecimal.ZERO) > 0) {
                detPatrimonio.setTipoAsiento("CREDITO");
                detPatrimonio.setMonto(excedenteNeto);
            } else {
                detPatrimonio.setTipoAsiento("DEBITO");
                detPatrimonio.setMonto(excedenteNeto.abs());
            }
            detallesAsiento.add(detPatrimonio);
        }

        // 7. Generar el macro-asiento contable de cierre
        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setGlosa("Cierre Fiscal " + anioFiscal);
        cabecera.setReferencia("Cierre Fiscal " + anioFiscal);
        cabecera.setNumeroAsiento("AS-CIERRE-" + anioFiscal);
        cabecera.setFechaAsiento(hasta);

        // Se usa el método registrarAsientoCuadrado. Como el CierreAnual aún no ha sido guardado, 
        // pasa la validación del Time-Lock sin problemas.
        registrarAsientoCuadrado(cabecera, detallesAsiento);

        // 8. Crear y guardar el registro histórico del cierre
        CierreAnual cierre = new CierreAnual();
        cierre.setAnioFiscal(anioFiscal);
        cierre.setTotalIngresos(totalIngresos);
        cierre.setTotalGastos(totalGastos);
        cierre.setTotalProvisiones(BigDecimal.ZERO);
        cierre.setExcedenteNeto(excedenteNeto);
        cierre.setFechaCierre(LocalDateTime.now());
        cierre.setUsuarioAdminId(usuario.getId());

        return cierreAnualRepository.save(cierre);
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        return BigDecimal.valueOf(((Number) val).doubleValue());
    }
}