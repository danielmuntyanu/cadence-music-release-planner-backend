package dev.danyil.releases;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.annotations.CreationTimestamp;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import dev.danyil.release_artists.ReleaseArtistEntity;
import dev.danyil.users.UserEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(name = "releases")
@Getter 
@Setter 
@NoArgsConstructor 
public class ReleaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleasePhase phase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleaseType type;

    @Column(length = 512)
    private String artworkUrl;

    // Uploaded cover in private storage; wins over artworkUrl (external link or seed data)
    @Column(length = 512)
    private String artworkKey;

    @Column(length = 1024)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genre primaryGenre;

    @Enumerated(EnumType.STRING)
    private Genre secondaryGenre;

    private LocalDate releaseDate;

    private LocalDate originalReleaseDate;

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ReleaseArtistEntity> artists = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Builder 
    public ReleaseEntity(String name, ReleasePhase phase, ReleaseType type, String artworkUrl, String notes, Genre primaryGenre, Genre secondaryGenre,
            LocalDate releaseDate, LocalDate originalReleaseDate, List<ReleaseArtistEntity> artists) {
        this.name = name;
        this.phase = phase != null ? phase : ReleasePhase.PLANNING;
        this.type = type != null ? type : ReleaseType.SINGLE;
        this.artworkUrl = artworkUrl;
        this.notes = notes;
        this.primaryGenre = primaryGenre;
        this.secondaryGenre = secondaryGenre;
        this.releaseDate = releaseDate;
        this.originalReleaseDate = originalReleaseDate;
    }

    public void replaceArtists(List<ArtistEntity> ordered) {
        Map<Long, ReleaseArtistEntity> existing = artists.stream()
            .collect(Collectors.toMap(link -> link.getArtist().getId(), link -> link));

        Set<Long> wanted = ordered.stream().map(ArtistEntity::getId).collect(Collectors.toSet());

        // Removed from the list: orphanRemoval deletes the link row
        artists.removeIf(link -> !wanted.contains(link.getArtist().getId()));

        for (int position = 0; position < ordered.size(); position++) {
            ArtistEntity artist = ordered.get(position);
            ReleaseArtistEntity link = existing.get(artist.getId());

            if (link != null) link.setPosition(position);
            else artists.add(new ReleaseArtistEntity(this, artist, position));
        }

        artists.sort(Comparator.comparingInt(ReleaseArtistEntity::getPosition));
    }

}
