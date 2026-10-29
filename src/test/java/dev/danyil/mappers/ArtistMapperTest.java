package dev.danyil.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.artists.Social;
import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialRequestDTO;
import dev.danyil.artists.dtos.SocialResponseDTO;
import dev.danyil.enums.SocialPlatform;
import dev.danyil.users.UserEntity;

class ArtistMapperTest {

    private final ArtistMapper mapper = new ArtistMapper(key -> "signed:" + key);

    @Test
    void toEntity_trimsValuesAndTurnsBlankOptionalFieldsIntoNull() {
        UserEntity owner = new UserEntity();
        ArtistCreateRequestDTO request = new ArtistCreateRequestDTO(
            "  Nova  ", "   ", null, "", "  Synthwave  ",
            List.of(new SocialRequestDTO(SocialPlatform.SPOTIFY, "  https://open.spotify.com/artist/nova  ")));

        ArtistEntity entity = mapper.toEntity(request, owner);

        assertThat(entity.getName(), is("Nova"));
        assertThat(entity.getAvatarUrl(), is(nullValue()));
        assertThat(entity.getBannerUrl(), is(nullValue()));
        assertThat(entity.getDescription(), is(nullValue()));
        assertThat(entity.getGenre(), is("Synthwave"));
        assertThat(entity.getUser(), is(sameInstance(owner)));
        assertThat(entity.getSocials(),
            contains(new Social(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/nova")));
    }

    @Test
    void toEntity_nullSocials_createsEmptyCollection() {
        ArtistCreateRequestDTO request = new ArtistCreateRequestDTO("Nova", null, null, null, null, null);

        ArtistEntity entity = mapper.toEntity(request, new UserEntity());

        assertThat(entity.getSocials(), is(empty()));
    }

    @Test
    void toResponse_sortsSocialsByPlatformOrder() {
        ArtistEntity artist = new ArtistEntity();
        artist.setName("Nova");
        artist.getSocials().addAll(Set.of(
            new Social(SocialPlatform.WEBSITE, "https://nova.com"),
            new Social(SocialPlatform.INSTAGRAM, "https://instagram.com/nova"),
            new Social(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/nova")));

        ArtistResponseDTO response = mapper.toResponse(artist);

        List<SocialPlatform> platforms = response.socials().stream()
            .map(SocialResponseDTO::platform)
            .toList();
        assertThat(platforms, contains(SocialPlatform.SPOTIFY, SocialPlatform.INSTAGRAM, SocialPlatform.WEBSITE));
    }

    @Test
    void applyUpdate_allFieldsNull_leavesEntityUnchanged() {
        ArtistEntity artist = new ArtistEntity();
        artist.setName("Nova");
        artist.setGenre("Synthwave");
        artist.getSocials().add(new Social(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/nova"));
        ArtistUpdateRequestDTO request = new ArtistUpdateRequestDTO(null, null, null, null, null, null);

        mapper.applyUpdate(request, artist);

        assertThat(artist.getName(), is("Nova"));
        assertThat(artist.getGenre(), is("Synthwave"));
        assertThat(artist.getSocials(),
            contains(new Social(SocialPlatform.SPOTIFY, "https://open.spotify.com/artist/nova")));
    }
}