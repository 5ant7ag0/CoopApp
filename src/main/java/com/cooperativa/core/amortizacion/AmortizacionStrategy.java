package com.cooperativa.core.amortizacion;

import com.cooperativa.core.dto.CuotaSimuladaDTO;
import java.math.BigDecimal;
import java.util.List;

public interface AmortizacionStrategy {
    List<CuotaSimuladaDTO> calcular(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual);
}
