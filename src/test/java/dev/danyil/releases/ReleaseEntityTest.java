package dev.danyil.releases;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.enums.Genre;
import dev.danyil.enums.ReleasePhase;
import dev.danyil.enums.ReleaseType;
import dev.danyil.release_artists.ReleaseArtistEntity;

class ReleaseEntityTest {

    private ArtistEntity nova;
    private ArtistEntity ada;
    private ArtistEntity kai;
    private ReleaseEntity release;

    @BeforeEach
    void setUp() {
        nova = artist(4200L, "Nova");
        ada = artist(4300L, "Ada");
        kai = artist(4400L, "Kai");

        release = ReleaseEntity.builder()
            .name("Night Drive")
            .type(ReleaseType.EP)
            .primaryGenre(Genre.POP)
            .build();
    }

    private static ArtistEntity artist(long id, String name) {
        ArtistEntity artist = new ArtistEntity();
        artist.setId(id);
        artist.setName(name);
        return artist;
    }

    private List<String> artistNames() {
        return release.getArtists().stream().map(link -> link.getArtist().getName()).toList();
    }

    private List<Integer> positions() {
        return release.getArtists().stream().map(ReleaseArtistEntity::getPosition).toList();
    }

    private ReleaseArtistEntity linkOf(ArtistEntity artist) {
        return release.getArtists().stream()
            .filter(link -> link.getArtist() == artist)
            .findFirst()
            .orElseThrow();
    }

    // ---------- builder ----------

    @Test
    void builder_withoutPhase_defaultsToPlanning() {
        assertThat(release.getPhase(), is(ReleasePhase.PLANNING));
    }

    @Test
    void builder_startsWithEmptyArtistList() {
        // replaceArtists relies on a non-null list
        assertThat(release.getArtists(), is(notNullValue()));
        assertThat(release.getArtists(), is(empty()));
    }

    // ---------- replaceArtists ----------

    @Test
    void replaceArtists_onEmptyRelease_addsLinksInGivenOrder() {
        release.replaceArtists(List.of(ada, nova));

        assertThat(artistNames(), contains("Ada", "Nova"));
        assertThat(positions(), contains(0, 1));
    }

    @Test
    void replaceArtists_newLink_pointsBackToThisRelease() {
        release.replaceArtists(List.of(nova));

        assertThat(release.getArtists().get(0).getRelease(), is(sameInstance(release)));
    }

    @Test
    void replaceArtists_reorder_keepsExistingLinkInstancesAndUpdatesPositions() {
        release.replaceArtists(List.of(nova, ada));
        ReleaseArtistEntity novaLink = linkOf(nova);
        ReleaseArtistEntity adaLink = linkOf(ada);

        release.replaceArtists(List.of(ada, nova));

        // Same instances: an UPDATE of position, not DELETE + INSERT,
        // which would violate the (release_id, artist_id) unique constraint on flush
        assertThat(linkOf(nova), is(sameInstance(novaLink)));
        assertThat(linkOf(ada), is(sameInstance(adaLink)));
        assertThat(artistNames(), contains("Ada", "Nova"));
        assertThat(positions(), contains(0, 1));
    }

    @Test
    void replaceArtists_mixedChange_removesMissingKeepsRemainingAndAddsNew() {
        release.replaceArtists(List.of(nova, ada));
        ReleaseArtistEntity adaLink = linkOf(ada);

        release.replaceArtists(List.of(ada, kai));

        assertThat(artistNames(), contains("Ada", "Kai"));
        assertThat(positions(), contains(0, 1));
        assertThat(linkOf(ada), is(sameInstance(adaLink)));
    }

    @Test
    void replaceArtists_emptyList_removesAllLinks() {
        release.replaceArtists(List.of(nova, ada));

        release.replaceArtists(List.of());

        assertThat(release.getArtists(), is(empty()));
    }

    @Test
    void builder_withoutType_defaultsToSingle() {
        ReleaseEntity withoutType = ReleaseEntity.builder()
            .name("Night Drive")
            .primaryGenre(Genre.POP)
            .build();

        assertThat(withoutType.getType(), is(ReleaseType.SINGLE));
    }
}