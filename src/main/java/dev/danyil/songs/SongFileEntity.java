package dev.danyil.songs;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One audio file of a song: a demo, a mix, a master. The bytes live in private storage;
 * this row only knows where, and the name the user gave it.
 */
@Entity
@Table(name = "song_files", indexes = @Index(name = "idx_song_files_song", columnList = "song_id"))
@Getter
@Setter
@NoArgsConstructor
public class SongFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private SongEntity song;

    // users/{userId}/song-files/{uuid}.{ext}: independent of the song and release, so a file never moves.
    // Unique: one upload can be confirmed only once
    @Column(nullable = false, unique = true, length = 255)
    @Setter(AccessLevel.NONE)
    private String storageKey;

    // The only field a user can change after the upload
    @Column(nullable = false, length = 150)
    private String displayName;

    @Column(nullable = false, length = 255)
    @Setter(AccessLevel.NONE)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    @Setter(AccessLevel.NONE)
    private String contentType;

    @Setter(AccessLevel.NONE)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant uploadedAt = Instant.now();

    @Builder
    public SongFileEntity(String storageKey, String displayName, String originalFilename,
            String contentType, long sizeBytes) {
        this.storageKey = storageKey;
        this.displayName = displayName;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
}