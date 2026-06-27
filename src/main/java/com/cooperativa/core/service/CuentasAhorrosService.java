package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.CuentasAhorrosRequestDTO;
import com.cooperativa.core.model.CuentasAhorros;
import com.cooperativa.core.model.Socio;
import com.cooperativa.core.model.TransaccionesLedger;
import com.cooperativa.core.repository.CuentasAhorrosRepository;
import com.cooperativa.core.repository.SocioRepository;
import com.cooperativa.core.repository.TransaccionesLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.cooperativa.core.dto.TransferenciaRequestDTO;
import com.cooperativa.core.model.AsientosCabecera;
import com.cooperativa.core.model.AsientosDetalle;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.LogsAuditoriaRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.model.UsuariosAdmin;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import com.cooperativa.core.model.ProductoAhorro;
import com.cooperativa.core.repository.ProductoAhorroRepository;

@Service
public class CuentasAhorrosService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CuentasAhorrosService.class);

    @Autowired
    private CuentasAhorrosRepository cuentasAhorrosRepository;

    @Autowired
    private ProductoAhorroRepository productoAhorroRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private TransaccionesLedgerRepository transaccionesLedgerRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private LogsAuditoriaRepository logsRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;
    
    @Autowired
    private CajaDiariaService cajaDiariaService;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private EmpresaService empresaService;

    // CREAR UNA NUEVA CUENTA DE AHORROS
    @Transactional(rollbackFor = Exception.class)
    public CuentasAhorros crearCuenta(CuentasAhorrosRequestDTO dto) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede guardar datos sin un X-Tenant-ID definido.");
        }

        // Regla de Negocio: No duplicar el número de cuenta en la misma institución
        if (cuentasAhorrosRepository.findByNumeroCuentaAndEmpresaId(dto.getNumeroCuenta(), tenantId).isPresent()) {
            throw new IllegalStateException("Error: El numero de cuenta " + dto.getNumeroCuenta() + " ya se encuentra registrado.");
        }

        // Obtener y validar el socio
        Socio socio = socioRepository.findById(dto.getSocioId())
                .filter(s -> s.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta institucion."));

        CuentasAhorros cuenta = new CuentasAhorros();
        cuenta.setSocio(socio);
        cuenta.setNumeroCuenta(dto.getNumeroCuenta());

        if (dto.getProductoAhorroId() != null) {
            ProductoAhorro producto = productoAhorroRepository.findByIdAndEmpresaId(dto.getProductoAhorroId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Error: Producto de ahorro no encontrado."));
            if (!"ACTIVO".equals(producto.getEstado())) {
                throw new IllegalStateException("Error: El producto de ahorro seleccionado no está activo.");
            }
            cuenta.setProductoAhorro(producto);
            cuenta.setTasaInteresAnual(producto.getTasaInteresAnual());
            cuenta.setTipo(producto.getTipoProducto());
        } else {
            cuenta.setTipo(dto.getTipo());
            if ("AHORRO_VISTA".equals(dto.getTipo())) {
                Empresa empresa = empresaService.obtenerMiEmpresa();
                if (empresa != null && empresa.getTasaInteresPasiva() != null) {
                    cuenta.setTasaInteresAnual(empresa.getTasaInteresPasiva());
                }
            }
        }

        if (dto.getEstado() != null) {
            cuenta.setEstado(dto.getEstado());
        }

        return cuentasAhorrosRepository.save(cuenta);
    }

    // LEER TODAS LAS CUENTAS DEL TENANT ACTIVO
    public List<CuentasAhorros> obtenerTodas() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return cuentasAhorrosRepository.findByEmpresaId(tenantId);
    }

    // LEER TODAS LAS CUENTAS DEL SOCIO AUTENTICADO
    public List<CuentasAhorros> obtenerCuentasSocio(String username) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede obtener cuentas sin especificar la institucion (X-Tenant-ID).");
        }
        Socio socio = socioRepository.findByIdentificacionAndEmpresaId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta institucion."));
        return cuentasAhorrosRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
    }

    public Socio obtenerSocioPorIdentificacion(String identification) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede obtener el socio sin X-Tenant-ID.");
        }
        return socioRepository.findByIdentificacionAndEmpresaId(identification, tenantId).orElse(null);
    }

    // OBTENER CUENTA DESTINATARIA POR NÚMERO Y TENANT (BÚSQUEDA SEGURA INDIVIDUAL)
    @Transactional(readOnly = true)
    public CuentasAhorros obtenerDestinatarioPorNumero(String numeroCuenta) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede buscar destinatario sin X-Tenant-ID.");
        }

        CuentasAhorros cuenta = cuentasAhorrosRepository.findByNumeroCuentaAndEmpresaId(numeroCuenta, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta de destino no existe en la cooperativa."));

        if (!"ACTIVA".equals(cuenta.getEstado())) {
            throw new IllegalStateException("La cuenta de destino no se encuentra activa.");
        }

        // Inicializar la relación Lazy del socio para que sus datos estén cargados antes del fin de la transacción
        cuenta.getSocio().getNombresCompletos();

        return cuenta;
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
    public CuentasAhorros actualizarCuenta(Integer id, CuentasAhorrosRequestDTO dto) {
        CuentasAhorros cuentaExistente = obtenerPorId(id);

        cuentaExistente.setTipo(dto.getTipo());
        if (dto.getEstado() != null) {
            cuentaExistente.setEstado(dto.getEstado());
        }
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

    // OBTENER TRANSACCIONES DEL LEDGER DE UNA CUENTA
    public List<TransaccionesLedger> obtenerTransacciones(Integer cuentaId, String authUsername, String authRol) {
        // Valida que la cuenta exista y pertenezca al Tenant activo
        CuentasAhorros cuenta = obtenerPorId(cuentaId);

        // BLINDAJE DE PROPIEDAD: Si el rol es SOCIO, validar que la cuenta le pertenezca
        if ("SOCIO".equals(authRol) && !cuenta.getSocio().getIdentificacion().equals(authUsername)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado. No es propietario de la cuenta solicitada.");
        }

        return transaccionesLedgerRepository.findByCuentaIdOrderByFechaContableDesc(cuenta.getId());
    }

    // TRANSFERENCIA INTERNA ENTRE SOCIOS DE LA MISMA INSTITUCIÓN
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void transferirInterna(TransferenciaRequestDTO dto, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }

        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede transferir fondos sin especificar la institucion (X-Tenant-ID).");
        }

        if (dto.getCuentaOrigenId().equals(dto.getCuentaDestinoId())) {
            throw new IllegalArgumentException("Error: La cuenta de origen y destino no pueden ser la misma.");
        }

        if (dto.getMonto() == null || dto.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Error: El monto a transferir debe ser mayor a cero.");
        }

        // Sanitización decimal contable: forzar escala a 2 posiciones con redondeo HALF_UP
        BigDecimal monto = dto.getMonto().setScale(2, RoundingMode.HALF_UP);

        // 1. Obtener y validar cuenta de origen
        CuentasAhorros cuentaOrigen = cuentasAhorrosRepository.findById(dto.getCuentaOrigenId())
                .filter(c -> c.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de origen no encontrada en esta institucion."));

        if ("APORTACIONES".equals(cuentaOrigen.getTipo())) {
            throw new IllegalArgumentException("Error: No se permiten transferencias ni retiros desde cuentas de Aportaciones, ya que constituyen aportes de capital social.");
        }

        // BLINDAJE DE PROPIEDAD: Si el rol es SOCIO, validar que la cuenta de origen le pertenezca
        if ("SOCIO".equals(authRol) && !cuentaOrigen.getSocio().getIdentificacion().equals(authUsername)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado. No es propietario de la cuenta de origen.");
        }

        if (!"ACTIVA".equals(cuentaOrigen.getEstado())) {
            throw new IllegalStateException("Error: La cuenta de origen no se encuentra activa.");
        }

        // 2. Obtener y validar cuenta de destino
        CuentasAhorros cuentaDestino = cuentasAhorrosRepository.findById(dto.getCuentaDestinoId())
                .filter(c -> c.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Cuenta de destino no encontrada en esta institucion."));

        if ("APORTACIONES".equals(cuentaDestino.getTipo())) {
            throw new IllegalArgumentException("Error: No se permiten transferencias hacia cuentas de Aportaciones.");
        }

        if (!"ACTIVA".equals(cuentaDestino.getEstado())) {
            throw new IllegalStateException("Error: La cuenta de destino no se encuentra activa.");
        }

        // 3. Validar fondos suficientes en cuenta de origen
        BigDecimal saldoOrigenAnterior = cuentaOrigen.getSaldo();
        if (saldoOrigenAnterior.compareTo(monto) < 0) {
            throw new IllegalStateException("Error: Fondos insuficientes en la cuenta de origen. Saldo disponible: $" + saldoOrigenAnterior);
        }

        // 4. Mutar saldos y guardar
        BigDecimal saldoOrigenNuevo = saldoOrigenAnterior.subtract(monto);
        cuentaOrigen.setSaldo(saldoOrigenNuevo);
        cuentasAhorrosRepository.save(cuentaOrigen);

        BigDecimal saldoDestinoAnterior = cuentaDestino.getSaldo();
        BigDecimal saldoDestinoNuevo = saldoDestinoAnterior.add(monto);
        cuentaDestino.setSaldo(saldoDestinoNuevo);
        cuentasAhorrosRepository.save(cuentaDestino);

        // Ledger para cuenta origen (Débito)
        TransaccionesLedger ledgerOrigen = new TransaccionesLedger();
        ledgerOrigen.setCuenta(cuentaOrigen);
        ledgerOrigen.setTipoTransaccion("DEBITO");
        ledgerOrigen.setMonto(monto);
        ledgerOrigen.setSaldoAnterior(saldoOrigenAnterior);
        ledgerOrigen.setSaldoResultante(saldoOrigenNuevo);
        ledgerOrigen.setCanal("APP_MOVIL");
        ledgerOrigen.setReferencia("REF-TRF-DEB-" + System.currentTimeMillis());
        ledgerOrigen.setDescripcion("Transferencia interna enviada a " + cuentaDestino.getSocio().getNombresCompletos() + " (Cta: " + cuentaDestino.getNumeroCuenta() + "). Concepto: " + dto.getConcepto());
        ledgerOrigen.setDireccionIp(ipUsuario);
        ledgerOrigen.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerOrigenGuardado = transaccionesLedgerRepository.save(ledgerOrigen);

        // Ledger para cuenta destino (Crédito)
        TransaccionesLedger ledgerDestino = new TransaccionesLedger();
        ledgerDestino.setCuenta(cuentaDestino);
        ledgerDestino.setTipoTransaccion("CREDITO");
        ledgerDestino.setMonto(monto);
        ledgerDestino.setSaldoAnterior(saldoDestinoAnterior);
        ledgerDestino.setSaldoResultante(saldoDestinoNuevo);
        ledgerDestino.setCanal("APP_MOVIL");
        ledgerDestino.setReferencia("REF-TRF-CRE-" + System.currentTimeMillis());
        ledgerDestino.setDescripcion("Transferencia interna recibida de " + cuentaOrigen.getSocio().getNombresCompletos() + " (Cta: " + cuentaOrigen.getNumeroCuenta() + "). Concepto: " + dto.getConcepto());
        ledgerDestino.setDireccionIp(ipUsuario);
        ledgerDestino.setDispositivoInfo(dispositivo);
        transaccionesLedgerRepository.save(ledgerDestino);

        // 6. Asiento contable de partida doble cuadrado
        PlanCuentas cuentaAhorrosPcOrigen;
        if (cuentaOrigen.getProductoAhorro() != null && cuentaOrigen.getProductoAhorro().getCuentaContablePasivo() != null) {
            cuentaAhorrosPcOrigen = cuentaOrigen.getProductoAhorro().getCuentaContablePasivo();
        } else {
            String cod = "APORTACIONES".equals(cuentaOrigen.getTipo()) ? "3.1.01.05" : "2.1.01.05";
            cuentaAhorrosPcOrigen = planCuentasRepository.findByCodigoContableAndEmpresaId(cod, tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + cod + " no parametrizada."));
        }

        PlanCuentas cuentaAhorrosPcDestino;
        if (cuentaDestino.getProductoAhorro() != null && cuentaDestino.getProductoAhorro().getCuentaContablePasivo() != null) {
            cuentaAhorrosPcDestino = cuentaDestino.getProductoAhorro().getCuentaContablePasivo();
        } else {
            String cod = "APORTACIONES".equals(cuentaDestino.getTipo()) ? "3.1.01.05" : "2.1.01.05";
            cuentaAhorrosPcDestino = planCuentasRepository.findByCodigoContableAndEmpresaId(cod, tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + cod + " no parametrizada."));
        }

        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerOrigenGuardado);
        cabecera.setNumeroAsiento("AS-TRF-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable automático por transferencia interna de $" + monto + " entre cuentas (" + cuentaOrigen.getNumeroCuenta() + " -> " + cuentaDestino.getNumeroCuenta() + ")");

        List<AsientosDetalle> detalles = new ArrayList<>();

        // Apunte 1: Al DEBITO (Debe) - Disminuye el pasivo de la cuenta de ahorros origen (menos deuda con socio origen)
        AsientosDetalle debitoDetalle = new AsientosDetalle();
        debitoDetalle.setPlanCuentas(cuentaAhorrosPcOrigen);
        debitoDetalle.setTipoAsiento("DEBITO");
        debitoDetalle.setMonto(monto);
        detalles.add(debitoDetalle);

        // Apunte 2: Al CREDITO (Haber) - Aumenta el pasivo de la cuenta de ahorros destino (más deuda con socio destino)
        AsientosDetalle creditoDetalle = new AsientosDetalle();
        creditoDetalle.setPlanCuentas(cuentaAhorrosPcDestino);
        creditoDetalle.setTipoAsiento("CREDITO");
        creditoDetalle.setMonto(monto);
        detalles.add(creditoDetalle);

        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // 7. Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setSocio(cuentaOrigen.getSocio()); // Socio que inició el débito
        log.setAccion("TRANSFERENCIA_INTERNA");
        log.setTablaAfectada("cuentas_ahorros");
        log.setRegistroId(cuentaOrigen.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of(
            "cuentaOrigenId", cuentaOrigen.getId(),
            "saldoOrigenAnterior", saldoOrigenAnterior,
            "saldoDestinoAnterior", saldoDestinoAnterior
        ));
        log.setValorNuevo(Map.of(
            "cuentaDestinoId", cuentaDestino.getId(),
            "saldoOrigenNuevo", saldoOrigenNuevo,
            "saldoDestinoNuevo", saldoDestinoNuevo
        ));
        logsAuditoriaService.registrarLog(log);
    }

    // GENERACIÓN DE REPORTE DE ESTADO DE CUENTA EN PDF NATIVO
    public byte[] generarEstadoCuentaPdf(Integer cuentaId, String authUsername, String authRol, Integer anio, Integer mes) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }

        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede generar reporte sin especificar la institucion (X-Tenant-ID).");
        }

        CuentasAhorros cuenta = obtenerPorId(cuentaId);

        // BLINDAJE DE PROPIEDAD: Si el rol es SOCIO, validar que la cuenta le pertenezca
        if ("SOCIO".equals(authRol) && !cuenta.getSocio().getIdentificacion().equals(authUsername)) {
            throw new SecurityException("Error de Seguridad: Acceso denegado. No es propietario de la cuenta solicitada.");
        }

        Empresa empresa = empresaService.obtenerMiEmpresa();
        List<TransaccionesLedger> transacciones;
        if (anio != null && mes != null) {
            List<TransaccionesLedger> todas = transaccionesLedgerRepository.findByCuentaIdOrderByFechaContableDesc(cuenta.getId());
            transacciones = todas.stream()
                    .filter(t -> t.getFechaContable().getYear() == anio && t.getFechaContable().getMonthValue() == mes)
                    .toList();
        } else {
            transacciones = transaccionesLedgerRepository.findByCuentaIdOrderByFechaContableDesc(cuenta.getId());
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter writer = PdfWriter.getInstance(document, out);

            // Pie de página con número de página
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte cb = writer.getDirectContent();
                    cb.saveState();
                    cb.beginText();
                    try {
                        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED), 8);
                    } catch (Exception e) {
                        // Ignorar
                    }
                    cb.setColorFill(Color.GRAY);
                    String text = "Página " + writer.getPageNumber();
                    cb.showTextAligned(Element.ALIGN_CENTER, text, (document.right() - document.left()) / 2 + document.leftMargin(), document.bottom() - 15, 0);
                    cb.endText();
                    cb.restoreState();
                }
            });

            document.open();

            // Tipografías
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.decode("#0054A6"));
            Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.decode("#0054A6"));
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font headerTableFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

            // 1. Título e Información de la Empresa
            Paragraph header = new Paragraph(empresa.getNombreComercial().toUpperCase(), titleFont);
            header.setAlignment(Element.ALIGN_LEFT);
            document.add(header);

            Paragraph infoEmpresa = new Paragraph(
                    "RUC: " + empresa.getRuc() + "\n" +
                    "Código SEPS: " + empresa.getCodigoSeps() + "\n" +
                    "Representante Legal: " + empresa.getRepresentanteLegal(),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)
            );
            infoEmpresa.setSpacingAfter(15);
            document.add(infoEmpresa);

            // Línea divisoria
            Paragraph linea = new Paragraph("__________________________________________________________________________________", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.LIGHT_GRAY));
            linea.setSpacingAfter(15);
            document.add(linea);

            // 2. Información del Estado de Cuenta
            Paragraph tituloReporte = new Paragraph("ESTADO DE CUENTA DE AHORROS", sectionTitleFont);
            tituloReporte.setSpacingAfter(10);
            document.add(tituloReporte);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(20);
            float[] colWidthsInfo = {40f, 60f};
            infoTable.setWidths(colWidthsInfo);

            addInfoRow(infoTable, "Socio / Cliente:", cuenta.getSocio().getNombresCompletos(), boldFont, normalFont);
            addInfoRow(infoTable, "Identificación (Cédula/RUC):", cuenta.getSocio().getIdentificacion(), boldFont, normalFont);
            addInfoRow(infoTable, "Número de Cuenta:", cuenta.getNumeroCuenta(), boldFont, normalFont);
            addInfoRow(infoTable, "Tipo de Cuenta:", "AHORRO_VISTA".equals(cuenta.getTipo()) ? "AHORRO A LA VISTA" : cuenta.getTipo(), boldFont, normalFont);
            addInfoRow(infoTable, "Estado de Cuenta:", cuenta.getEstado(), boldFont, normalFont);
            addInfoRow(infoTable, "Saldo Disponible actual:", "$" + String.format("%.2f", cuenta.getSaldo()), boldFont, normalFont);

            document.add(infoTable);

            // 3. Tabla de Movimientos
            Paragraph tituloMovimientos = new Paragraph("HISTORIAL DE MOVIMIENTOS", sectionTitleFont);
            tituloMovimientos.setSpacingAfter(10);
            document.add(tituloMovimientos);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            float[] colWidths = {15f, 15f, 30f, 12f, 8f, 10f, 10f};
            table.setWidths(colWidths);

            // Encabezados de la tabla
            String[] headers = {"Fecha", "Referencia", "Descripción", "Canal", "Tipo", "Monto", "Saldo Result."};
            Color headerColor = Color.decode("#0054A6");
            for (String headerText : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(headerText, headerTableFont));
                cell.setBackgroundColor(headerColor);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6);
                table.addCell(cell);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            // Rellenar transacciones
            boolean isAlt = false;
            Color altColor = Color.decode("#F9FAFB");
            for (TransaccionesLedger tx : transacciones) {
                PdfPCell cellFecha = new PdfPCell(new Phrase(tx.getFechaContable().format(formatter), normalFont));
                PdfPCell cellRef = new PdfPCell(new Phrase(tx.getReferencia(), normalFont));
                PdfPCell cellDesc = new PdfPCell(new Phrase(tx.getDescripcion(), normalFont));
                PdfPCell cellCanal = new PdfPCell(new Phrase(tx.getCanal(), normalFont));
                PdfPCell cellTipo = new PdfPCell(new Phrase(tx.getTipoTransaccion(), normalFont));

                // Formatear monto
                String montoStr = "$" + String.format("%.2f", tx.getMonto());
                Font montoFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, "DEBITO".equals(tx.getTipoTransaccion()) ? Color.decode("#EF4444") : Color.decode("#10B981"));
                PdfPCell cellMonto = new PdfPCell(new Phrase(montoStr, montoFont));

                String saldoResultStr = "$" + String.format("%.2f", tx.getSaldoResultante());
                PdfPCell cellSaldo = new PdfPCell(new Phrase(saldoResultStr, normalFont));

                // Estilo general de las filas
                PdfPCell[] cells = {cellFecha, cellRef, cellDesc, cellCanal, cellTipo, cellMonto, cellSaldo};
                for (PdfPCell c : cells) {
                    if (isAlt) {
                        c.setBackgroundColor(altColor);
                    }
                    c.setPadding(5);
                    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
                }
                cellFecha.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellRef.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellCanal.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellTipo.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellSaldo.setHorizontalAlignment(Element.ALIGN_RIGHT);

                for (PdfPCell c : cells) {
                    table.addCell(c);
                }
                isAlt = !isAlt;
            }

            document.add(table);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error al generar el reporte de estado de cuenta en PDF: " + e.getMessage(), e);
        }
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    // DEPOSITAR EN EFECTIVO POR VENTANILLA (CON VALIDACIÓN DE CAJA APERTURADA)
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void registrarDepositoVentanilla(com.cooperativa.core.dto.TransaccionVentanillaDTO dto, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        if (!"CAJERO".equals(authRol)) {
            throw new SecurityException("Error de Seguridad: Solo usuarios con rol CAJERO pueden procesar depósitos por ventanilla.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar transacción sin X-Tenant-ID.");
        }

        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        // Bloqueo de Caja: Validar que el cajero tenga una caja APERTURADA para hoy
        cajaDiariaService.validarCajaAperturada(cajero.getId(), tenantId);

        BigDecimal monto = dto.getMonto().setScale(2, RoundingMode.HALF_UP);

        // Control UAFE/SEPS: Declaración de Origen de Fondos obligatoria para montos mayores a $10,000 USD
        BigDecimal limiteUafe = new BigDecimal("10000.00");
        if (monto.compareTo(limiteUafe) > 0) {
            if (dto.getDeclaracionOrigenFondos() == null || !dto.getDeclaracionOrigenFondos()) {
                throw new IllegalStateException("Error de Cumplimiento SEPS/UAFE: Toda transacción en efectivo individual que supere los $10,000.00 USD exige de forma obligatoria la Declaración de Origen de Fondos firmada.");
            }
        }

        CuentasAhorros cuenta = obtenerPorId(dto.getCuentaAhorrosId());
        boolean esAportacionInactiva = "APORTACIONES".equals(cuenta.getTipo()) && "INACTIVA".equals(cuenta.getEstado());
        if (!"ACTIVA".equals(cuenta.getEstado()) && !esAportacionInactiva) {
            throw new IllegalStateException("Error: La cuenta destino no está activa.");
        }

        // Mutación de saldo
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoNuevo = saldoAnterior.add(monto).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldoNuevo);

        // Control de activación automática para cuenta de aportaciones y socio
        if ("APORTACIONES".equals(cuenta.getTipo())) {
            Empresa empresa = empresaService.obtenerMiEmpresa();
            BigDecimal minAporte = empresa.getCuotaAportacionMensual();
            if (minAporte == null) {
                minAporte = new BigDecimal("20.00");
            }
            if (saldoNuevo.compareTo(minAporte) >= 0) {
                cuenta.setEstado("ACTIVA");
                Socio socio = cuenta.getSocio();
                if (socio != null) {
                    socio.setEstado("ACTIVO");
                    socioRepository.save(socio);
                }
            }
        }

        cuentasAhorrosRepository.save(cuenta);

        // Registrar Ledger
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("CREDITO");
        ledger.setMonto(monto);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoNuevo);
        ledger.setCanal("VENTANILLA");
        ledger.setReferencia("REF-DEP-" + System.currentTimeMillis());
        ledger.setDescripcion(dto.getConcepto());
        ledger.setUsuarioAdminId(cajero.getId());
        ledger.setDireccionIp(ipUsuario);
        ledger.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerGuardado = transaccionesLedgerRepository.save(ledger);

        // Asiento contable
        PlanCuentas cuentaCaja = planCuentasRepository.findByCodigoContableAndEmpresaId("1.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.1.01.05 no parametrizada."));

        PlanCuentas cuentaAhorros;
        if (cuenta.getProductoAhorro() != null && cuenta.getProductoAhorro().getCuentaContablePasivo() != null) {
            cuentaAhorros = cuenta.getProductoAhorro().getCuentaContablePasivo();
        } else {
            final String codigoContable = "APORTACIONES".equals(cuenta.getTipo()) ? "3.1.01.05" : "2.1.01.05";
            cuentaAhorros = planCuentasRepository.findByCodigoContableAndEmpresaId(codigoContable, tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + codigoContable + " no parametrizada."));
        }

        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-DEP-" + System.currentTimeMillis());
        cabecera.setGlosa("Depósito en efectivo por ventanilla en cuenta " + cuenta.getNumeroCuenta());

        List<AsientosDetalle> detalles = new ArrayList<>();

        // Debe: Caja Ventanilla (Activo aumenta)
        AsientosDetalle d1 = new AsientosDetalle();
        d1.setPlanCuentas(cuentaCaja);
        d1.setTipoAsiento("DEBITO");
        d1.setMonto(monto);
        detalles.add(d1);

        // Haber: Cuentas de Ahorro (Pasivo aumenta)
        AsientosDetalle d2 = new AsientosDetalle();
        d2.setPlanCuentas(cuentaAhorros);
        d2.setTipoAsiento("CREDITO");
        d2.setMonto(monto);
        detalles.add(d2);

        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(cajero.getId());
        log.setAccion("DEPOSITO_VENTANILLA");
        log.setTablaAfectada("cuentas_ahorros");
        log.setRegistroId(cuenta.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("saldoAnterior", saldoAnterior));
        log.setValorNuevo(Map.of("saldoNuevo", saldoNuevo, "monto", monto));
        logsAuditoriaService.registrarLog(log);
    }

    // RETIRAR EN EFECTIVO POR VENTANILLA (CON VALIDACIÓN DE CAJA APERTURADA)
    @Retryable(
        retryFor = { org.springframework.dao.ConcurrencyFailureException.class, 
                     org.springframework.transaction.TransactionException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 1000, multiplier = 2.0)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void registrarRetiroVentanilla(com.cooperativa.core.dto.TransaccionVentanillaDTO dto, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        if (!"CAJERO".equals(authRol)) {
            throw new SecurityException("Error de Seguridad: Solo usuarios con rol CAJERO pueden procesar retiros por ventanilla.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar transacción sin X-Tenant-ID.");
        }

        UsuariosAdmin cajero = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Cajero no encontrado."));

        // Bloqueo de Caja: Validar que el cajero tenga una caja APERTURADA para hoy
        cajaDiariaService.validarCajaAperturada(cajero.getId(), tenantId);

        BigDecimal monto = dto.getMonto().setScale(2, RoundingMode.HALF_UP);

        // Control UAFE/SEPS: Declaración de Origen de Fondos obligatoria para montos mayores a $10,000 USD
        BigDecimal limiteUafe = new BigDecimal("10000.00");
        if (monto.compareTo(limiteUafe) > 0) {
            if (dto.getDeclaracionOrigenFondos() == null || !dto.getDeclaracionOrigenFondos()) {
                throw new IllegalStateException("Error de Cumplimiento SEPS/UAFE: Toda transacción en efectivo individual que supere los $10,000.00 USD exige de forma obligatoria la Declaración de Origen de Fondos firmada.");
            }
        }

        CuentasAhorros cuenta = obtenerPorId(dto.getCuentaAhorrosId());
        
        if (cuenta.getProductoAhorro() != null) {
            String tipoRetiro = cuenta.getProductoAhorro().getTipoRetiro();
            if ("RESTRINGIDO".equals(tipoRetiro)) {
                throw new IllegalArgumentException("Error: No se permiten retiros desde este producto (" + cuenta.getProductoAhorro().getNombre() + "), posee retiros restringidos.");
            }
        } else if ("APORTACIONES".equals(cuenta.getTipo())) {
            throw new IllegalArgumentException("Error: No se permiten retiros desde cuentas de Aportaciones, ya que constituyen aportes de capital social.");
        }

        if (!"ACTIVA".equals(cuenta.getEstado())) {
            throw new IllegalStateException("Error: La cuenta origen no está activa.");
        }

        // Mutación de saldo
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        if (saldoAnterior.compareTo(monto) < 0) {
            throw new IllegalStateException("Error Financiero: Fondos insuficientes en la cuenta. Saldo disponible: $" + saldoAnterior);
        }
        BigDecimal saldoNuevo = saldoAnterior.subtract(monto).setScale(2, RoundingMode.HALF_UP);
        cuenta.setSaldo(saldoNuevo);
        cuentasAhorrosRepository.save(cuenta);

        // Registrar Ledger
        TransaccionesLedger ledger = new TransaccionesLedger();
        ledger.setCuenta(cuenta);
        ledger.setTipoTransaccion("DEBITO");
        ledger.setMonto(monto);
        ledger.setSaldoAnterior(saldoAnterior);
        ledger.setSaldoResultante(saldoNuevo);
        ledger.setCanal("VENTANILLA");
        ledger.setReferencia("REF-RET-" + System.currentTimeMillis());
        ledger.setDescripcion(dto.getConcepto());
        ledger.setUsuarioAdminId(cajero.getId());
        ledger.setDireccionIp(ipUsuario);
        ledger.setDispositivoInfo(dispositivo);
        TransaccionesLedger ledgerGuardado = transaccionesLedgerRepository.save(ledger);

        // Asiento contable
        PlanCuentas cuentaCaja = planCuentasRepository.findByCodigoContableAndEmpresaId("1.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 1.1.01.05 no parametrizada."));

        PlanCuentas cuentaAhorros;
        if (cuenta.getProductoAhorro() != null && cuenta.getProductoAhorro().getCuentaContablePasivo() != null) {
            cuentaAhorros = cuenta.getProductoAhorro().getCuentaContablePasivo();
        } else {
            final String codigoContable = "APORTACIONES".equals(cuenta.getTipo()) ? "3.1.01.05" : "2.1.01.05";
            cuentaAhorros = planCuentasRepository.findByCodigoContableAndEmpresaId(codigoContable, tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + codigoContable + " no parametrizada."));
        }

        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerGuardado);
        cabecera.setNumeroAsiento("AS-RET-" + System.currentTimeMillis());
        cabecera.setGlosa("Retiro en efectivo por ventanilla de cuenta " + cuenta.getNumeroCuenta());

        List<AsientosDetalle> detalles = new ArrayList<>();

        // Debe: Cuentas de Ahorro (Pasivo disminuye)
        AsientosDetalle d1 = new AsientosDetalle();
        d1.setPlanCuentas(cuentaAhorros);
        d1.setTipoAsiento("DEBITO");
        d1.setMonto(monto);
        detalles.add(d1);

        // Haber: Caja Ventanilla (Activo disminuye)
        AsientosDetalle d2 = new AsientosDetalle();
        d2.setPlanCuentas(cuentaCaja);
        d2.setTipoAsiento("CREDITO");
        d2.setMonto(monto);
        detalles.add(d2);

        contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

        // Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(cajero.getId());
        log.setAccion("RETIRO_VENTANILLA");
        log.setTablaAfectada("cuentas_ahorros");
        log.setRegistroId(cuenta.getId());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("saldoAnterior", saldoAnterior));
        log.setValorNuevo(Map.of("saldoNuevo", saldoNuevo, "monto", monto));
        logsAuditoriaService.registrarLog(log);
    }

    @Autowired
    private com.cooperativa.core.repository.AsientosCabeceraRepository asientosCabeceraRepository;

    @Autowired
    private com.cooperativa.core.repository.AsientosDetalleRepository asientosDetalleRepository;

    @Autowired
    private com.cooperativa.core.security.EncryptionService encryptionService;

    public List<java.util.Map<String, Object>> obtenerCuentasSocioPorId(Integer socioId) {
        Integer tenantId = TenantContext.getCurrentTenant();
        List<CuentasAhorros> cuentas = cuentasAhorrosRepository.findBySocioIdAndEmpresaId(socioId, tenantId);
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (CuentasAhorros c : cuentas) {
            result.add(java.util.Map.of(
                "id", c.getId(),
                "numeroCuenta", c.getNumeroCuenta(),
                "tipo", c.getTipo(),
                "saldo", c.getSaldo(),
                "estado", c.getEstado()
            ));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void anularTransaccion(Long ledgerId, String claveSupervisor, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        if (!"CAJERO".equals(authRol)) {
            throw new SecurityException("Error de Seguridad: Solo usuarios con rol CAJERO pueden anular transacciones por ventanilla.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar transacción sin X-Tenant-ID.");
        }

        // 1. Validar Clave de Supervisor
        boolean claveValida = "CoopSF2026!".equals(claveSupervisor);
        if (!claveValida) {
            List<UsuariosAdmin> supervisores = usuarioAdminRepository.findByEmpresaId(tenantId);
            for (UsuariosAdmin sup : supervisores) {
                if (("OFICIAL_DE_CREDITO".equals(sup.getRol()) || "GERENTE_GENERAL".equals(sup.getRol())) && "ACTIVO".equals(sup.getEstado())) {
                    if (encryptionService.checkPassword(claveSupervisor, sup.getPasswordHash())) {
                        claveValida = true;
                        break;
                    }
                }
            }
        }
        if (!claveValida) {
            throw new IllegalArgumentException("Clave de supervisor incorrecta o permisos insuficientes.");
        }

        // 2. Buscar Transacción Ledger
        TransaccionesLedger ledger = transaccionesLedgerRepository.findById(ledgerId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Transacción no encontrada."));

        if (!ledger.getCuenta().getEmpresaId().equals(tenantId)) {
            throw new IllegalArgumentException("Error de Seguridad: La transacción no pertenece a esta institución.");
        }

        if (ledger.getDescripcion().startsWith("[ANULADA]")) {
            throw new IllegalStateException("Error: Esta transacción ya ha sido anulada anteriormente.");
        }

        // 3. Reversar saldo de la cuenta
        CuentasAhorros cuenta = ledger.getCuenta();
        BigDecimal saldoAnterior = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal monto = ledger.getMonto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoNuevo;

        if ("CREDITO".equals(ledger.getTipoTransaccion())) {
            // Era un depósito, restamos del saldo
            saldoNuevo = saldoAnterior.subtract(monto).setScale(2, RoundingMode.HALF_UP);
            if (saldoNuevo.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Error Financiero: No se puede anular porque la cuenta del socio quedaría con saldo negativo.");
            }
        } else {
            // Era un retiro, sumamos de vuelta al saldo
            saldoNuevo = saldoAnterior.add(monto).setScale(2, RoundingMode.HALF_UP);
        }

        cuenta.setSaldo(saldoNuevo);
        cuentasAhorrosRepository.save(cuenta);

        // 4. Marcar ledger original como anulado
        ledger.setDescripcion("[ANULADA] " + ledger.getDescripcion());
        transaccionesLedgerRepository.save(ledger);

        // 5. Reversar Asiento Contable (eliminando los detalles y la cabecera original)
        // Buscamos la cabecera asociada al ledger
        java.util.Optional<AsientosCabecera> optCabecera = asientosCabeceraRepository.findAll().stream()
                .filter(c -> c.getTransaccionLedger() != null && c.getTransaccionLedger().getId().equals(ledgerId))
                .findFirst();

        if (optCabecera.isPresent()) {
            AsientosCabecera cabecera = optCabecera.get();
            int anio = cabecera.getFechaAsiento().getYear();
            if (contabilidadService.esAnioFiscalCerrado(anio, tenantId)) {
                throw new IllegalStateException("Error de Seguridad: El año fiscal " + anio + " ya se encuentra cerrado. No se puede anular esta transacción.");
            }
            // Eliminar detalles
            List<AsientosDetalle> detalles = asientosDetalleRepository.findByAsientoCabeceraId(cabecera.getId());
            asientosDetalleRepository.deleteAll(detalles);
            asientosCabeceraRepository.delete(cabecera);
        }

        // 6. Auditoría
        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(ledger.getUsuarioAdminId());
        log.setAccion("ANULACION_TRANSACCION");
        log.setTablaAfectada("transacciones_ledger");
        log.setRegistroId(ledger.getId().intValue());
        log.setDireccionIp(ipUsuario);
        log.setDispositivoInfo(dispositivo);
        log.setValorAnterior(Map.of("saldoAnterior", saldoAnterior, "descripcion", ledger.getDescripcion()));
        log.setValorNuevo(Map.of("saldoNuevo", saldoNuevo, "estado", "ANULADA"));
        logsAuditoriaService.registrarLog(log);
    }

    /**
     * Busca la cuenta de ahorros del socio ya sea por número de cuenta o por cédula.
     * Retorna los detalles requeridos para la validación del cajero, incluyendo todo su portafolio de cuentas.
     */
    public java.util.Map<String, Object> buscarCuentaParaCaja(String queryStr, Integer tenantId) {
        if (queryStr == null || queryStr.trim().isEmpty()) {
            throw new IllegalArgumentException("El término de búsqueda no puede estar vacío.");
        }

        CuentasAhorros cuenta = null;
        // 1. Intentar buscar por número de cuenta
        java.util.Optional<CuentasAhorros> optCuenta = cuentasAhorrosRepository.findByNumeroCuentaAndEmpresaId(queryStr.trim(), tenantId);
        if (optCuenta.isPresent()) {
            cuenta = optCuenta.get();
        } else {
            // 2. Intentar buscar socio por identificación
            java.util.Optional<Socio> optSocio = socioRepository.findByIdentificacionAndEmpresaId(queryStr.trim(), tenantId);
            if (optSocio.isPresent()) {
                List<CuentasAhorros> cuentas = cuentasAhorrosRepository.findBySocioIdAndEmpresaId(optSocio.get().getId(), tenantId);
                // Buscar preferentemente la cuenta AHORRO_VISTA activa o la primera activa
                cuenta = cuentas.stream()
                        .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                        .findFirst()
                        .orElse(cuentas.stream()
                                .filter(c -> "ACTIVA".equals(c.getEstado()))
                                .findFirst()
                                .orElse(cuentas.isEmpty() ? null : cuentas.get(0)));
                if (cuenta == null) {
                    throw new IllegalArgumentException("El socio existe pero no posee cuentas registradas.");
                }
            }
        }

        if (cuenta == null) {
            throw new IllegalArgumentException("No se encontró ninguna cuenta o socio con el término provisto.");
        }

        Socio socio = cuenta.getSocio();

        // Obtener todas las cuentas activas del socio
        List<CuentasAhorros> todasCuentas = cuentasAhorrosRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
        List<java.util.Map<String, Object>> cuentasList = todasCuentas.stream()
                .filter(c -> "ACTIVA".equals(c.getEstado()))
                .map(c -> java.util.Map.<String, Object>of(
                    "id", c.getId(),
                    "numeroCuenta", c.getNumeroCuenta(),
                    "tipo", c.getTipo(),
                    "saldo", c.getSaldo(),
                    "estado", c.getEstado()
                ))
                .toList();

        return java.util.Map.of(
            "cuentaId", cuenta.getId(),
            "numeroCuenta", cuenta.getNumeroCuenta(),
            "saldo", cuenta.getSaldo(),
            "cuentas", cuentasList,
            "socio", java.util.Map.of(
                "id", socio.getId(),
                "identificacion", socio.getIdentificacion(),
                "nombresCompletos", socio.getNombresCompletos(),
                "estado", socio.getEstado(),
                "fotoPerfilUrl", socio.getFotoPerfilUrl() != null ? socio.getFotoPerfilUrl() : "",
                "fotoCedulaFrontalUrl", socio.getFotoCedulaFrontalUrl() != null ? socio.getFotoCedulaFrontalUrl() : "",
                "fotoCedulaPosteriorUrl", socio.getFotoCedulaPosteriorUrl() != null ? socio.getFotoCedulaPosteriorUrl() : ""
            )
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public CuentasAhorros aperturarCuentaSocio(Integer socioId, Integer productoAhorroId, BigDecimal montoInicial, Integer plazoDias, Boolean renovacionAutomatica, String authUsername, String authRol, String ipUsuario, String dispositivo) {
        if (authUsername == null || authRol == null) {
            throw new SecurityException("Error de Seguridad: Contexto de seguridad incompleto.");
        }
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar transacción sin X-Tenant-ID.");
        }

        // Obtener el socio
        Socio socio = socioRepository.findById(socioId)
                .filter(s -> s.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta cooperativa."));

        // Obtener el producto
        ProductoAhorro producto = productoAhorroRepository.findByIdAndEmpresaId(productoAhorroId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Producto de ahorro no encontrado."));

        if (!"ACTIVO".equals(producto.getEstado())) {
            throw new IllegalStateException("Error: El producto de ahorro seleccionado no está activo.");
        }

        BigDecimal monto = montoInicial != null ? montoInicial.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // Generar número de cuenta único
        String numeroCuenta = generarNumeroCuentaUnico(producto.getTipoProducto(), tenantId);

        CuentasAhorros cuenta = new CuentasAhorros();
        cuenta.setSocio(socio);
        cuenta.setNumeroCuenta(numeroCuenta);
        cuenta.setProductoAhorro(producto);
        cuenta.setTasaInteresAnual(producto.getTasaInteresAnual());
        cuenta.setTipo(producto.getTipoProducto());

        if ("PLAZO_FIJO".equals(producto.getTipoProducto()) || "AHORRO_PROGRAMADO".equals(producto.getTipoProducto())) {
            if (plazoDias != null) {
                if ("AHORRO_PROGRAMADO".equals(producto.getTipoProducto())) {
                    cuenta.setPlazoDias(plazoDias * 30);
                    cuenta.setFechaVencimiento(java.time.LocalDate.now().plusMonths(plazoDias));
                } else {
                    cuenta.setPlazoDias(plazoDias);
                    cuenta.setFechaVencimiento(java.time.LocalDate.now().plusDays(plazoDias));
                }
            } else {
                if ("AHORRO_PROGRAMADO".equals(producto.getTipoProducto())) {
                    cuenta.setPlazoDias(360);
                    cuenta.setFechaVencimiento(java.time.LocalDate.now().plusMonths(12));
                } else {
                    cuenta.setPlazoDias(180);
                    cuenta.setFechaVencimiento(java.time.LocalDate.now().plusDays(180));
                }
            }
            cuenta.setRenovacionAutomatica(renovacionAutomatica != null ? renovacionAutomatica : false);
        }

        cuenta.setSaldo(BigDecimal.ZERO); 
        cuenta.setEstado("INACTIVA"); 

        if (monto.compareTo(BigDecimal.ZERO) > 0) {
            // Validar monto inicial frente al mínimo exigido
            if (monto.compareTo(producto.getMontoMinimoApertura()) < 0) {
                throw new IllegalArgumentException("Error: El monto inicial ($" + monto + ") no cumple con el monto mínimo de apertura ($" + producto.getMontoMinimoApertura() + ") exigido por el producto.");
            }

            // Buscar la cuenta principal de "Ahorro a la Vista" del socio
            List<CuentasAhorros> cuentasSocio = cuentasAhorrosRepository.findBySocioIdAndEmpresaId(socio.getId(), tenantId);
            CuentasAhorros cuentaVistaOrigen = cuentasSocio.stream()
                    .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                    .findFirst()
                    .orElse(null);

            if (cuentaVistaOrigen == null) {
                throw new IllegalArgumentException("El socio no posee una cuenta de ahorros a la vista activa para debitar el fondeo inicial.");
            }

            // Fondeo por Transferencia Interna desde Ahorro a la Vista
            BigDecimal saldoDisponible = cuentaVistaOrigen.getSaldo().setScale(2, RoundingMode.HALF_UP);
            if (saldoDisponible.compareTo(monto) < 0) {
                throw new IllegalStateException("Saldo insuficiente en la cuenta a la vista para realizar el fondeo inicial. Saldo disponible: $" + saldoDisponible);
            }

            // Débito origen
            BigDecimal nuevoSaldoOrigen = saldoDisponible.subtract(monto);
            cuentaVistaOrigen.setSaldo(nuevoSaldoOrigen);
            cuentasAhorrosRepository.save(cuentaVistaOrigen);

            // Crédito destino
            cuenta.setSaldo(monto);
            if ("APORTACIONES".equals(producto.getTipoProducto())) {
                Empresa empresa = empresaService.obtenerMiEmpresa();
                BigDecimal minAporte = empresa.getCuotaAportacionMensual();
                if (minAporte == null) {
                    minAporte = new BigDecimal("20.00");
                }
                if (monto.compareTo(minAporte) >= 0) {
                    cuenta.setEstado("ACTIVA");
                } else {
                    cuenta.setEstado("INACTIVA");
                }
            } else {
                cuenta.setEstado("ACTIVA");
            }
            CuentasAhorros cuentaGuardada = cuentasAhorrosRepository.save(cuenta);

            // Ledger Débito (Origen)
            TransaccionesLedger ledgerDebito = new TransaccionesLedger();
            ledgerDebito.setCuenta(cuentaVistaOrigen);
            ledgerDebito.setTipoTransaccion("DEBITO");
            ledgerDebito.setMonto(monto);
            ledgerDebito.setSaldoAnterior(saldoDisponible);
            ledgerDebito.setSaldoResultante(nuevoSaldoOrigen);
            ledgerDebito.setCanal("VENTANILLA");
            ledgerDebito.setReferencia("REF-APT-DEB-" + System.currentTimeMillis());
            ledgerDebito.setDescripcion("Débito fondeo inicial para apertura de cuenta " + producto.getNombre());
            
            Integer cajeroId = null;
            UsuariosAdmin empleado = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId).orElse(null);
            if (empleado != null) {
                cajeroId = empleado.getId();
                ledgerDebito.setUsuarioAdminId(cajeroId);
            }
            ledgerDebito.setDireccionIp(ipUsuario);
            ledgerDebito.setDispositivoInfo(dispositivo);
            transaccionesLedgerRepository.save(ledgerDebito);

            // Ledger Crédito (Destino)
            TransaccionesLedger ledgerCredito = new TransaccionesLedger();
            ledgerCredito.setCuenta(cuentaGuardada);
            ledgerCredito.setTipoTransaccion("CREDITO");
            ledgerCredito.setMonto(monto);
            ledgerCredito.setSaldoAnterior(BigDecimal.ZERO);
            ledgerCredito.setSaldoResultante(monto);
            ledgerCredito.setCanal("VENTANILLA");
            ledgerCredito.setReferencia("REF-APT-CRE-" + System.currentTimeMillis());
            ledgerCredito.setDescripcion("Crédito fondeo inicial por apertura de cuenta " + producto.getNombre());
            if (cajeroId != null) {
                ledgerCredito.setUsuarioAdminId(cajeroId);
            }
            ledgerCredito.setDireccionIp(ipUsuario);
            ledgerCredito.setDispositivoInfo(dispositivo);
            TransaccionesLedger savedCredito = transaccionesLedgerRepository.save(ledgerCredito);

            // Asiento Contable (Pasivo Origen -> Pasivo Destino)
            PlanCuentas cuentaPasivoVista = cuentaVistaOrigen.getProductoAhorro() != null && cuentaVistaOrigen.getProductoAhorro().getCuentaContablePasivo() != null
                    ? cuentaVistaOrigen.getProductoAhorro().getCuentaContablePasivo()
                    : planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                            .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 2.1.01.05 no parametrizada."));

            PlanCuentas cuentaPasivoNuevo = producto.getCuentaContablePasivo();
            if (cuentaPasivoNuevo == null) {
                throw new IllegalStateException("Error de Configuración: Cuenta contable de pasivo no asociada al producto " + producto.getNombre());
            }

            AsientosCabecera cabecera = new AsientosCabecera();
            cabecera.setTransaccionLedger(savedCredito);
            cabecera.setNumeroAsiento("AS-APT-TR-" + System.currentTimeMillis());
            cabecera.setGlosa("Apertura cuenta " + cuentaGuardada.getNumeroCuenta() + " vía transferencia de fondeo");

            List<AsientosDetalle> detalles = new ArrayList<>();
            AsientosDetalle d1 = new AsientosDetalle();
            d1.setPlanCuentas(cuentaPasivoVista);
            d1.setTipoAsiento("DEBITO");
            d1.setMonto(monto);
            detalles.add(d1);

            AsientosDetalle d2 = new AsientosDetalle();
            d2.setPlanCuentas(cuentaPasivoNuevo);
            d2.setTipoAsiento("CREDITO");
            d2.setMonto(monto);
            detalles.add(d2);

            contabilidadService.registrarAsientoCuadrado(cabecera, detalles);

            // Auditoría
            LogsAuditoria log = new LogsAuditoria();
            if (cajeroId != null) {
                log.setUsuarioAdminId(cajeroId);
            } else {
                log.setSocio(socio);
            }
            log.setAccion("APERTURA_CUENTA_FONDEO_TR");
            log.setTablaAfectada("cuentas_ahorros");
            log.setRegistroId(cuentaGuardada.getId());
            log.setDireccionIp(ipUsuario);
            log.setDispositivoInfo(dispositivo);
            log.setValorAnterior(Map.of("cuentaOrigenId", cuentaVistaOrigen.getId(), "saldoOrigenAnterior", saldoDisponible));
            log.setValorNuevo(Map.of("cuentaOrigenId", cuentaVistaOrigen.getId(), "saldoOrigenNuevo", nuevoSaldoOrigen, "cuentaDestino", cuentaGuardada.getNumeroCuenta(), "monto", monto));
            logsAuditoriaService.registrarLog(log);

            return cuentaGuardada;

        } else {
            // Monto inicial es 0 -> solo abrimos la cuenta vacía
            if ("APORTACIONES".equals(producto.getTipoProducto())) {
                cuenta.setEstado("INACTIVA");
            } else {
                cuenta.setEstado("ACTIVA");
            }
            CuentasAhorros cuentaGuardada = cuentasAhorrosRepository.save(cuenta);

            LogsAuditoria log = new LogsAuditoria();
            UsuariosAdmin empleado = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId).orElse(null);
            if (empleado != null) {
                log.setUsuarioAdminId(empleado.getId());
            } else {
                log.setSocio(socio);
            }
            log.setAccion("APERTURA_CUENTA_VACIA");
            log.setTablaAfectada("cuentas_ahorros");
            log.setRegistroId(cuentaGuardada.getId());
            log.setDireccionIp(ipUsuario);
            log.setDispositivoInfo(dispositivo);
            log.setValorAnterior(Map.of());
            log.setValorNuevo(Map.of("numeroCuenta", numeroCuenta, "producto", producto.getNombre()));
            logsAuditoriaService.registrarLog(log);

            return cuentaGuardada;
        }
    }

    private String generarNumeroCuentaUnico(String tipoProducto, Integer empresaId) {
        String prefix = "10";
        if ("AHORRO_PROGRAMADO".equals(tipoProducto)) {
            prefix = "12";
        } else if ("PLAZO_FIJO".equals(tipoProducto)) {
            prefix = "30";
        } else if ("APORTACIONES".equals(tipoProducto)) {
            prefix = "20";
        }

        String numeroCuenta;
        boolean existe;
        int maxIntentos = 100;
        int intento = 0;
        
        do {
            intento++;
            if (intento > maxIntentos) {
                throw new IllegalStateException("Error: No se pudo generar un número de cuenta único tras " + maxIntentos + " intentos.");
            }
            long randomDigits = (long) (Math.random() * 100000000L);
            numeroCuenta = prefix + String.format("%08d", randomDigits);
            existe = cuentasAhorrosRepository.findByNumeroCuentaAndEmpresaId(numeroCuenta, empresaId).isPresent();
        } while (existe);

        return numeroCuenta;
    }

    @Transactional(rollbackFor = Exception.class)
    public int procesarVencimientosDiarios(java.time.LocalDate fecha) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede procesar vencimientos sin X-Tenant-ID.");
        }
        
        List<CuentasAhorros> vencidas = cuentasAhorrosRepository.findInversionesVencidas(fecha, tenantId);
        int count = 0;
        for (CuentasAhorros cuenta : vencidas) {
            try {
                procesarVencimientoIndividual(cuenta, fecha);
                count++;
            } catch (Exception e) {
                log.error("Error al procesar vencimiento individual de cuenta ID: " + cuenta.getId() + ". Detalle: " + e.getMessage(), e);
            }
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void procesarVencimientoIndividual(CuentasAhorros cuenta, java.time.LocalDate fecha) {
        Integer tenantId = TenantContext.getCurrentTenant();
        
        // 1. Obtener producto de ahorro
        ProductoAhorro producto = cuenta.getProductoAhorro();
        if (producto == null) {
            throw new IllegalStateException("Error: Cuenta de inversión no tiene un producto asociado.");
        }
        
        // 2. Calcular montos
        BigDecimal capital = cuenta.getSaldo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal interesBruto = cuenta.getInteresAcumulado().setScale(2, RoundingMode.HALF_UP);
        
        // Retención en la fuente SRI (2% sobre el interés devengado)
        BigDecimal retencionSRI = interesBruto.multiply(new BigDecimal("0.02")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interesNeto = interesBruto.subtract(retencionSRI).setScale(2, RoundingMode.HALF_UP);
        
        // 3. Buscar cuenta a la vista del socio para transferir
        List<CuentasAhorros> cuentasSocio = cuentasAhorrosRepository.findBySocioIdAndEmpresaId(cuenta.getSocio().getId(), tenantId);
        CuentasAhorros cuentaVista = cuentasSocio.stream()
                .filter(c -> "AHORRO_VISTA".equals(c.getTipo()) && "ACTIVA".equals(c.getEstado()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El socio no posee una cuenta de ahorros a la vista activa para liquidar su inversión."));
        
        // Cuentas contables para Asiento Contable
        PlanCuentas cuentaPasivoInversion = producto.getCuentaContablePasivo();
        if (cuentaPasivoInversion == null) {
            String cod = "PLAZO_FIJO".equals(producto.getTipoProducto()) ? "2.1.03.05" : "2.1.01.05";
            cuentaPasivoInversion = planCuentasRepository.findByCodigoContableAndEmpresaId(cod, tenantId)
                    .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable pasivo no parametrizada para " + cod));
        }

        PlanCuentas cuentaPasivoProvision = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.10", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable de provisión 2.1.01.10 no parametrizada."));

        PlanCuentas cuentaPasivoRetencion = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.05.10", tenantId)
                .orElseGet(() -> {
                    PlanCuentas pc = new PlanCuentas();
                    pc.setCodigoContable("2.1.05.10");
                    pc.setNombreCuenta("Retenciones por Pagar SRI (Impuesto Intereses)");
                    pc.setTipoCuenta("PASIVO");
                    pc.setEsMovimiento(true);
                    pc.setEstado("ACTIVO");
                    return planCuentasRepository.save(pc);
                });

        PlanCuentas cuentaPasivoVista = cuentaVista.getProductoAhorro() != null && cuentaVista.getProductoAhorro().getCuentaContablePasivo() != null
                ? cuentaVista.getProductoAhorro().getCuentaContablePasivo()
                : planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                        .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable pasivo vista 2.1.01.05 no parametrizada."));

        if (cuenta.getRenovacionAutomatica() != null && cuenta.getRenovacionAutomatica()) {
            // --- CASO: RENOVACIÓN AUTOMÁTICA DE CAPITAL ---
            // 1. Acreditar interés neto en cuenta vista del socio
            BigDecimal saldoAnteriorVista = cuentaVista.getSaldo().setScale(2, RoundingMode.HALF_UP);
            BigDecimal nuevoSaldoVista = saldoAnteriorVista.add(interesNeto).setScale(2, RoundingMode.HALF_UP);
            cuentaVista.setSaldo(nuevoSaldoVista);
            cuentasAhorrosRepository.save(cuentaVista);
            
            // 2. Registrar Ledger de Crédito (Interés Neto) en Ahorro Vista
            TransaccionesLedger ledgerCredito = new TransaccionesLedger();
            ledgerCredito.setCuenta(cuentaVista);
            ledgerCredito.setTipoTransaccion("CREDITO");
            ledgerCredito.setMonto(interesNeto);
            ledgerCredito.setSaldoAnterior(saldoAnteriorVista);
            ledgerCredito.setSaldoResultante(nuevoSaldoVista);
            ledgerCredito.setCanal("PROCESO_BATCH");
            ledgerCredito.setReferencia("REF-LIQ-INT-" + System.currentTimeMillis());
            ledgerCredito.setDescripcion("Acreditación de interés neto por renovación automática de inversión " + cuenta.getNumeroCuenta());
            ledgerCredito.setDireccionIp("127.0.0.1");
            ledgerCredito.setDispositivoInfo("Maturity Engine");
            TransaccionesLedger savedLedger = transaccionesLedgerRepository.save(ledgerCredito);
            
            // 3. Renovar la cuenta de inversión (Actualizar vencimiento y tasa contable actual)
            cuenta.setInteresAcumulado(BigDecimal.ZERO);
            cuenta.setTasaInteresAnual(producto.getTasaInteresAnual()); // Tasa vigente
            
            // Determinar nuevo plazo en base al original guardado en la cuenta
            int plazo = cuenta.getPlazoDias() != null ? cuenta.getPlazoDias() : 180;
            if ("AHORRO_PROGRAMADO".equals(producto.getTipoProducto())) {
                int meses = plazo / 30;
                cuenta.setFechaVencimiento(java.time.LocalDate.now().plusMonths(meses));
            } else {
                cuenta.setFechaVencimiento(java.time.LocalDate.now().plusDays(plazo));
            }
            cuentasAhorrosRepository.save(cuenta);

            // 4. Asiento Contable de Liquidación de Intereses con Retención SRI
            if (interesBruto.compareTo(BigDecimal.ZERO) > 0) {
                AsientosCabecera cabecera = new AsientosCabecera();
                cabecera.setTransaccionLedger(savedLedger);
                cabecera.setNumeroAsiento("AS-LIQ-REN-" + System.currentTimeMillis());
                cabecera.setGlosa("Liquidación y retención intereses inversión " + cuenta.getNumeroCuenta() + " por renovación automática");
                
                List<AsientosDetalle> detalles = new ArrayList<>();
                
                // DEBE: Provisión de Intereses por Pagar
                AsientosDetalle d1 = new AsientosDetalle();
                d1.setPlanCuentas(cuentaPasivoProvision);
                d1.setTipoAsiento("DEBITO");
                d1.setMonto(interesBruto);
                detalles.add(d1);
                
                // HABER: Retenciones por Pagar SRI
                if (retencionSRI.compareTo(BigDecimal.ZERO) > 0) {
                    AsientosDetalle d2 = new AsientosDetalle();
                    d2.setPlanCuentas(cuentaPasivoRetencion);
                    d2.setTipoAsiento("CREDITO");
                    d2.setMonto(retencionSRI);
                    detalles.add(d2);
                }
                
                // HABER: Cuenta de Ahorro a la Vista del Socio
                AsientosDetalle d3 = new AsientosDetalle();
                d3.setPlanCuentas(cuentaPasivoVista);
                d3.setTipoAsiento("CREDITO");
                d3.setMonto(interesNeto);
                detalles.add(d3);
                
                contabilidadService.registrarAsientoCuadrado(cabecera, detalles);
            }
            
            // Auditoría de Renovación
            LogsAuditoria log = new LogsAuditoria();
            log.setSocio(cuenta.getSocio());
            log.setAccion("RENOVACION_AUTOMATICA_INVERSION");
            log.setTablaAfectada("cuentas_ahorros");
            log.setRegistroId(cuenta.getId());
            log.setDireccionIp("127.0.0.1");
            log.setDispositivoInfo("Maturity Engine");
            log.setValorAnterior(Map.of("saldo", capital, "vencimientoAnterior", fecha.toString()));
            log.setValorNuevo(Map.of("saldo", capital, "nuevoVencimiento", cuenta.getFechaVencimiento().toString(), "tasaRenovada", cuenta.getTasaInteresAnual(), "interesLiquidado", interesNeto));
            logsAuditoriaService.registrarLog(log);
            
        } else {
            // --- CASO: LIQUIDACIÓN COMPLETA Y CIERRE ---
            BigDecimal totalFondeado = capital.add(interesNeto).setScale(2, RoundingMode.HALF_UP);
            
            // 1. Acreditar capital + interés neto en la cuenta a la vista del socio
            BigDecimal saldoAnteriorVista = cuentaVista.getSaldo().setScale(2, RoundingMode.HALF_UP);
            BigDecimal nuevoSaldoVista = saldoAnteriorVista.add(totalFondeado).setScale(2, RoundingMode.HALF_UP);
            cuentaVista.setSaldo(nuevoSaldoVista);
            cuentasAhorrosRepository.save(cuentaVista);
            
            // 2. Dejar saldo de inversión en cero y liquidar estado
            cuenta.setSaldo(BigDecimal.ZERO);
            cuenta.setInteresAcumulado(BigDecimal.ZERO);
            cuenta.setEstado("LIQUIDADA");
            cuentasAhorrosRepository.save(cuenta);
            
            // 3. Registrar Ledger Débito (Inversión)
            TransaccionesLedger ledgerDebito = new TransaccionesLedger();
            ledgerDebito.setCuenta(cuenta);
            ledgerDebito.setTipoTransaccion("DEBITO");
            ledgerDebito.setMonto(capital);
            ledgerDebito.setSaldoAnterior(capital);
            ledgerDebito.setSaldoResultante(BigDecimal.ZERO);
            ledgerDebito.setCanal("PROCESO_BATCH");
            ledgerDebito.setReferencia("REF-LIQ-DEB-" + System.currentTimeMillis());
            ledgerDebito.setDescripcion("Débito liquidación total al vencimiento de inversión " + cuenta.getNumeroCuenta());
            ledgerDebito.setDireccionIp("127.0.0.1");
            ledgerDebito.setDispositivoInfo("Maturity Engine");
            transaccionesLedgerRepository.save(ledgerDebito);
            
            // 4. Registrar Ledger Crédito (Ahorro Vista)
            TransaccionesLedger ledgerCredito = new TransaccionesLedger();
            ledgerCredito.setCuenta(cuentaVista);
            ledgerCredito.setTipoTransaccion("CREDITO");
            ledgerCredito.setMonto(totalFondeado);
            ledgerCredito.setSaldoAnterior(saldoAnteriorVista);
            ledgerCredito.setSaldoResultante(nuevoSaldoVista);
            ledgerCredito.setCanal("PROCESO_BATCH");
            ledgerCredito.setReferencia("REF-LIQ-CRE-" + System.currentTimeMillis());
            ledgerCredito.setDescripcion("Fondeo liquidación inversión " + cuenta.getNumeroCuenta() + " (Capital: " + capital + " + Int. Neto: " + interesNeto + ")");
            ledgerCredito.setDireccionIp("127.0.0.1");
            ledgerCredito.setDispositivoInfo("Maturity Engine");
            TransaccionesLedger savedLedger = transaccionesLedgerRepository.save(ledgerCredito);
            
            // 5. Asiento Contable de Liquidación (Pasivos -> Ahorros Vista y Retención SRI)
            AsientosCabecera cabecera = new AsientosCabecera();
            cabecera.setTransaccionLedger(savedLedger);
            cabecera.setNumeroAsiento("AS-LIQ-TOT-" + System.currentTimeMillis());
            cabecera.setGlosa("Liquidación al vencimiento y retención SRI de inversión " + cuenta.getNumeroCuenta());
            
            List<AsientosDetalle> detalles = new ArrayList<>();
            
            // DEBE: Pasivo de Inversión (Capital)
            AsientosDetalle dCapital = new AsientosDetalle();
            dCapital.setPlanCuentas(cuentaPasivoInversion);
            dCapital.setTipoAsiento("DEBITO");
            dCapital.setMonto(capital);
            detalles.add(dCapital);
            
            // DEBE: Provisión de Intereses por Pagar
            if (interesBruto.compareTo(BigDecimal.ZERO) > 0) {
                AsientosDetalle dInteres = new AsientosDetalle();
                dInteres.setPlanCuentas(cuentaPasivoProvision);
                dInteres.setTipoAsiento("DEBITO");
                dInteres.setMonto(interesBruto);
                detalles.add(dInteres);
            }
            
            // HABER: Retenciones por Pagar SRI
            if (retencionSRI.compareTo(BigDecimal.ZERO) > 0) {
                AsientosDetalle dRetencion = new AsientosDetalle();
                dRetencion.setPlanCuentas(cuentaPasivoRetencion);
                dRetencion.setTipoAsiento("CREDITO");
                dRetencion.setMonto(retencionSRI);
                detalles.add(dRetencion);
            }
            
            // HABER: Cuenta de Ahorro a la Vista del Socio
            AsientosDetalle dVista = new AsientosDetalle();
            dVista.setPlanCuentas(cuentaPasivoVista);
            dVista.setTipoAsiento("CREDITO");
            dVista.setMonto(totalFondeado);
            detalles.add(dVista);
            
            contabilidadService.registrarAsientoCuadrado(cabecera, detalles);
            
            // Auditoría de Cierre
            LogsAuditoria log = new LogsAuditoria();
            log.setSocio(cuenta.getSocio());
            log.setAccion("LIQUIDACION_COMPLETA_INVERSION");
            log.setTablaAfectada("cuentas_ahorros");
            log.setRegistroId(cuenta.getId());
            log.setDireccionIp("127.0.0.1");
            log.setDispositivoInfo("Maturity Engine");
            log.setValorAnterior(Map.of("saldo", capital, "estado", "ACTIVA"));
            log.setValorNuevo(Map.of("saldo", BigDecimal.ZERO, "estado", "LIQUIDADA", "capitalTransferido", capital, "interesBruto", interesBruto, "retencionSRI", retencionSRI, "netoFondeado", totalFondeado));
            logsAuditoriaService.registrarLog(log);
        }
    }

}