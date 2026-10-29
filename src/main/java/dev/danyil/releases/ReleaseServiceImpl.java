package dev.danyil.releases;

import static dev.danyil.releases.ReleaseSpecifications.hasArtist;
import static dev.danyil.releases.ReleaseSpecifications.inGenres;
import static dev.danyil.releases.ReleaseSpecifications.inPhases;
import static dev.danyil.releases.ReleaseSpecifications.inTypes;
import static dev.danyil.releases.ReleaseSpecifications.nameContains;
import static dev.danyil.releases.ReleaseSpecifications.ownedBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import dev.danyil.artists.ArtistEntity;
import dev.danyil.artists.ArtistRepository;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.contracts.ReleaseService;
import dev.danyil.contracts.StorageService;
import dev.danyil.mappers.ReleaseMapper;
import dev.danyil.media.ImageKind;
import dev.danyil.media.ImageUploadService;
import dev.danyil.media.MediaKeys;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseDatesRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleaseGenresRequestDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import dev.danyil.releases.exceptions.ArtworkNotFoundException;
import dev.danyil.releases.exceptions.ReleaseAlreadyExistsException;
import dev.danyil.releases.exceptions.ReleaseException;
import dev.danyil.storage.DownloadNames;
import dev.danyil.storage.StorageProperties;
import dev.danyil.storage.dtos.DownloadLinkDTO;
import dev.danyil.users.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReleaseServiceImpl implements ReleaseService {

    private final ReleaseMapper releaseMapper;
    private final ReleaseRepository releaseRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final ImageUploadService imageUploads;
    private final ReleaseFinder releaseFinder;
    private final StorageService storage;
    private final StorageProperties storageProperties;

    // Enums are stored as strings, so sorting by phase or type would be alphabetical
    // (CATALOG, PLANNING, PRE_RELEASE…), not the lifecycle order; they are filters only.
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "releaseDate", "createdAt");

    @Override
    public Page<ReleaseResponseDTO> getAll(Long userId, ReleaseFilterDTO filter, Pageable pageable) {
        validateSort(pageable);
        return releaseRepository.findAll(buildSpecification(userId, filter), pageable)
            .map(releaseMapper::toResponse);
    }

    @Override
    public ReleaseResponseDTO getById(Long userId, Long id) {
        return releaseMapper.toResponse(findOwned(userId, id));
    }

    @Override
    @Transactional
    public ReleaseResponseDTO create(ReleaseCreateRequestDTO dto, Long userId) {
        String name = dto.name().trim();
        if (releaseRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ReleaseAlreadyExistsException(name);
        }

        ReleaseEntity release = releaseMapper.toEntity(dto);
        release.setUser(userRepository.getReferenceById(userId));
        release.replaceArtists(loadOwnedArtistsInOrder(dto.artistIds(), userId));

        return releaseMapper.toResponse(releaseRepository.save(release));
    }

    @Override
    @Transactional
    public ReleaseResponseDTO update(Long userId, Long id, ReleaseUpdateRequestDTO dto) {
        ReleaseEntity release = findOwned(userId, id);

        if (dto.name() != null) {
            String name = dto.name().trim();
            if (releaseRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
                throw new ReleaseAlreadyExistsException(name);
            }
        }

        releaseMapper.applyUpdate(release, dto);
        if (dto.artistIds() != null) {
            release.replaceArtists(loadOwnedArtistsInOrder(dto.artistIds(), userId));
        }

        return releaseMapper.toResponse(release);
    }

    @Override
    @Transactional
    public ReleaseResponseDTO updatePhase(Long userId, Long id, ReleasePhaseUpdateRequestDTO dto) {
        ReleaseEntity release = findOwned(userId, id);
        release.setPhase(dto.phase());
        return releaseMapper.toResponse(release);
    }

    @Override
    @Transactional
    public ReleaseResponseDTO updateGenres(Long userId, Long id, ReleaseGenresRequestDTO request) {
        ReleaseEntity release = findOwned(userId, id);

        // The pair is validated on the DTO; both values replace the current ones
        release.setPrimaryGenre(request.primaryGenre());
        release.setSecondaryGenre(request.secondaryGenre());

        return releaseMapper.toResponse(release);
    }

    @Override
    @Transactional
    public ReleaseResponseDTO updateDates(Long userId, Long id, ReleaseDatesRequestDTO request) {
        ReleaseEntity release = findOwned(userId, id);

        // null is a real value here: it clears the date
        release.setReleaseDate(request.releaseDate());
        release.setOriginalReleaseDate(request.originalReleaseDate());

        return releaseMapper.toResponse(release);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        ReleaseEntity release = findOwned(userId, id);

        // Rows go by cascade; files in storage have to be removed explicitly, after commit
        deleteArtworkFiles(release);
        release.getSongs().stream()
            .flatMap(song -> song.getFiles().stream())
            .forEach(file -> imageUploads.deleteAfterCommit(file.getStorageKey()));

        releaseRepository.delete(release);
    }

    /**
     * Keeps two copies: the resized one the app shows, and the file exactly as uploaded,
     * which is what distributors need (usually 3000 × 3000).
     */
    @Override
    @Transactional
    public ReleaseResponseDTO uploadArtwork(Long userId, Long id, MultipartFile file) {
        ReleaseEntity release = findOwned(userId, id);
        String prefix = MediaKeys.releaseArtwork(userId, id);

        String displayKey = imageUploads.store(prefix, ImageKind.ARTWORK, file);
        String originalKey = imageUploads.storeOriginal(prefix, file);

        deleteArtworkFiles(release);
        release.setArtworkKey(displayKey);
        release.setArtworkOriginalKey(originalKey);
        release.setArtworkUrl(null);

        return releaseMapper.toResponse(release);
    }

    @Override
    @Transactional
    public ReleaseResponseDTO removeArtwork(Long userId, Long id) {
        ReleaseEntity release = findOwned(userId, id);

        deleteArtworkFiles(release);
        release.setArtworkUrl(null);

        return releaseMapper.toResponse(release);
    }

    @Override
    public DownloadLinkDTO artworkDownloadLink(Long userId, Long id) {
        ReleaseEntity release = findOwned(userId, id);

        // Covers uploaded before originals were kept only have the resized copy
        String key = release.getArtworkOriginalKey() != null
            ? release.getArtworkOriginalKey()
            : release.getArtworkKey();
        if (key == null) throw new ArtworkNotFoundException(id);

        Duration ttl = storageProperties.downloadUrlTtl();
        String name = DownloadNames.of(release.getName() + " - cover", DownloadNames.extensionOf(key));
        return new DownloadLinkDTO(storage.presignDownload(key, name, ttl), Instant.now().plus(ttl));
    }

    // HELPERS

    private ReleaseEntity findOwned(Long userId, Long id) {
        return releaseFinder.findOwned(userId, id);
    }

    /** Both copies of the cover go together: the resized one and the original. */
    private void deleteArtworkFiles(ReleaseEntity release) {
        imageUploads.deleteAfterCommit(release.getArtworkKey());
        imageUploads.deleteAfterCommit(release.getArtworkOriginalKey());
        release.setArtworkKey(null);
        release.setArtworkOriginalKey(null);
    }

    private Specification<ReleaseEntity> buildSpecification(Long userId, ReleaseFilterDTO filter) {
        // Ownership is always applied first and is never optional
        Specification<ReleaseEntity> spec = ownedBy(userId);

        if (filter.phase() != null && !filter.phase().isEmpty()) spec = spec.and(inPhases(filter.phase()));
        if (filter.type() != null && !filter.type().isEmpty()) spec = spec.and(inTypes(filter.type()));
        if (filter.genre() != null && !filter.genre().isEmpty()) spec = spec.and(inGenres(filter.genre()));
        if (filter.artistId() != null) spec = spec.and(hasArtist(filter.artistId()));
        if (StringUtils.hasText(filter.q())) spec = spec.and(nameContains(filter.q().trim()));

        return spec;
    }

    // Loads the user's artists with one query and returns them in the requested order.
    private List<ArtistEntity> loadOwnedArtistsInOrder(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) return List.of();

        Map<Long, ArtistEntity> found = new HashMap<>();
        for (ArtistEntity artist : artistRepository.findAllByIdInAndUserId(ids, userId)) {
            found.put(artist.getId(), artist);
        }

        List<ArtistEntity> ordered = new ArrayList<>();
        for (Long id : ids) {
            ArtistEntity artist = found.get(id);
            // Missing means it does not exist or belongs to another user: both are "not found" here
            if (artist == null) throw new ArtistNotFoundException(id);
            ordered.add(artist);
        }
        return ordered;
    }

    private void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ReleaseException("Cannot sort by '" + order.getProperty() + "'");
            }
        });
    }
}