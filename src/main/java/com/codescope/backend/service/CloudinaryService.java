package com.codescope.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Uploads a file to Cloudinary as a raw resource (for code / text / zip files).
     *
     * @param filePart  The reactive FilePart to upload
     * @param folder    The Cloudinary folder (e.g. "codescope/projects/{projectId}")
     * @return A Mono emitting a Map with "url", "public_id", "bytes", "format", "resource_type"
     */
    public Mono<Map<String, Object>> uploadFile(FilePart filePart, String folder) {
        String filename = filePart.filename();
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return uploadBytes(bytes, filename, folder);
                });
    }

    public Mono<Map<String, Object>> uploadBytes(byte[] bytes, String filename, String folder) {
        return Mono.fromCallable(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "resource_type", "raw",
                    "folder", folder,
                    "public_id", filename,
                    "overwrite", true
            ));
            log.info("Uploaded to Cloudinary: {} → {}", filename, result.get("secure_url"));
            return result;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes a resource from Cloudinary.
     *
     * @param publicId The Cloudinary public_id of the resource
     * @return A Mono signaling completion
     */
    public Mono<Void> deleteFile(String publicId) {
        return Mono.fromCallable(() -> {
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            log.info("Deleted from Cloudinary: {}", publicId);
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
