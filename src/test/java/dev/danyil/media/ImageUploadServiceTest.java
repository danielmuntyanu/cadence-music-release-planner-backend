package dev.danyil.media;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import dev.danyil.contracts.StorageService;
import dev.danyil.storage.exceptions.StorageException;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, 1, 2 };

    @Mock
    private ImageProcessor processor;

    @Mock
    private StorageService storage;

    @InjectMocks
    private ImageUploadService imageUploads;

    private final MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", new byte[] { 1, 2, 3 });

    @BeforeEach
    void givenProcessorAcceptsTheFile() {
        lenient()
            .when(processor.process(any(), any()))
            .thenReturn(new ProcessedImage(JPEG, "image/jpeg", ".jpg"));
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** Simulates the end of a transaction by running the registered callbacks. */
    private static void completeTransaction(boolean committed) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (committed) synchronizations.forEach(TransactionSynchronization::afterCommit);
        int status = committed ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK;
        synchronizations.forEach(sync -> sync.afterCompletion(status));
    }

    // ---------- store ----------

    @Test
    void store_uploadsTheProcessedImageUnderAUniqueKey() {
        String key = imageUploads.store("users/7/releases/42/artwork", ImageKind.ARTWORK, file);

        ArgumentCaptor<String> uploadedKey = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(uploadedKey.capture(), eq(JPEG), eq("image/jpeg"));
        assertThat(key, matchesPattern("users/7/releases/42/artwork-[0-9a-f-]{36}\\.jpg"));
        assertThat(uploadedKey.getValue(), matchesPattern(key));
    }

    @Test
    void store_emptyFile_isRejectedWithoutUploading() {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);

        assertThrows(InvalidImageException.class, () -> imageUploads.store("prefix", ImageKind.AVATAR, empty));

        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void store_unreadableFile_isRejected() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getBytes()).thenThrow(new IOException("disk"));

        assertThrows(InvalidImageException.class, () -> imageUploads.store("prefix", ImageKind.AVATAR, broken));
    }

    @Test
    void store_rolledBackTransaction_deletesTheNewFile() {
        TransactionSynchronizationManager.initSynchronization();
        String key = imageUploads.store("prefix", ImageKind.AVATAR, file);

        completeTransaction(false);

        verify(storage).delete(key);
    }

    @Test
    void store_committedTransaction_keepsTheNewFile() {
        TransactionSynchronizationManager.initSynchronization();
        imageUploads.store("prefix", ImageKind.AVATAR, file);

        completeTransaction(true);

        verify(storage, never()).delete(anyString());
    }

    // ---------- storeOriginal ----------

    @Test
    void storeOriginal_uploadsTheBytesUnchanged_withTheDetectedType() {
        byte[] original = { 1, 2, 3, 4 };
        MockMultipartFile upload = new MockMultipartFile("file", "cover.png", "image/png", original);
        when(processor.inspect(original)).thenReturn(ImageFormat.PNG);

        String key = imageUploads.storeOriginal("users/1/releases/5/artwork", upload);

        assertThat(key, startsWith("users/1/releases/5/artwork-original-"));
        assertThat(key, endsWith(".png"));
        verify(storage).upload(key, original, "image/png");
        // The original is never resized or re-encoded
        verify(processor, never()).process(any(), any());
    }

    @Test
    void storeOriginal_trustsTheContent_notTheFileName() {
        byte[] original = { 1, 2, 3 };
        // Named .png, but the content is a JPEG
        MockMultipartFile upload = new MockMultipartFile("file", "cover.png", "image/png", original);
        when(processor.inspect(original)).thenReturn(ImageFormat.JPEG);

        String key = imageUploads.storeOriginal("prefix", upload);

        assertThat(key, endsWith(".jpg"));
        verify(storage).upload(key, original, "image/jpeg");
    }

    @Test
    void storeOriginal_invalidImage_uploadsNothing() {
        byte[] original = { 1, 2, 3 };
        MockMultipartFile upload = new MockMultipartFile("file", "cover.png", "image/png", original);
        when(processor.inspect(original)).thenThrow(new InvalidImageException("The file is not an image"));

        assertThrows(InvalidImageException.class, () -> imageUploads.storeOriginal("prefix", upload));

        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void storeOriginal_emptyFile_isRejectedBeforeInspecting() {
        MockMultipartFile empty = new MockMultipartFile("file", "cover.png", "image/png", new byte[0]);

        assertThrows(InvalidImageException.class, () -> imageUploads.storeOriginal("prefix", empty));

        verify(processor, never()).inspect(any());
    }

    @Test
    void storeOriginal_rolledBackTransaction_deletesTheNewFile() {
        byte[] original = { 1, 2, 3 };
        MockMultipartFile upload = new MockMultipartFile("file", "cover.png", "image/png", original);
        when(processor.inspect(original)).thenReturn(ImageFormat.PNG);
        TransactionSynchronizationManager.initSynchronization();

        String key = imageUploads.storeOriginal("prefix", upload);
        completeTransaction(false);

        verify(storage).delete(key);
    }

    // ---------- deleteAfterCommit ----------

    @Test
    void deleteAfterCommit_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        imageUploads.deleteAfterCommit("old.jpg");
        verify(storage, never()).delete(anyString());

        completeTransaction(true);
        verify(storage).delete("old.jpg");
    }

    @Test
    void deleteAfterCommit_rolledBack_keepsTheOldFile() {
        TransactionSynchronizationManager.initSynchronization();

        imageUploads.deleteAfterCommit("old.jpg");
        completeTransaction(false);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void deleteAfterCommit_withoutTransaction_deletesImmediately() {
        imageUploads.deleteAfterCommit("old.jpg");

        verify(storage).delete("old.jpg");
    }

    @Test
    void deleteAfterCommit_nullKey_doesNothing() {
        imageUploads.deleteAfterCommit(null);

        verify(storage, never()).delete(anyString());
    }

    @Test
    void deleteAfterCommit_storageFailure_doesNotFailTheRequest() {
        doThrow(new StorageException("down", null)).when(storage).delete("old.jpg");

        assertDoesNotThrow(() -> imageUploads.deleteAfterCommit("old.jpg"));
    }
}