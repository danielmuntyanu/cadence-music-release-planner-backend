package dev.danyil.release_artists;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.releases.ReleaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Table(
    name = "release_artists",
    uniqueConstraints = @UniqueConstraint(columnNames = {"release_id", "artist_id"}))
@Getter 
@Setter
@NoArgsConstructor
public class ReleaseArtistEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id")
    private ReleaseEntity release;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ArtistEntity artist;

    @Column(nullable = false)
    private int position;

    public ReleaseArtistEntity(ReleaseEntity release, ArtistEntity artist, int position) {
        this.release = release;
        this.artist = artist;
        this.position = position;
    }

}
