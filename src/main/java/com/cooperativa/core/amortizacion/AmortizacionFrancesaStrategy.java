package com.cooperativa.core.amortizacion;

import com.cooperativa.core.dto.CuotaSimuladaDTO;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component("FRANCES")
public class AmortizacionFrancesaStrategy implements AmortizacionStrategy {

    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);

    @Override
    public List<CuotaSimuladaDTO> calcular(BigDecimal monto, int plazoMeses, BigDecimal tasaAnual) {
        List<CuotaSimuladaDTO> tabla = new ArrayList<>();
        BigDecimal tasaMensual = tasaAnual.divide(BigDecimal.valueOf(12), MC).divide(BigDecimal.valueOf(100), MC);
        BigDecimal saldoPendiente = monto;
        LocalDate fechaIteracion = LocalDate.now();

        BigDecimal unoMasI = BigDecimal.ONE.add(tasaMensual);
        BigDecimal factorPotencia = unoMasI.pow(plazoMeses, MC);
        BigDecimal numerador = tasaMensual.multiply(factorPotencia, MC);
        BigDecimal denominador = factorPotencia.subtract(BigDecimal.ONE);
        BigDecimal cuotaFijaTotal = monto.multiply(numerador.divide(denominador, MC), MC).setScale(2, RoundingMode.HALF_EVEN);

        for (int i = 1; i <= plazoMeses; i++) {
            fechaIteracion = fechaIteracion.plusMonths(1);
            BigDecimal interesCuota = saldoPendiente.multiply(tasaMensual, MC).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal capitalCuota = cuotaFijaTotal.subtract(interesCuota).setScale(2, RoundingMode.HALF_EVEN);

            if (i == plazoMeses) {
                capitalCuota = saldoPendiente;
                cuotaFijaTotal = capitalCuota.add(interesCuota);
            }

            saldoPendiente = saldoPendiente.subtract(capitalCuota);
            tabla.add(new CuotaSimuladaDTO(
                    i,
                    fechaIteracion,
                    capitalCuota,
                    interesCuota,
                    cuotaFijaTotal,
                    saldoPendiente.max(BigDecimal.ZERO)
            ));
        }

        return tabla;
    }
}
