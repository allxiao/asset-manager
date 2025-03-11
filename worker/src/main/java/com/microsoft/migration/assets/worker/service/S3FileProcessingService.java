package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@Profile("!dev")
@RequiredArgsConstructor
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final S3Client s3Client;
    private final ImageMetadataRepository imageMetadataRepository;
    
    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
                
        try (var inputStream = s3Client.getObject(request)) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();
                
        s3Client.putObject(request, RequestBody.fromFile(source));
        
        // Save or update thumbnail metadata
        ImageMetadata metadata = imageMetadataRepository.findById(extractOriginalKey(key))
            .orElseGet(() -> {
                ImageMetadata newMetadata = new ImageMetadata();
                newMetadata.setId(extractOriginalKey(key));
                return newMetadata;
            });

        metadata.setThumbnailKey(key);
        metadata.setThumbnailUrl(generateUrl(key));
        imageMetadataRepository.save(metadata);
    }

    @Override
    public String getStorageType() {
        return "s3";
    }

    @Override
    protected String generateUrl(String key) {
        GetUrlRequest request = GetUrlRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.utilities().getUrl(request).toString();
    }

    private String extractOriginalKey(String thumbnailKey) {
        // Remove "_thumbnail" from the key to get the original key
        int dotIndex = thumbnailKey.lastIndexOf('.');
        if (dotIndex > 0) {
            String nameWithoutExt = thumbnailKey.substring(0, dotIndex);
            String ext = thumbnailKey.substring(dotIndex);
            if (nameWithoutExt.endsWith("_thumbnail")) {
                return nameWithoutExt.substring(0, nameWithoutExt.length() - 10) + ext;
            }
        }
        return thumbnailKey;
    }
}