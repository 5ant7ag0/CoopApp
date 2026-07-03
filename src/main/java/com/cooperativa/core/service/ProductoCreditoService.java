package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.ProductoCreditoDTO;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.ProductoCredito;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.ProductoCreditoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductoCreditoService {

    @Autowired
    private ProductoCreditoRepository productoCreditoRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    public List<ProductoCredito> listarTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalStateException("Tenant no definido");
        return productoCreditoRepository.findByEmpresaId(tenantId);
    }

    public List<ProductoCredito> listarActivos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalStateException("Tenant no definido");
        return productoCreditoRepository.findByEmpresaIdAndEstado(tenantId, "ACTIVO");
    }

    public ProductoCredito obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalStateException("Tenant no definido");
        return productoCreditoRepository.findByIdAndEmpresaId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Producto de crédito no encontrado"));
    }

    public ProductoCredito crear(ProductoCreditoDTO dto) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) throw new IllegalStateException("Tenant no definido");

        ProductoCredito p = new ProductoCredito();
        mapearDtoAEntidad(dto, p, tenantId);
        p.setEstado("ACTIVO");
        return productoCreditoRepository.save(p);
    }

    public ProductoCredito actualizar(Integer id, ProductoCreditoDTO dto) {
        ProductoCredito p = obtenerPorId(id);
        Integer tenantId = TenantContext.getCurrentTenant();
        mapearDtoAEntidad(dto, p, tenantId);
        p.setEstado(dto.getEstado());
        return productoCreditoRepository.save(p);
    }

    private void mapearDtoAEntidad(ProductoCreditoDTO dto, ProductoCredito p, Integer tenantId) {
        p.setNombre(dto.getNombre());
        p.setMontoMinimo(dto.getMontoMinimo());
        p.setMontoMaximo(dto.getMontoMaximo());
        p.setPlazoMinimoMeses(dto.getPlazoMinimoMeses());
        p.setPlazoMaximoMeses(dto.getPlazoMaximoMeses());
        p.setTasaInteresAnual(dto.getTasaInteresAnual());
        p.setTasaMoraAnual(dto.getTasaMoraAnual());
        p.setPorcentajeSeguroDesgravamen(dto.getPorcentajeSeguroDesgravamen());

        PlanCuentas cuentaCartera = planCuentasRepository.findByIdAndEmpresaId(dto.getCuentaContableCarteraId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de cartera no encontrada"));
        PlanCuentas cuentaIngresos = planCuentasRepository.findByIdAndEmpresaId(dto.getCuentaContableIngresosInteresesId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de ingresos por interés no encontrada"));
        PlanCuentas cuentaMora = planCuentasRepository.findByIdAndEmpresaId(dto.getCuentaContableMoraId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta de ingresos por mora no encontrada"));
        
        p.setCuentaContableCartera(cuentaCartera);
        p.setCuentaContableIngresosIntereses(cuentaIngresos);
        p.setCuentaContableMora(cuentaMora);

        if (dto.getCuentaContableSeguroId() != null) {
            PlanCuentas cuentaSeguro = planCuentasRepository.findByIdAndEmpresaId(dto.getCuentaContableSeguroId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Cuenta de seguro no encontrada"));
            p.setCuentaContableSeguro(cuentaSeguro);
        } else {
            p.setCuentaContableSeguro(null);
        }
    }
}
