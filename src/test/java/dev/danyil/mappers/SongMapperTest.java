package dev.danyil.mappers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.danyil.enums.SongStatus;
import dev.danyil.songs.SongEntity;
import dev.danyil.songs.SongFileEntity;
import dev.danyil.songs.dtos.SongRequestDTO;
import dev.danyil.songs.dtos.SongResponseDTO;

class SongMapperTest {

    private final SongMapper mapper = new SongMapper();

    private static SongRequestDTO request(String title, Integer duration, String isrc, String lyrics, boolean explicit) {
        return new SongRequestDTO(title, duration, isrc, SongStatus.RECORDING, lyrics, explicit);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "US-ABC-26-00001, USABC2600001",
        "us-abc-26-00001, USABC2600001",
        "USABC2600001,    USABC2600001",
        "'  usabc2600001 ', USABC2600001",
    })
    void normalizeIsrc_removesHyphensAndUppercases(String input, String expected) {
        assertThat(SongMapper.normalizeIsrc(input), is(expected));
    }

    @Test
    void normalizeIsrc_blankOrNull_isNull() {
        assertThat(SongMapper.normalizeIsrc(""), is(nullValue()));
        assertThat(SongMapper.normalizeIsrc("   "), is(nullValue()));
        assertThat(SongMapper.normalizeIsrc(null), is(nullValue()));
    }

    @Test
    void toEntity_trimsTitle_normalizesIsrc_andDropsBlankLyrics() {
        SongEntity song = mapper.toEntity(request("  Night Drive ", 215, "us-abc-26-00001", "   ", true));

        assertThat(song.getTitle(), is("Night Drive"));
        assertThat(song.getDurationSeconds(), is(215));
        assertThat(song.getIsrc(), is("USABC2600001"));
        assertThat(song.getLyrics(), is(nullValue()));
        assertThat(song.getStatus(), is(SongStatus.RECORDING));
        assertThat(song.isExplicit(), is(true));
    }

    @Test
    void replace_nullFields_clearTheOptionalValues() {
        SongEntity song = mapper.toEntity(request("Night Drive", 215, "USABC2600001", "Lyrics", true));

        mapper.replace(song, request("Night Drive (Edit)", null, null, null, false));

        assertThat(song.getTitle(), is("Night Drive (Edit)"));
        assertThat(song.getDurationSeconds(), is(nullValue()));
        assertThat(song.getIsrc(), is(nullValue()));
        assertThat(song.getLyrics(), is(nullValue()));
        assertThat(song.isExplicit(), is(false));
    }

    @Test
    void toResponse_showsAOneBasedTrackNumber() {
        SongEntity song = mapper.toEntity(request("Night Drive", null, null, null, false));
        song.setPosition(2);

        SongResponseDTO response = mapper.toResponse(song);

        assertThat(response.trackNumber(), is(3));
    }

    @Test
    void missingExplicitFlag_meansNotExplicit() {
        SongEntity song = mapper.toEntity(new SongRequestDTO("Intro", null, null, SongStatus.WRITING, null, null));

        assertThat(song.isExplicit(), is(false));
    }

    @Test
    void toResponse_songWithoutFiles_hasNoPrimaryAndZeroCount() {
        SongResponseDTO response = mapper.toResponse(mapper.toEntity(request("Intro", null, null, null, false)));

        assertThat(response.primaryFileId(), is(nullValue()));
        assertThat(response.fileCount(), is(0));
    }

    @Test
    void toResponse_reportsThePrimaryFileAndHowManyFilesThereAre() {
        SongEntity song = mapper.toEntity(request("Intro", null, null, null, false));
        for (long id = 8100L; id <= 8300L; id += 100) {
            SongFileEntity file = SongFileEntity.builder()
                .storageKey("users/1/song-files/" + id + ".wav").displayName("take " + id)
                .originalFilename("take.wav").contentType("audio/wav").sizeBytes(1).build();
            file.setId(id);
            song.addFile(file);
        }

        SongResponseDTO response = mapper.toResponse(song);

        // The first file added became primary
        assertThat(response.primaryFileId(), is(8100L));
        assertThat(response.fileCount(), is(3));
    }
}