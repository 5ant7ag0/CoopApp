package com.cooperativa.core.service;

import com.cooperativa.core.dto.CajaVentanillaDTO;
import com.cooperativa.core.model.Agencia;
import com.cooperativa.core.model.CajasVentanilla;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.AgenciaRepository;
import com.cooperativa.core.repository.CajasVentanillaRepository;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.repository.CajaDiariaRepository;
import com.cooperativa.core.repository.TransaccionesLedgerRepository;
import com.cooperativa.core.model.CajaDiaria;
import com.cooperativa.core.model.TransaccionesLedger;
import com.cooperativa.core.config.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CajasVentanillaService {

    private final CajasVentanillaRepository cajasRepository;
    private final AgenciaRepository agenciaRepository;
    private final PlanCuentasRepository planCuentasRepository;
    private final UsuarioAdminRepository usuarioAdminRepository;
    private final CajaDiariaRepository cajaDiariaRepository;
    private final TransaccionesLedgerRepository transaccionesLedgerRepository;

    public List<CajaVentanillaDTO> listarCajas() {
        return cajasRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private void validarCuentaContableUnica(Integer cuentaContableId, Integer cajaIdExcluida) {
        if (cuentaContableId == null) return;
        List<CajasVentanilla> cajas = cajasRepository.findByCuentaContableId(cuentaContableId);
        for (CajasVentanilla caja : cajas) {
            if (!"INACTIVA".equals(caja.getEstado()) && !caja.getId().equals(cajaIdExcluida)) {
                throw new RuntimeException("Esta cuenta contable ya se encuentra asignada a otra ventanilla.");
            }
        }
    }

    public CajaVentanillaDTO crearCaja(CajaVentanillaDTO dto) {
        String codigo = dto.getCodigo();
        if (codigo == null || codigo.trim().isEmpty() || codigo.equalsIgnoreCase("Autogenerado")) {
            long count = cajasRepository.count();
            codigo = String.format("CAJ-%02d", count + 1);
        }

        if (cajasRepository.findByCodigo(codigo).isPresent()) {
            throw new RuntimeException("Ya existe una caja con el código: " + codigo);
        }

        CajasVentanilla entity = new CajasVentanilla();
        entity.setCodigo(codigo);
        entity.setNombre(dto.getNombre());
        
        if (dto.getAgenciaId() != null) {
            Agencia agencia = agenciaRepository.findById(dto.getAgenciaId())
                .orElseThrow(() -> new RuntimeException("Agencia no encontrada"));
            entity.setAgencia(agencia);
        }

        if (dto.getCuentaContableId() != null) {
            validarCuentaContableUnica(dto.getCuentaContableId(), null);
            PlanCuentas cuenta = planCuentasRepository.findById(dto.getCuentaContableId())
                .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));
            entity.setCuentaContable(cuenta);
        }

        entity.setSaldoBase(dto.getSaldoBase() != null ? dto.getSaldoBase() : BigDecimal.ZERO);
        entity.setLimiteEfectivoMaximo(dto.getLimiteEfectivoMaximo() != null ? dto.getLimiteEfectivoMaximo() : BigDecimal.ZERO);
        entity.setSaldoActual(BigDecimal.ZERO); // Force to zero initially as per user request
        
        if (dto.getEstado() != null) entity.setEstado(dto.getEstado());

        CajasVentanilla saved = cajasRepository.save(entity);
        return toDTO(saved);
    }
    
    public CajaVentanillaDTO actualizarCaja(Integer id, CajaVentanillaDTO dto) {
        CajasVentanilla entity = cajasRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Caja no encontrada"));
            
        if (!entity.getCodigo().equals(dto.getCodigo()) && cajasRepository.findByCodigo(dto.getCodigo()).isPresent()) {
            throw new RuntimeException("Ya existe otra caja con el código: " + dto.getCodigo());
        }
        
        entity.setCodigo(dto.getCodigo());
        entity.setNombre(dto.getNombre());
        entity.setEstado(dto.getEstado() != null ? dto.getEstado() : entity.getEstado());
        entity.setSaldoBase(dto.getSaldoBase() != null ? dto.getSaldoBase() : entity.getSaldoBase());
        entity.setLimiteEfectivoMaximo(dto.getLimiteEfectivoMaximo() != null ? dto.getLimiteEfectivoMaximo() : entity.getLimiteEfectivoMaximo());
        
        if (dto.getAgenciaId() != null && (entity.getAgencia() == null || !entity.getAgencia().getId().equals(dto.getAgenciaId()))) {
            Agencia agencia = agenciaRepository.findById(dto.getAgenciaId())
                .orElseThrow(() -> new RuntimeException("Agencia no encontrada"));
            entity.setAgencia(agencia);
        }
        
        if (dto.getCuentaContableId() != null && (entity.getCuentaContable() == null || !entity.getCuentaContable().getId().equals(dto.getCuentaContableId()))) {
            validarCuentaContableUnica(dto.getCuentaContableId(), id);
            PlanCuentas cuenta = planCuentasRepository.findById(dto.getCuentaContableId())
                .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));
            entity.setCuentaContable(cuenta);
        }
        
        CajasVentanilla saved = cajasRepository.save(entity);
        return toDTO(saved);
    }

    private CajaVentanillaDTO toDTO(CajasVentanilla entity) {
        CajaVentanillaDTO dto = new CajaVentanillaDTO();
        dto.setId(entity.getId());
        dto.setCodigo(entity.getCodigo());
        dto.setNombre(entity.getNombre());
        dto.setEstado(entity.getEstado());
        dto.setSaldoBase(entity.getSaldoBase());
        dto.setSaldoActual(entity.getSaldoActual());
        dto.setLimiteEfectivoMaximo(entity.getLimiteEfectivoMaximo());
        dto.setCreatedAt(entity.getCreatedAt());

        if (entity.getAgencia() != null) {
            dto.setAgenciaId(entity.getAgencia().getId());
            dto.setAgenciaNombre(entity.getAgencia().getNombre());
        }

        if (entity.getCuentaContable() != null) {
            dto.setCuentaContableId(entity.getCuentaContable().getId());
            dto.setCuentaContableNombre(entity.getCuentaContable().getNombreCuenta());
        }
        
        // Find assigned user if any
        Optional<UsuariosAdmin> assignedUser = usuarioAdminRepository.findByCajaId(entity.getId());
        if (assignedUser.isPresent()) {
            dto.setCajeroAsignado(assignedUser.get().getNombresCompletos());
            
            Integer tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null) {
                // Check if this user has an active CajaDiaria for today
                Optional<CajaDiaria> activeCaja = cajaDiariaRepository.findByUsuarioCajeroIdAndEstadoAndEmpresaId(
                    assignedUser.get().getId(), "APERTURADA", tenantId
                );
                
                if (activeCaja.isPresent()) {
                    CajaDiaria c = activeCaja.get();
                    dto.setEstadoOperativo("ABIERTA"); // Set operational status
                    
                    // Calculate real-time physical balance
                    LocalDateTime inicioDia = c.getFechaContable().atStartOfDay();
                    LocalDateTime finDia = c.getFechaContable().atTime(23, 59, 59, 999999999);
                    
                    List<TransaccionesLedger> transacciones = transaccionesLedgerRepository
                        .findByUsuarioAdminIdAndCanalAndFechaContableBetween(
                            assignedUser.get().getId(), "VENTANILLA", inicioDia, finDia
                        );
                        
                    BigDecimal ingresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal egresos = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    
                    for (TransaccionesLedger tx : transacciones) {
                        BigDecimal montoTx = tx.getMonto().setScale(2, RoundingMode.HALF_UP);
                        if ("CREDITO".equals(tx.getTipoTransaccion())) {
                            ingresos = ingresos.add(montoTx);
                        } else if ("DEBITO".equals(tx.getTipoTransaccion())) {
                            // Check if it's not a puente deposit or something else if needed, 
                            // but TransaccionesLedger represents actual physical in/outs when Canal=VENTANILLA
                            egresos = egresos.add(montoTx);
                        }
                    }
                    
                    BigDecimal actual = c.getMontoApertura().add(ingresos).subtract(egresos);
                    dto.setSaldoActual(actual);
                    return dto;
                }
            }
        } else {
            dto.setCajeroAsignado("Sin Asignar");
        }

        // If not explicitly "ABIERTA" operational state, fallback to "CERRADA", unless administratively "INACTIVA"
        if ("INACTIVA".equals(entity.getEstado())) {
            dto.setEstadoOperativo("INACTIVA");
        } else {
            dto.setEstadoOperativo("CERRADA");
        }
        dto.setSaldoActual(BigDecimal.ZERO);

        return dto;
    }
}
