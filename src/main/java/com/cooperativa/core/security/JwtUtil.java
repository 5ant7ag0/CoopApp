package com.cooperativa.core.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para la creacion y validacion de tokens JWT (JSON Web Tokens).
 * Incorpora el control de inquilino (Tenant ID) y rol en los claims del token.
 */
@Component
public class JwtUtil {

    // Clave secreta robusta (En produccion se debe inyectar desde variables de entorno)
    private static final String SECRET_KEY = "CooperativaCoreSecretKey2026!SistemaFinancieroSaaS";
    private static final String ISSUER = "cooperativa-core-api";

    // Duracion del token: 8 horas (en milisegundos)
    private static final long EXPIRATION_TIME = 8 * 60 * 60 * 1000L; 

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil() {
        this.algorithm = Algorithm.HMAC256(SECRET_KEY);
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build();
    }

    /**
     * Genera un token JWT para un usuario administrativo o socio.
     * 
     * @param username Nombre de usuario o identificacion del socio.
     * @param rol Rol del usuario (ej. 'CAJERO', 'SOCIO', etc.).
     * @param empresaId ID de la empresa/cooperativa (Tenant ID).
     * @return El token JWT firmado.
     */
    public String generateToken(String username, String rol, Integer empresaId) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim("rol", rol)
                .withClaim("empresaId", empresaId)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(algorithm);
    }

    /**
     * Valida un token JWT y retorna su informacion (Claims).
     * 
     * @param token Token en formato String.
     * @return Mapa con los datos del Subject, Rol y EmpresaId decodificados.
     * @throws Exception Si el token es invalido, expiro o la firma no coincide.
     */
    public Map<String, Object> validateTokenAndGetClaims(String token) {
        DecodedJWT decodedJWT = verifier.verify(token);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", decodedJWT.getSubject());
        claims.put("rol", decodedJWT.getClaim("rol").asString());
        claims.put("empresaId", decodedJWT.getClaim("empresaId").asInt());
        claims.put("expiresAt", decodedJWT.getExpiresAt());
        
        return claims;
    }
}
