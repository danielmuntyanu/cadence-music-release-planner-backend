package dev.danyil.auth;

import dev.danyil.mappers.UserMapper;

import java.util.HashSet;
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
import io.jsonwebtoken.JwtException;
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
        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new JwtException("Invalid refresh token: token doesn't exist");
        }
        
        jwtService.validateJwtToken(oldRefreshToken);
            
        String tokenUsername = jwtService.getUsernameFromToken(oldRefreshToken);
        Set<String> tokenRoles = jwtService.getRolesFromToken(oldRefreshToken);

        UserEntity user = userRepository.findByUsername(tokenUsername).orElseThrow(
            () -> new JwtException("Invalid refresh token: user with username " + tokenUsername + " doesn't exist")
        );
        
        UserProfileEntity profile = userProfileRepository.findById(user.getId()).orElseThrow(
            () -> new JwtException("Invalid refresh token: userProfile with id " + user.getId() + " doesn't exist")
        );

        UserCurrentResponseDTO userDTO = userMapper.toCurrentUser(user, profile);

        Set<String> userRoles = new HashSet<>(userDTO.roles());
        if (!tokenRoles.equals(userRoles)) {
            throw new JwtException("Invalid refresh token: roles mismatch");
        }

        return jwtService.refreshBaseToken(tokenUsername, String.join(", ", userRoles), oldRefreshToken);
    }
    
}
