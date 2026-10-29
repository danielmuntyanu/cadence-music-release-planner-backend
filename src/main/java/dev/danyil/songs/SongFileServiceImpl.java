package dev.danyil.songs;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.contracts.SongFileService;
import dev.danyil.contracts.StorageService;
import dev.danyil.mappers.SongFileMapper;
import dev.danyil.media.ImageUploadService;
import dev.danyil.songs.dtos.SongFileConfirmRequestDTO;
import dev.danyil.songs.dtos.SongFileRenameRequestDTO;
import dev.danyil.songs.dtos.SongFileResponseDTO;
import dev.danyil.songs.dtos.SongFileUploadRequestDTO;
import dev.danyil.songs.dtos.SongFileUploadResponseDTO;
import dev.danyil.songs.dtos.SongPrimaryFileRequestDTO;
import dev.danyil.songs.exceptions.InvalidAudioFileException;
import dev.danyil.songs.exceptions.SongFileNotFoundException;
import dev.danyil.songs.exceptions.UploadNotFoundException;
import dev.danyil.storage.DownloadNames;
import dev.danyil.storage.StorageProperties;
import dev.danyil.storage.dtos.DownloadLinkDTO;
import dev.danyil.storage.dtos.PresignedUpload;
import dev.danyil.storage.dtos.StoredObjectInfo;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongFileServiceImpl implements SongFileService {

    private static final String ACCEPTED = "Upload WAV, AIFF, FLAC, MP3, M4A, AAC or OGG audio";

    private final SongFinder songFinder;
    private final SongFileRepository songFileRepository;
    private final StorageService storage;
    // Deletes a key only after the transaction commits; works for any media, not only images
    private final ImageUploadService mediaCleanup;
    private final SongFileMapper songFileMapper;
    private final AudioUploadProperties limits;
    private final StorageProperties storageProperties;

    @Override
    public List<SongFileResponseDTO> getAll(Long userId, Long releaseId, Long songId) {
        return songFinder.findOwned(userId, releaseId, songId).getFiles().stream()
            .map(songFileMapper::toResponse)
            .toList();
    }

    /** Step 1: checks the file before anything is uploaded, then signs a PUT for exactly it. */
    @Override
    public SongFileUploadResponseDTO startUpload(Long userId, Long releaseId, Long songId,
            SongFileUploadRequestDTO request) {
        songFinder.findOwned(userId, releaseId, songId);

        String extension = AudioFormats.extension(request.fileName())
            .orElseThrow(() -> new InvalidAudioFileException(ACCEPTED));
        checkSize(request.sizeBytes());

        String key = keyPrefix(userId) + UUID.randomUUID() + "." + extension;
        PresignedUpload upload = storage.presignPut(
            key, AudioFormats.contentType(extension), request.sizeBytes(), limits.uploadUrlTtl());

        return new SongFileUploadResponseDTO(key, upload.url(), upload.headers(), upload.expiresAt());
    }

    /**
     * Step 3: the browser says the upload finished. Nothing it sends is trusted: the key must
     * be one this user could have been given, and storage itself must confirm the object.
     */
    @Override
    @Transactional
    public SongFileResponseDTO confirmUpload(Long userId, Long releaseId, Long songId,
            SongFileConfirmRequestDTO request) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);

        // Another user's key would hand out a signed link to their audio
        if (!request.key().startsWith(keyPrefix(userId))) throw new UploadNotFoundException();
        // A second row for the same key: deleting one would delete the other's audio
        if (songFileRepository.existsByStorageKey(request.key())) throw new UploadNotFoundException();

        String extension = AudioFormats.extension(request.key())
            .orElseThrow(() -> new InvalidAudioFileException(ACCEPTED));
        // The size comes from storage, never from the request
        StoredObjectInfo stored = storage.describe(request.key()).orElseThrow(UploadNotFoundException::new);
        checkSize(stored.size());

        SongFileEntity file = song.addFile(SongFileEntity.builder()
            .storageKey(request.key())
            .displayName(displayName(request))
            .originalFilename(request.fileName().trim())
            .contentType(AudioFormats.contentType(extension))
            .sizeBytes(stored.size())
            .build());

        return songFileMapper.toResponse(songFileRepository.save(file));
    }

    @Override
    @Transactional
    public SongFileResponseDTO rename(Long userId, Long releaseId, Long songId, Long fileId,
            SongFileRenameRequestDTO request) {
        SongFileEntity file = findFile(songFinder.findOwned(userId, releaseId, songId), fileId);
        file.setDisplayName(request.displayName().trim());
        return songFileMapper.toResponse(file);
    }

    /** Returns the whole list: the heart moves, so two files change at once. */
    @Override
    @Transactional
    public List<SongFileResponseDTO> setPrimary(Long userId, Long releaseId, Long songId,
            SongPrimaryFileRequestDTO request) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);
        song.setPrimaryFile(request.fileId() == null ? null : findFile(song, request.fileId()));
        return song.getFiles().stream().map(songFileMapper::toResponse).toList();
    }

    /** Saved under the name the user gave the file, not the one it was uploaded with. */
    @Override
    public DownloadLinkDTO downloadLink(Long userId, Long releaseId, Long songId, Long fileId) {
        SongFileEntity file = findFile(songFinder.findOwned(userId, releaseId, songId), fileId);
        Duration ttl = storageProperties.downloadUrlTtl();

        String name = DownloadNames.of(file.getDisplayName(), DownloadNames.extensionOf(file.getStorageKey()));
        return new DownloadLinkDTO(storage.presignDownload(file.getStorageKey(), name, ttl), Instant.now().plus(ttl));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long releaseId, Long songId, Long fileId) {
        SongEntity song = songFinder.findOwned(userId, releaseId, songId);
        SongFileEntity file = findFile(song, fileId);

        song.removeFile(file);
        // Only after the row is gone for good: a rolled-back delete must keep its audio
        mediaCleanup.deleteAfterCommit(file.getStorageKey());
    }

    // HELPERS

    private static String keyPrefix(Long userId) {
        return "users/" + userId + "/song-files/";
    }

    private void checkSize(long sizeBytes) {
        if (sizeBytes <= 0) throw new InvalidAudioFileException("The file is empty");
        if (sizeBytes > limits.maxBytes()) {
            throw new InvalidAudioFileException(
                "Audio files must be at most " + limits.maxBytes() / (1024 * 1024) + " MB");
        }
    }

    private static String displayName(SongFileConfirmRequestDTO request) {
        String name = request.displayName() == null || request.displayName().isBlank()
            ? AudioFormats.nameWithoutExtension(request.fileName())
            : request.displayName().trim();
        return name.length() > 150 ? name.substring(0, 150) : name;
    }

    /** The file must belong to this song, not just to this user. */
    private static SongFileEntity findFile(SongEntity song, Long fileId) {
        return song.getFiles().stream()
            .filter(file -> file.getId().equals(fileId))
            .findFirst()
            .orElseThrow(() -> new SongFileNotFoundException(fileId));
    }
}