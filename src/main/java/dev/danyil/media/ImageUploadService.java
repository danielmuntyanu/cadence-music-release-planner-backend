package dev.danyil.media;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import dev.danyil.contracts.StorageService;
import dev.danyil.storage.exceptions.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores processed images and keeps storage consistent with the database:
 * a new file is removed if the transaction rolls back, an old file only after it commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final ImageProcessor processor;
    private final StorageService storage;

    /** Processes and uploads the image; returns the new storage key. */
    public String store(String keyPrefix, ImageKind kind, MultipartFile file) {
        ProcessedImage image = processor.process(readBytes(file), kind);
        // A unique key per upload: a replaced image gets a new URL, so no stale browser cache
        String key = keyPrefix + "-" + UUID.randomUUID() + image.extension();

        storage.upload(key, image.content(), image.contentType());
        onRollback(() -> deleteQuietly(key));
        return key;
    }

    /** Deletes a no longer referenced file once the transaction has committed. No-op for null. */
    public void deleteAfterCommit(String key) {
        if (key == null) return;
        afterCommit(() -> deleteQuietly(key));
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidImageException("The file is empty");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("The file could not be read");
        }
    }

    // A leftover file is harmless; failing a request that already saved its data is not
    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (StorageException e) {
            log.warn("### Could not delete stored file {}", key, e);
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) action.run();
            }
        });
    }
}