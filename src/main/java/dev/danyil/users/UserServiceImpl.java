package dev.danyil.users;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.contracts.UserService;
import dev.danyil.enums.UserRole;
import dev.danyil.mappers.UserMapper;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserRequestUpdateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import dev.danyil.users.exceptions.CurrentUserNotFoundException;
import dev.danyil.users.exceptions.ProfileNotFoundException;
import dev.danyil.users.exceptions.UserAlreadyExistsException;
import dev.danyil.users.exceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponseDTO register(UserRequestCreateDTO req) {
        
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("username");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("email");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.getRoles().add(UserRole.USER);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("username or email");
        }

        UserProfileEntity profile = new UserProfileEntity();
        profile.setUser(user);
        profile.setBio(req.bio());
        profile.setDisplayName(
                req.displayName() != null && !req.displayName().isBlank()
                        ? req.displayName().trim()
                        : username);
        userProfileRepository.save(profile);
        
        return userMapper.toResponse(user, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserCurrentResponseDTO getCurrent(String username) {
        UserEntity user = findUser(username);
        UserProfileEntity profile = findProfile(user.getId());

        return userMapper.toCurrentUser(user, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserAdministrationResponseDTO> getAll(Pageable pageable) {
        Page<UserEntity> pageEntities = userRepository.findAll(pageable);
        return pageEntities.map(userMapper::toAdministrationResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAdministrationResponseDTO getById(Long id) {
        UserEntity user = findUser(id);

        return userMapper.toAdministrationResponse(user);
    }

    @Override 
    @Transactional
    public UserAdministrationResponseDTO updateLocked(Long id, boolean makeLocked) {
        UserEntity user = findUser(id);

        user.setLocked(makeLocked);
        return userMapper.toAdministrationResponse(user);
    }

    @Override
    @Transactional
    public UserCurrentResponseDTO updateProfile(Long id, UserRequestUpdateDTO dto) {
        UserEntity user = findUser(id);
        UserProfileEntity profile = findProfile(user.getId());

        if (toUpdate(dto.username())) {
            user.setUsername(dto.username());
        }

        if (toUpdate(dto.email())) {
            user.setEmail(dto.email());
        }

        if (toUpdate(dto.displayName())) {
            profile.setDisplayName(dto.displayName());
        }

        if (toUpdate(dto.bio())) {
            profile.setBio(dto.bio());
        }
    
        return userMapper.toCurrentUser(user, profile);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        UserEntity user = findUser(id);
        UserProfileEntity profile = findProfile(user.getId());

        userProfileRepository.delete(profile);
        userRepository.delete(user);
    }

    private UserEntity findUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }

    private UserEntity findUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new CurrentUserNotFoundException(username));
    }

    private UserProfileEntity findProfile(Long id) {
        return userProfileRepository.findById(id)
            .orElseThrow(() -> new ProfileNotFoundException(id));
    }
    
    private boolean toUpdate(String field) {
        return field != null && field instanceof String && !field.isBlank();
    }

}
