package dev.danyil.users;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.contracts.UserService;
import dev.danyil.enums.UserRole;
import dev.danyil.mappers.UserMapper;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
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
        user.setUsername(req.username());
        user.setEmail(req.email());
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
                        ? req.displayName()
                        : req.username());
        userProfileRepository.save(profile);
        
        return userMapper.toResponse(user, profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserCurrentResponseDTO getCurrent(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));

        UserProfileEntity profile = userProfileRepository.findById(user.getId())
            .orElseThrow(() -> new ProfileNotFoundException(user.getId()));

        return userMapper.toCurrentUser(user, profile);
    }

    

}
