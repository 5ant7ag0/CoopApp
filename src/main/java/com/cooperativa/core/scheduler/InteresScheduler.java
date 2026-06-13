package com.cooperativa.core.scheduler;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.service.InteresAhorroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class InteresScheduler {

    private static final Logger log = LoggerFactory.getLogger(InteresScheduler.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private InteresAhorroService interesAhorroService;

    /**
     * Tarea automatizada que se ejecuta a la medianoche (00:00:00) diariamente para
     * devengar intereses de todas las cooperativas activas.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void devengarInteresesAutomatico() {
        log.info("Iniciando lote nocturno automatizado: Devengo Diario de Intereses.");
        List<Empresa> empresas = empresaRepository.findAll();
        LocalDate ayer = LocalDate.now().minusDays(1); // Devenga el día que acaba de cerrar

        for (Empresa empresa : empresas) {
            if ("ACTIVO".equals(empresa.getEstado())) {
                try {
                    TenantContext.setCurrentTenant(empresa.getId());
                    log.info("Procesando devengo diario para tenant: {} (ID: {}) para la fecha: {}", 
                            empresa.getNombreComercial(), empresa.getId(), ayer);
                    interesAhorroService.devengarInteresesDiarios(ayer, null);
                    log.info("Devengo diario completado con éxito para tenant: {}", empresa.getNombreComercial());
                } catch (Exception e) {
                    log.error("Error al procesar devengo diario para tenant ID: " + empresa.getId() + ". Detalle: " + e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
        }
        log.info("Lote nocturno automatizado de devengo diario finalizado.");
    }

    /**
     * Tarea automatizada que se ejecuta el primer día de cada mes a las 00:30:00
     * para capitalizar los intereses devengados del mes anterior en todos los tenants.
     */
    @Scheduled(cron = "0 30 0 1 * *")
    public void capitalizarInteresesAutomatico() {
        log.info("Iniciando lote mensual automatizado: Capitalización de Intereses.");
        List<Empresa> empresas = empresaRepository.findAll();
        
        // Obtener el mes y año a capitalizar (el mes anterior)
        LocalDate hoy = LocalDate.now();
        LocalDate mesAnterior = hoy.minusMonths(1);
        int anio = mesAnterior.getYear();
        int mes = mesAnterior.getMonthValue();

        for (Empresa empresa : empresas) {
            if ("ACTIVO".equals(empresa.getEstado())) {
                try {
                    TenantContext.setCurrentTenant(empresa.getId());
                    log.info("Procesando capitalización para tenant: {} (ID: {}) para el período: {}/{}", 
                            empresa.getNombreComercial(), empresa.getId(), mes, anio);
                    interesAhorroService.capitalizarInteresesMensuales(anio, mes, null);
                    log.info("Capitalización completada con éxito para tenant: {}", empresa.getNombreComercial());
                } catch (Exception e) {
                    log.error("Error al procesar capitalización para tenant ID: " + empresa.getId() + ". Detalle: " + e.getMessage(), e);
                } finally {
                    TenantContext.clear();
                }
            }
        }
        log.info("Lote mensual automatizado de capitalización finalizado.");
    }
}
