package com.cooperativa.core.exception;

/**
 * Excepción de dominio para representar errores de cooperativas duplicadas (RUC o Código SEPS).
 */
public class EmpresaDuplicadaException extends RuntimeException {
    public EmpresaDuplicadaException(String message) {
        super(message);
    }
}
