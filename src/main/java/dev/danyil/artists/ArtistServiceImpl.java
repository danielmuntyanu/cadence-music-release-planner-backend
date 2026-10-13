package dev.danyil.artists;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialRequestDTO;
import dev.danyil.artists.exceptions.ArtistAlreadyExistsException;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.artists.exceptions.ArtistOwnershipException;
import dev.danyil.artists.exceptions.DuplicateSocialPlatformException;
import dev.danyil.contracts.ArtistService;
import dev.danyil.enums.SocialPlatform;
import dev.danyil.mappers.ArtistMapper;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserRepository;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final ArtistMapper artistMapper;

    @Override 
    @Transactional
    public ArtistResponseDTO create(ArtistCreateRequestDTO dto, Long userId) {
        String name = dto.name().trim();
        if (artistRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ArtistAlreadyExistsException(name);
        }
        ensureUniquePlatforms(dto.socials());

        // Reference proxy: no SELECT needed, only the id is used for the foreign key.
        UserEntity owner = userRepository.getReferenceById(userId);

        ArtistEntity saved = artistRepository.save(artistMapper.toEntity(dto, owner));
        return artistMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ArtistResponseDTO> getAll(Long userId, Pageable pageable) {
        Page<ArtistEntity> pageEntities = artistRepository.findAllByUserId(userId, pageable);
        return pageEntities.map(artistMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ArtistResponseDTO getById(Long userId, Long id) {
        ArtistEntity artist = findArtist(id);
        checkOwner(artist, userId);

        return artistMapper.toResponse(artist);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        ArtistEntity artist = findArtist(id);
        checkOwner(artist, userId);

        artistRepository.delete(artist);
    }

    @Override
    @Transactional
    public ArtistResponseDTO update(Long userId, Long id, ArtistUpdateRequestDTO dto) {
        ArtistEntity artist = findArtist(id);
        checkOwner(artist, userId);

        if (dto.name() != null) {
            String newName = dto.name().trim();
            boolean nameChanged = !newName.equalsIgnoreCase(artist.getName());
            if (nameChanged && artistRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
                throw new ArtistAlreadyExistsException(newName);
            }
        }
        ensureUniquePlatforms(dto.socials());

        artistMapper.applyUpdate(dto, artist);
        return artistMapper.toResponse(artist);
    }

    // HELPERS

    private void ensureUniquePlatforms(List<SocialRequestDTO> socials) {
        if (socials == null) {
            return;
        }
        Set<SocialPlatform> seen = new HashSet<>();
        for (SocialRequestDTO social : socials) {
            if (!seen.add(social.platform())) {
                throw new DuplicateSocialPlatformException(social.platform());
            }
        }
    }

    private ArtistEntity findArtist(Long id) {
        return artistRepository.findById(id)
            .orElseThrow(() -> new ArtistNotFoundException(id));
        
    }

    private void checkOwner(ArtistEntity artist, Long userId) {
        if (!Objects.equals(artist.getUser().getId(), userId)) {
            throw new ArtistOwnershipException();
        }
    }
    
}
