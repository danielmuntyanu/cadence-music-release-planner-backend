package dev.danyil.artists;

import java.util.HashSet;
import java.util.Set;

import dev.danyil.users.UserEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
    name = "artists", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
public class ArtistEntity {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 512)
    private String avatarUrl;

    @Column(length = 512)
    private String bannerUrl;

    @Column(length = 512)
    private String description;

    @Column(length = 50)
    private String genre;

    @ElementCollection
    @CollectionTable(
        name = "artist_socials",
        joinColumns = @JoinColumn(name = "artist_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"artist_id", "platform"})
    )
    private Set<Social> socials = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

}
