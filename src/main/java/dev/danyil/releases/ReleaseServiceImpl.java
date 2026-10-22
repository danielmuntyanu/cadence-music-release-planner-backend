package dev.danyil.releases;

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

import dev.danyil.artists.ArtistEntity;
import dev.danyil.artists.ArtistRepository;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.contracts.ReleaseService;
import dev.danyil.mappers.ReleaseMapper;
import dev.danyil.releases.dtos.ReleaseCreateRequestDTO;
import dev.danyil.releases.dtos.ReleaseFilterDTO;
import dev.danyil.releases.dtos.ReleasePhaseUpdateRequestDTO;
import dev.danyil.releases.dtos.ReleaseResponseDTO;
import dev.danyil.releases.dtos.ReleaseUpdateRequestDTO;
import dev.danyil.releases.exceptions.ReleaseAlreadyExistsException;
import dev.danyil.releases.exceptions.ReleaseException;
import dev.danyil.releases.exceptions.ReleaseNotFoundException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import dev.danyil.users.UserRepository;
import lombok.RequiredArgsConstructor;

import static dev.danyil.releases.ReleaseSpecifications.hasArtist;
import static dev.danyil.releases.ReleaseSpecifications.inGenres;
import static dev.danyil.releases.ReleaseSpecifications.inPhases;
import static dev.danyil.releases.ReleaseSpecifications.inTypes;
import static dev.danyil.releases.ReleaseSpecifications.nameContains;
import static dev.danyil.releases.ReleaseSpecifications.ownedBy;
@Service 
@RequiredArgsConstructor 
@Transactional (readOnly = true)
public class ReleaseServiceImpl implements ReleaseService {

    private final ReleaseMapper releaseMapper;
    private final ReleaseRepository releaseRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;

    // Client-controlled sort is limited to these fields: sorting by arbitrary
    // properties (e.g. user.password) would leak information through result order
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "releaseDate", "createdAt", "phase", "type");

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

        // Cross-field rules are checked on the merged state: a PATCH may change only one side
        validateConsistency(release);

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
    public void delete(Long userId, Long id) {
        releaseRepository.delete(findOwned(userId, id));
    }    

    // HELPERS

    private ReleaseEntity findOwned(Long userId, Long id) {
        ReleaseEntity release = releaseRepository.findById(id)
            .orElseThrow(() -> new ReleaseNotFoundException(id));

        if (!release.getUser().getId().equals(userId)) {
            throw new ReleaseOwnershipException();
        }
        return release;
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

    private void validateConsistency(ReleaseEntity release) {
        if (release.getSecondaryGenre() != null && release.getSecondaryGenre() == release.getPrimaryGenre()) {
            throw new ReleaseException( "Secondary genre must differ from the primary genre");
        }
        if (release.getOriginalReleaseDate() != null && release.getReleaseDate() != null
                && !release.getOriginalReleaseDate().isBefore(release.getReleaseDate())) {
            throw new ReleaseException( "Original release date must be before the release date");
        }
    }

    private void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new ReleaseException("Cannot sort by '" + order.getProperty() + "'");
            }
        });
    }


}
