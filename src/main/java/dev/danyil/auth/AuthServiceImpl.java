package dev.danyil.auth;

import dev.danyil.mappers.UserMapper;
import java.util.Set;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.auth.dtos.CredentialsDTO;
import dev.danyil.contracts.AuthService;
import dev.danyil.security.JwtService;
import dev.danyil.security.dtos.JwtAuthenticationDTO;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserProfileEntity;
import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.exceptions.ProfileNotFoundException;
import dev.danyil.users.exceptions.UserNotFoundException;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class AuthServiceImpl implements AuthService{

    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserCurrentResponseDTO login(CredentialsDTO credentials) {
        UserEntity user = userRepository.findByUsername(credentials.username())
            .orElseThrow(() -> new BadCredentialsException("User doesn't exist."));

        if (!passwordEncoder.matches(credentials.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Wrong password");
        } 

        UserProfileEntity profile = userProfileRepository.findById(user.getId())
            .orElseThrow(() -> new ProfileNotFoundException(user.getId()));
        
        return userMapper.toCurrentUser(user, profile);
    }

    @Override
    public JwtAuthenticationDTO getAuth(String username, Set<String> roles) {
        String rolesString = String.join(", ", roles);
        return jwtService.generateAuthToken(username, rolesString);
    }

    @Override
    public JwtAuthenticationDTO updateAuth(String oldRefreshToken) {
        // TODO Auto-generated method stub
        return null;
    }
    
}
