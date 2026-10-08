package dev.danyil.users;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_profiles")
@Getter 
@Setter 
public class UserProfileEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private Long id; // same as in UserEntity

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(length = 100)
    private String displayName;

    @Column(length = 500)
    private String bio;

    private String avatarUrl;

}