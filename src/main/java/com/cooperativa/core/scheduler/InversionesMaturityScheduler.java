package com.cooperativa.core.scheduler;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.service.CuentasAhorrosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class InversionesMaturityScheduler {

    private static final Logger log = LoggerFactory.getLogger(InversionesMaturityScheduler.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private CuentasAhorrosService cuentasAhorrosService;

    /**
     * Tarea automatizada que se ejecuta a las 00:15 daily para evaluar e
     * liquidar o renovar las cuentas de inversiones vencidas (PLAZO_FIJO o AHORRO_PROGRAMADO)
     * de acuerdo con la normativa SEPS y retenciones del SRI.
     */
    @Scheduled(cron = "0 15 0 * * *")
    public void procesarVencimientosDeInversiones() {
        log.info("Iniciando lote nocturno automatizado: Motor de Vencimientos y Liquidaciones de Inversiones.");
        List<Empresa> empresas = empresaRepository.findAll();
        LocalDate hoy = LocalDate.now();

        for (Empresa empresa : empresas) {
            if ("ACTIVO".equals(empresa.getEstado())) {
                try {
                    TenantContext.setCurrentTenant(empresa.getId());
                    log.info("Procesando vencimientos para tenant: {} (ID: {}) para la fecha: {}", 
                            empresa.getNombreComercial(), empresa.getId(), hoy);
                    int procesados = cuentasAhorrosService.procesarVencimientosDiarios(hoy);
                    log.info("Vencimientos de inversiones completados con éxito para tenant: {}. Cuentas procesadas: {}", 
                            empresa.getNombreComercial(), procesados);
                } catch (Exception e) {
                    log.error("Error al procesar vencimientos de inversiones para tenant ID: " + empresa.getId() + ". Detalle: " + e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
        }
        log.info("Lote nocturno automatizado de vencimiento de inversiones finalizado.");
    }
}
