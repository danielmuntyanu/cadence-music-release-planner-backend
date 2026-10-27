package dev.danyil.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.danyil.contracts.StorageService;
import dev.danyil.songs.SongEntity;
import dev.danyil.songs.SongFileEntity;
import dev.danyil.songs.dtos.SongFileResponseDTO;

class SongFileMapperTest {

    private StorageService storage;
    private SongFileMapper mapper;
    private SongEntity song;

    @BeforeEach
    void setUp() {
        storage = mock(StorageService.class);
        mapper = new SongFileMapper(storage);
        song = SongEntity.builder().title("Night Drive").build();
    }

    private SongFileEntity addFile(long id, String key, String name) {
        SongFileEntity file = SongFileEntity.builder()
            .storageKey(key)
            .displayName(name)
            .originalFilename("Night Drive " + name + ".wav")
            .contentType("audio/wav")
            .sizeBytes(84_000_000L)
            .build();
        file.setId(id);
        return song.addFile(file);
    }

    @Test
    void mapsTheFileWithAFreshSignedUrl() {
        SongFileEntity file = addFile(8100L, "users/1/song-files/a.wav", "mix3");
        when(storage.signedReadUrl("users/1/song-files/a.wav")).thenReturn("https://signed.test/a.wav");

        SongFileResponseDTO response = mapper.toResponse(file);

        assertThat(response.id(), is(8100L));
        assertThat(response.displayName(), is("mix3"));
        assertThat(response.originalFilename(), is("Night Drive mix3.wav"));
        assertThat(response.contentType(), is("audio/wav"));
        assertThat(response.sizeBytes(), is(84_000_000L));
        assertThat(response.url(), is("https://signed.test/a.wav"));
    }

    @Test
    void marksOnlyThePrimaryFile() {
        SongFileEntity demo = addFile(8100L, "users/1/song-files/a.wav", "demo");
        SongFileEntity mix = addFile(8200L, "users/1/song-files/b.wav", "mix3");

        assertThat(mapper.toResponse(demo).primary(), is(true));
        assertThat(mapper.toResponse(mix).primary(), is(false));
    }
}