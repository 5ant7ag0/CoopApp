package com.cooperativa.core.scheduler;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.service.CreditoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class DebitoAutomaticoScheduler {

    private static final Logger log = LoggerFactory.getLogger(DebitoAutomaticoScheduler.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private CreditoService creditoService;

    /**
     * Tarea automatizada que se ejecuta a la medianoche (00:00:00) diariamente para
     * debitar automáticamente las cuotas de crédito vencidas o exigibles.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void procesarDebitosAutomaticos() {
        log.info("Iniciando lote nocturno automatizado: Motor de Débito Automático de Créditos.");
        List<Empresa> empresas = empresaRepository.findAll();
        LocalDate hoy = LocalDate.now();

        for (Empresa empresa : empresas) {
            if ("ACTIVO".equals(empresa.getEstado())) {
                try {
                    TenantContext.setCurrentTenant(empresa.getId());
                    log.info("Procesando debitos automaticos para tenant: {} (ID: {}) para la fecha: {}", 
                            empresa.getNombreComercial(), empresa.getId(), hoy);
                    creditoService.ejecutarDebitosAutomaticosParaTenant(hoy);
                    log.info("Débitos automáticos completados con éxito para tenant: {}", empresa.getNombreComercial());
                } catch (Exception e) {
                    log.error("Error al procesar debitos automaticos para tenant ID: " + empresa.getId() + ". Detalle: " + e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
        }
        log.info("Lote nocturno automatizado de débitos automáticos finalizado.");
    }
}
