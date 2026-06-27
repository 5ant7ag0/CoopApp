package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.ProductoAhorroRequestDTO;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.ProductoAhorro;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.ProductoAhorroRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductoAhorroServiceTest {

    @Mock
    private ProductoAhorroRepository productoAhorroRepository;

    @Mock
    private PlanCuentasRepository planCuentasRepository;

    @Mock
    private UsuarioAdminRepository usuarioAdminRepository;

    @Mock
    private LogsAuditoriaService logsAuditoriaService;

    @InjectMocks
    private ProductoAhorroService productoAhorroService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenant(1);
    }

    @Test
    public void testCrearProductoAhorroExitoso() {
        // Arrange
        ProductoAhorroRequestDTO dto = new ProductoAhorroRequestDTO();
        dto.setNombre("Premium Vista");
        dto.setTipoProducto("AHORRO_VISTA");
        dto.setTasaInteresAnual(BigDecimal.valueOf(3.5));
        dto.setMontoMinimoApertura(BigDecimal.valueOf(10.0));
        dto.setSaldoMinimoRequerido(BigDecimal.valueOf(5.0));
        dto.setTipoRetiro("LIBRE");
        dto.setTasaPenalizacionRetiro(BigDecimal.ZERO);
        dto.setCuentaContablePasivoId(100);
        dto.setCuentaContableGastoId(200);
        dto.setEstado("ACTIVO");

        PlanCuentas pasivo = new PlanCuentas();
        pasivo.setId(100);
        pasivo.setEmpresaId(1);

        PlanCuentas gasto = new PlanCuentas();
        gasto.setId(200);
        gasto.setEmpresaId(1);

        when(planCuentasRepository.findById(100)).thenReturn(Optional.of(pasivo));
        when(planCuentasRepository.findById(200)).thenReturn(Optional.of(gasto));
        
        ProductoAhorro savedProduct = new ProductoAhorro();
        savedProduct.setId(5);
        savedProduct.setNombre("Premium Vista");
        savedProduct.setTipoProducto("AHORRO_VISTA");
        savedProduct.setTasaInteresAnual(BigDecimal.valueOf(3.5));

        when(productoAhorroRepository.save(any(ProductoAhorro.class))).thenReturn(savedProduct);

        // Act
        ProductoAhorro result = productoAhorroService.crear(dto, "admin");

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getId());
        assertEquals("Premium Vista", result.getNombre());
        verify(productoAhorroRepository, times(1)).save(any(ProductoAhorro.class));
    }
}
