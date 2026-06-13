package com.cooperativa.core.amortizacion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AmortizacionFactory {

    @Autowired
    private Map<String, AmortizacionStrategy> estrategias;

    public AmortizacionStrategy getEstrategia(String sistema) {
        if (sistema == null) {
            throw new IllegalArgumentException("Error: El sistema de amortización no puede ser nulo.");
        }
        AmortizacionStrategy estrategia = estrategias.get(sistema.toUpperCase());
        if (estrategia == null) {
            throw new IllegalArgumentException("Error Financiero: El sistema '" + sistema + "' no está soportado.");
        }
        return estrategia;
    }
}
