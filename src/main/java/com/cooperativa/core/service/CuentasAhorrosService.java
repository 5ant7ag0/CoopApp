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

@Service
public class CuentasAhorrosService {

    @Autowired
    private CuentasAhorrosRepository cuentasAhorrosRepository;

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
        cuenta.setTipo(dto.getTipo());
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
        PlanCuentas cuentaAhorrosPc = planCuentasRepository.findByCodigoContableAndEmpresaId("2.1.01.05", tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable 2.1.01.05 no parametrizada para esta institucion."));

        AsientosCabecera cabecera = new AsientosCabecera();
        cabecera.setTransaccionLedger(ledgerOrigenGuardado);
        cabecera.setNumeroAsiento("AS-TRF-" + System.currentTimeMillis());
        cabecera.setGlosa("Asiento contable automático por transferencia interna de $" + monto + " entre cuentas (" + cuentaOrigen.getNumeroCuenta() + " -> " + cuentaDestino.getNumeroCuenta() + ")");

        List<AsientosDetalle> detalles = new ArrayList<>();

        // Apunte 1: Al DEBITO (Debe) - Disminuye el pasivo de la cuenta de ahorros origen (menos deuda con socio origen)
        AsientosDetalle debitoDetalle = new AsientosDetalle();
        debitoDetalle.setPlanCuentas(cuentaAhorrosPc);
        debitoDetalle.setTipoAsiento("DEBITO");
        debitoDetalle.setMonto(monto);
        detalles.add(debitoDetalle);

        // Apunte 2: Al CREDITO (Haber) - Aumenta el pasivo de la cuenta de ahorros destino (más deuda con socio destino)
        AsientosDetalle creditoDetalle = new AsientosDetalle();
        creditoDetalle.setPlanCuentas(cuentaAhorrosPc);
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

        final String codigoContable = "APORTACIONES".equals(cuenta.getTipo()) ? "3.1.01.05" : "2.1.01.05";
        PlanCuentas cuentaAhorros = planCuentasRepository.findByCodigoContableAndEmpresaId(codigoContable, tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + codigoContable + " no parametrizada."));

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
        if ("APORTACIONES".equals(cuenta.getTipo())) {
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

        final String codigoContable = "APORTACIONES".equals(cuenta.getTipo()) ? "3.1.01.05" : "2.1.01.05";
        PlanCuentas cuentaAhorros = planCuentasRepository.findByCodigoContableAndEmpresaId(codigoContable, tenantId)
                .orElseThrow(() -> new IllegalStateException("Error de Configuración: Cuenta contable " + codigoContable + " no parametrizada."));

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
}