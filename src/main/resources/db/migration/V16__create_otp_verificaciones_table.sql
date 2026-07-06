-- Create table otp_verificaciones
CREATE TABLE IF NOT EXISTS otp_verificaciones (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    email VARCHAR(100) NOT NULL,
    otp_code VARCHAR(6) NOT NULL,
    fecha_expiracion TIMESTAMP NOT NULL,
    verificado BOOLEAN NOT NULL DEFAULT FALSE,
    intentos_fallidos INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
