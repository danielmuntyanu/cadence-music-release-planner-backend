package dev.danyil.songs;

import java.time.Instant;

import dev.danyil.enums.SongStatus;
import dev.danyil.releases.ReleaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;


/**
 * A song on a release. Its order is owned by ReleaseEntity (addSong / removeSong / reorderSongs),
 * so positions always run 0, 1, 2… without gaps.
 *
 * No unique constraint on (release_id, position): reordering updates rows one by one,
 * and PostgreSQL checks uniqueness after every statement, so swapping two tracks would fail.
 */
@Entity 
@Table(name = "songs", indexes = @Index(name = "idx_songs_release", columnList = "release_id"))
@Getter
@Setter
@NoArgsConstructor 
public class SongEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false)
    private ReleaseEntity release;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false)
    private int position;

    // Length in whole seconds; null until the song is finished
    private Integer durationSeconds;

    // International Standard Recording Code, normalized: 12 characters, uppercase, no hyphens.
    // Not unique: the same recording can appear on a single and later on an album
    @Column(length = 12)
    private String isrc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SongStatus status = SongStatus.WRITING;

    @Column(columnDefinition = "text")
    private String lyrics;

    // Explicit lyrics marking, required by distributors for every track
    @Column(nullable = false)
    private boolean explicit = false;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt = Instant.now();

    @Builder 
    public SongEntity(String title, Integer durationSeconds, String isrc, SongStatus status, String lyrics, boolean explicit) {
        this.title = title;
        this.durationSeconds = durationSeconds;
        this.isrc = isrc;
        this.status = status != null ? status : SongStatus.WRITING;
        this.lyrics = lyrics;
        this.explicit = explicit;
    }

}
