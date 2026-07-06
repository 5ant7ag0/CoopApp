package com.cooperativa.core;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateToken {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = "$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq";
        System.out.println("Match CoopSF2026!: " + encoder.matches("CoopSF2026!", hash));
    }
}
