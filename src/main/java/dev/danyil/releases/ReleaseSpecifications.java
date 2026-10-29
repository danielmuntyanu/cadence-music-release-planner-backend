package dev.danyil.releases;

import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import dev.danyil.release_artists.ReleaseArtistEntity;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Reusable query predicates for releases; ReleaseServiceImpl combines them per request. */
public final class ReleaseSpecifications {

    private ReleaseSpecifications() {}

    public static Specification<ReleaseEntity> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<ReleaseEntity> inPhases(Set<ReleasePhase> phases) {
        return (root, query, cb) -> root.get("phase").in(phases);
    }

    public static Specification<ReleaseEntity> inTypes(Set<ReleaseType> types) {
        return (root, query, cb) -> root.get("type").in(types);
    }

    /** Matches the primary or the secondary genre. */
    public static Specification<ReleaseEntity> inGenres(Set<Genre> genres) {
        return (root, query, cb) -> cb.or(
            root.get("primaryGenre").in(genres),
            root.get("secondaryGenre").in(genres));
    }

    /**
     * EXISTS instead of JOIN: a join would also narrow the release's artist collection,
     * so "Nova & Ada" filtered by Nova would come back with Nova only.
     */
    public static Specification<ReleaseEntity> hasArtist(Long artistId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ReleaseArtistEntity> link = sub.from(ReleaseArtistEntity.class);
            sub.select(link.get("id")).where(
                cb.equal(link.get("release"), root),
                cb.equal(link.get("artist").get("id"), artistId));
            return cb.exists(sub);
        };
    }

    public static Specification<ReleaseEntity> nameContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + text.toLowerCase() + "%");
    }
}