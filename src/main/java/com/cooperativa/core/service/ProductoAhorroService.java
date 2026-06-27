package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.ProductoAhorroRequestDTO;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.ProductoAhorro;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.ProductoAhorroRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ProductoAhorroService {

    @Autowired
    private ProductoAhorroRepository productoAhorroRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    public List<ProductoAhorro> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return productoAhorroRepository.findByEmpresaId(tenantId);
    }

    public List<ProductoAhorro> obtenerActivos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return productoAhorroRepository.findByEstadoAndEmpresaId("ACTIVO", tenantId);
    }

    public ProductoAhorro obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        return productoAhorroRepository.findByIdAndEmpresaId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Producto de ahorro no encontrado o no pertenece a esta cooperativa."));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductoAhorro crear(ProductoAhorroRequestDTO dto, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede guardar datos sin un X-Tenant-ID definido.");
        }

        PlanCuentas pasivo = planCuentasRepository.findById(dto.getCuentaContablePasivoId())
                .filter(p -> p.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable de pasivo no válida o no pertenece a esta cooperativa."));

        PlanCuentas gasto = planCuentasRepository.findById(dto.getCuentaContableGastoId())
                .filter(g -> g.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable de gasto no válida o no pertenece a esta cooperativa."));

        ProductoAhorro producto = new ProductoAhorro();
        producto.setNombre(dto.getNombre());
        producto.setTipoProducto(dto.getTipoProducto());
        producto.setTasaInteresAnual(dto.getTasaInteresAnual());
        producto.setMontoMinimoApertura(dto.getMontoMinimoApertura());
        producto.setSaldoMinimoRequerido(dto.getSaldoMinimoRequerido());
        producto.setTipoRetiro(dto.getTipoRetiro());
        producto.setTasaPenalizacionRetiro(dto.getTasaPenalizacionRetiro());
        producto.setCuentaContablePasivo(pasivo);
        producto.setCuentaContableGasto(gasto);
        if (dto.getEstado() != null) {
            producto.setEstado(dto.getEstado());
        }

        ProductoAhorro guardado = productoAhorroRepository.save(producto);
        registrarAuditoria(authUsername, tenantId, "CREAR_PRODUCTO_AHORRO", guardado.getId(), null, Map.of(
                "nombre", guardado.getNombre(),
                "tasaInteresAnual", guardado.getTasaInteresAnual(),
                "tipoProducto", guardado.getTipoProducto()
        ));

        return guardado;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductoAhorro actualizar(Integer id, ProductoAhorroRequestDTO dto, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        ProductoAhorro existente = obtenerPorId(id);

        PlanCuentas pasivo = planCuentasRepository.findById(dto.getCuentaContablePasivoId())
                .filter(p -> p.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable de pasivo no válida o no pertenece a esta cooperativa."));

        PlanCuentas gasto = planCuentasRepository.findById(dto.getCuentaContableGastoId())
                .filter(g -> g.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable de gasto no válida o no pertenece a esta cooperativa."));

        Map<String, Object> valorAnterior = Map.of(
                "nombre", existente.getNombre(),
                "tasaInteresAnual", existente.getTasaInteresAnual(),
                "estado", existente.getEstado()
        );

        existente.setNombre(dto.getNombre());
        existente.setTipoProducto(dto.getTipoProducto());
        existente.setTasaInteresAnual(dto.getTasaInteresAnual());
        existente.setMontoMinimoApertura(dto.getMontoMinimoApertura());
        existente.setSaldoMinimoRequerido(dto.getSaldoMinimoRequerido());
        existente.setTipoRetiro(dto.getTipoRetiro());
        existente.setTasaPenalizacionRetiro(dto.getTasaPenalizacionRetiro());
        existente.setCuentaContablePasivo(pasivo);
        existente.setCuentaContableGasto(gasto);
        if (dto.getEstado() != null) {
            existente.setEstado(dto.getEstado());
        }

        ProductoAhorro actualizado = productoAhorroRepository.save(existente);
        registrarAuditoria(authUsername, tenantId, "ACTUALIZAR_PRODUCTO_AHORRO", actualizado.getId(), valorAnterior, Map.of(
                "nombre", actualizado.getNombre(),
                "tasaInteresAnual", actualizado.getTasaInteresAnual(),
                "estado", actualizado.getEstado()
        ));

        return actualizado;
    }

    @Transactional(rollbackFor = Exception.class)
    public void eliminarLogico(Integer id, String authUsername) {
        Integer tenantId = TenantContext.getCurrentTenant();
        ProductoAhorro existente = obtenerPorId(id);

        Map<String, Object> valorAnterior = Map.of("estado", existente.getEstado());
        existente.setEstado("INACTIVO");
        productoAhorroRepository.save(existente);

        registrarAuditoria(authUsername, tenantId, "INACTIVAR_PRODUCTO_AHORRO", existente.getId(), valorAnterior, Map.of("estado", "INACTIVO"));
    }

    private void registrarAuditoria(String authUsername, Integer tenantId, String accion, Integer registroId, Map<String, Object> anterior, Map<String, Object> nuevo) {
        Integer adminId = null;
        if (authUsername != null) {
            adminId = usuarioAdminRepository.findByUsernameAndEmpresaId(authUsername, tenantId)
                    .map(UsuariosAdmin::getId)
                    .orElse(null);
        }
        if (adminId == null) {
            List<UsuariosAdmin> admins = usuarioAdminRepository.findByEmpresaId(tenantId);
            if (!admins.isEmpty()) {
                adminId = admins.get(0).getId();
            }
        }

        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(adminId);
        log.setAccion(accion);
        log.setTablaAfectada("productos_ahorro");
        log.setRegistroId(registroId);
        log.setDireccionIp("127.0.0.1");
        log.setDispositivoInfo("Web Portal");
        log.setValorAnterior(anterior);
        log.setValorNuevo(nuevo);
        logsAuditoriaService.registrarLog(log);
    }
}
