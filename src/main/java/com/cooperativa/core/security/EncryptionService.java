package com.cooperativa.core.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio para el hashing seguro de contrasenas utilizando el algoritmo BCrypt.
 */
@Service
public class EncryptionService {

    private final BCryptPasswordEncoder encoder;

    public EncryptionService() {
        // Inicializa el codificador de BCrypt con la fuerza por defecto (10 rounds)
        this.encoder = new BCryptPasswordEncoder();
    }

    /**
     * Genera un hash seguro a partir de una contrasena en texto plano.
     */
    public String hashPassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("La contrasena no puede estar vacia");
        }
        return encoder.encode(password);
    }

    /**
     * Verifica si una contrasena en texto plano coincide con un hash guardado.
     */
    public boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return encoder.matches(plainPassword, hashedPassword);
    }
}
