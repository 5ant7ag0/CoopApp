package com.cooperativa.core.amortizacion;

import com.cooperativa.core.dto.CuotaSimuladaDTO;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component("AMERICANO")
public class AmortizacionAmericanaStrategy implements AmortizacionStrategy {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    @Override
    public List<CuotaSimuladaDTO> calcular(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual) {
        List<CuotaSimuladaDTO> tabla = new ArrayList<>();
        BigDecimal tasaMensual = tasaAnual.divide(BigDecimal.valueOf(12), MC).divide(BigDecimal.valueOf(100), MC);
        BigDecimal saldoPendiente = monto;
        LocalDate fechaIteracion = LocalDate.now();

        BigDecimal interesFijoPeriodico = monto.multiply(tasaMensual, MC).setScale(2, RoundingMode.HALF_EVEN);

        for (int i = 1; i <= plazoMeses; i++) {
            fechaIteracion = fechaIteracion.plusMonths(1);
            BigDecimal capitalCuota = (i == plazoMeses) ? monto : BigDecimal.ZERO;
            BigDecimal cuotaTotal = capitalCuota.add(interesFijoPeriodico);
            if (i == plazoMeses) {
                saldoPendiente = BigDecimal.ZERO;
            }
            tabla.add(new CuotaSimuladaDTO(
                    i,
                    fechaIteracion,
                    capitalCuota,
                    interesFijoPeriodico,
                    cuotaTotal,
                    saldoPendiente
            ));
        }

        return tabla;
    }
}
