package dev.danyil.songs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dev.danyil.enums.SongStatus;
import dev.danyil.releases.ReleaseEntity;
import dev.danyil.songs.exceptions.SongFileNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // 0-based order inside the release; the UI shows position + 1 as the track number
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

    // Explicit lyrics marking, required by distributors for every track
    @Column(nullable = false)
    private boolean explicit = false;

    @Column(columnDefinition = "text")
    private String lyrics;

    @Column(nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt = Instant.now();

    // Newest first: the latest mix is usually the one people look for
    @OneToMany(mappedBy = "song", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt DESC, id DESC")
    @Setter(AccessLevel.NONE)
    private List<SongFileEntity> files = new ArrayList<>();

    /**
     * The file marked with a heart. A reference rather than a flag on each file, so the
     * database itself guarantees at most one.
     *
     * ON DELETE SET NULL is required: Hibernate deletes orphaned rows before it runs updates,
     * so when the primary file is removed, its row goes while this column still points at it.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "primary_file_id",
        foreignKey = @ForeignKey(
            name = "fk_songs_primary_file",
            foreignKeyDefinition = "FOREIGN KEY (primary_file_id) REFERENCES song_files (id) ON DELETE SET NULL"))
    @Setter(AccessLevel.NONE)
    private SongFileEntity primaryFile;

    @Builder 
    public SongEntity(String title, Integer durationSeconds, String isrc, SongStatus status, String lyrics, boolean explicit) {
        this.title = title;
        this.durationSeconds = durationSeconds;
        this.isrc = isrc;
        this.status = status != null ? status : SongStatus.WRITING;
        this.lyrics = lyrics;
        this.explicit = explicit;
    }

    /** Adds a file at the top of the list; the first file of a song becomes its primary one. */
    public SongFileEntity addFile(SongFileEntity file) {
        file.setSong(this);
        files.add(0, file);
        if (primaryFile == null) primaryFile = file;
        return file;
    }

    /** Removes a file; if it was the primary one, the song is left without a primary file. */
    public void removeFile(SongFileEntity file) {
        if (files.remove(file) && file == primaryFile) primaryFile = null;
    }

    /** Marks one of this song's files with the heart, or clears it with null. */
    public void setPrimaryFile(SongFileEntity file) {
        if (file != null && !files.contains(file)) throw new SongFileNotFoundException(file.getId());
        primaryFile = file;
    }
}