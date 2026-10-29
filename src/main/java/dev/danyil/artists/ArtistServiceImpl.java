package dev.danyil.artists;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.danyil.artists.dtos.ArtistCreateRequestDTO;
import dev.danyil.artists.dtos.ArtistResponseDTO;
import dev.danyil.artists.dtos.ArtistUpdateRequestDTO;
import dev.danyil.artists.dtos.SocialRequestDTO;
import dev.danyil.artists.exceptions.ArtistAlreadyExistsException;
import dev.danyil.artists.exceptions.ArtistNotFoundException;
import dev.danyil.artists.exceptions.ArtistOwnershipException;
import dev.danyil.artists.exceptions.DuplicateSocialPlatformException;
import dev.danyil.contracts.ArtistService;
import dev.danyil.enums.ArtistImage;
import dev.danyil.enums.SocialPlatform;
import dev.danyil.mappers.ArtistMapper;
import dev.danyil.media.ImageUploadService;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final ArtistMapper artistMapper;
    private final ImageUploadService imageUploads;

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
        return artistMapper.toResponse(findOwned(userId, id));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        ArtistEntity artist = findOwned(userId, id);

        imageUploads.deleteAfterCommit(artist.getAvatarKey());
        imageUploads.deleteAfterCommit(artist.getBannerKey());
        artistRepository.delete(artist);
    }

    @Override
    @Transactional
    public ArtistResponseDTO update(Long userId, Long id, ArtistUpdateRequestDTO dto) {
        ArtistEntity artist = findOwned(userId, id);

        if (dto.name() != null) {
            String newName = dto.name().trim();
            boolean nameChanged = !newName.equalsIgnoreCase(artist.getName());
            if (nameChanged && artistRepository.existsByUserIdAndNameIgnoreCase(userId, newName)) {
                throw new ArtistAlreadyExistsException(newName);
            }
        }
        ensureUniquePlatforms(dto.socials());

        // A link (or "" to clear) replaces an uploaded file
        if (dto.avatarUrl() != null) dropStoredFile(artist, ArtistImage.AVATAR);
        if (dto.bannerUrl() != null) dropStoredFile(artist, ArtistImage.BANNER);

        artistMapper.applyUpdate(dto, artist);
        return artistMapper.toResponse(artist);
    }

    @Override
    @Transactional
    public ArtistResponseDTO uploadImage(Long userId, Long id, ArtistImage image, MultipartFile file) {
        // Ownership first: a foreign request never costs image processing or storage
        ArtistEntity artist = findOwned(userId, id);

        String key = imageUploads.store(image.keyPrefix(userId, id), image.kind(), file);
        dropStoredFile(artist, image);
        setImage(artist, image, key, null);

        return artistMapper.toResponse(artist);
    }

    @Override
    @Transactional
    public ArtistResponseDTO removeImage(Long userId, Long id, ArtistImage image) {
        ArtistEntity artist = findOwned(userId, id);

        dropStoredFile(artist, image);
        setImage(artist, image, null, null);

        return artistMapper.toResponse(artist);
    }

    // HELPERS

    /** Schedules the stored file of this slot for deletion after commit and forgets its key. */
    private void dropStoredFile(ArtistEntity artist, ArtistImage image) {
        String key = image == ArtistImage.AVATAR ? artist.getAvatarKey() : artist.getBannerKey();
        imageUploads.deleteAfterCommit(key);
        if (image == ArtistImage.AVATAR) artist.setAvatarKey(null);
        else artist.setBannerKey(null);
    }

    private static void setImage(ArtistEntity artist, ArtistImage image, String key, String url) {
        if (image == ArtistImage.AVATAR) {
            artist.setAvatarKey(key);
            artist.setAvatarUrl(url);
        } else {
            artist.setBannerKey(key);
            artist.setBannerUrl(url);
        }
    }

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

    private ArtistEntity findOwned(Long userId, Long id) {
        ArtistEntity artist = artistRepository.findById(id)
            .orElseThrow(() -> new ArtistNotFoundException(id));
        if (!Objects.equals(artist.getUser().getId(), userId)) {
            throw new ArtistOwnershipException();
        }
        return artist;
    }
}