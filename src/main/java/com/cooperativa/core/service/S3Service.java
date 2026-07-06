package com.cooperativa.core.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${app.s3.endpoint:}")
    private String endpoint;

    @Value("${app.s3.access-key:}")
    private String accessKey;

    @Value("${app.s3.secret-key:}")
    private String secretKey;

    @Value("${app.s3.bucket-name:}")
    private String bucketName;

    @Value("${app.s3.region:us-east-1}")
    private String region;

    @Value("${app.s3.public-url-prefix:}")
    private String publicUrlPrefix;

    private S3Client s3Client;
    private boolean s3Enabled = false;

    @PostConstruct
    public void init() {
        if (endpoint != null && !endpoint.trim().isEmpty() &&
            accessKey != null && !accessKey.trim().isEmpty() &&
            secretKey != null && !secretKey.trim().isEmpty() &&
            bucketName != null && !bucketName.trim().isEmpty()) {
            
            try {
                this.s3Client = S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        ))
                        .region(Region.of(region))
                        .build();
                this.s3Enabled = true;
                System.out.println("AWS S3 / Cloudflare R2 Client initialized successfully.");
            } catch (Exception e) {
                System.err.println("Error initializing S3 Client: " + e.getMessage());
            }
        } else {
            System.out.println("AWS S3 / Cloudflare R2 not configured. Falling back to local file system storage.");
        }
    }

    public String subirArchivo(MultipartFile file, String folder, String keyPrefix) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Error: El archivo provisto está vacío.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "png";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String filename = keyPrefix + "_" + UUID.randomUUID().toString() + "." + extension;
        String objectKey = folder + "/" + filename;

        if (s3Enabled) {
            // Subir a S3 / R2
            try (InputStream inputStream = file.getInputStream()) {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));

                // Construir URL pública
                if (publicUrlPrefix != null && !publicUrlPrefix.trim().isEmpty()) {
                    return publicUrlPrefix.endsWith("/") ? publicUrlPrefix + objectKey : publicUrlPrefix + "/" + objectKey;
                } else {
                    return endpoint + "/" + bucketName + "/" + objectKey;
                }
            }
        } else {
            // Guardar localmente
            String uploadDir = System.getProperty("user.dir") + "/uploads/" + folder + "/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File destFile = new File(dir, filename);
            file.transferTo(destFile);
            return "/uploads/" + folder + "/" + filename;
        }
    }
}
