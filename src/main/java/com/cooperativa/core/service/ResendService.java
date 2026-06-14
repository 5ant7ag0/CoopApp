package com.cooperativa.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ResendService {

    private static final Logger log = LoggerFactory.getLogger(ResendService.class);

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void enviarCorreo(String destinatario, String asunto, String htmlBody) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> bodyMap = Map.of(
                    "from", fromEmail,
                    "to", List.of(destinatario),
                    "subject", asunto,
                    "html", htmlBody
                );

                String payload = objectMapper.writeValueAsString(bodyMap);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("[ResendService] Correo enviado exitosamente a {}. Response: {}", destinatario, response.body());
                } else {
                    log.error("[ResendService] Error al enviar correo a {}. Status: {}, Response: {}", destinatario, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("[ResendService] Excepcion al enviar correo a " + destinatario, e);
            }
        });
    }
}
