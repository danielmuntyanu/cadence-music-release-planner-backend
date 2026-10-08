package dev.danyil.auth;

import dev.danyil.users.UserEntity;
import dev.danyil.users.UserProfileEntity;
import dev.danyil.users.UserProfileRepository;
import dev.danyil.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.danyil.enums.UserRole;
import dev.danyil.security.JwtService;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired 
    private UserProfileRepository userProfileRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired 
    private PasswordEncoder passwordEncoder;


    @Value("/${api-endpoint}")
    private String apiEndpoint;

    private UserEntity user;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setUsername("test-user");
        user.setEmail("login-test@test.com");
        user.setPasswordHash(passwordEncoder.encode("correct-password")); 
        user.setRoles(Set.of(UserRole.USER));
        userRepository.save(user);

        profile = new UserProfileEntity();
        profile.setUser(user);
        profile.setDisplayName("Test Display Name");
        profile.setBio("Test bio");
        profile.setAvatarUrl("test-avatar");
        userProfileRepository.save(profile);
    }

    @Test
    void login_withCorrectCredentials_returns200AndSetsCookies() throws Exception {
        String body = """
            {"username":"test-user","password":"correct-password"}
            """;

        mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"))
            .andExpect(jsonPath("$.username").value("test-user"));
    }

         @Test
    void login_withoutCsrfToken_returns200() throws Exception {
        String body = """
            {"username":"test-user","password":"correct-password"}
            """;

        mockMvc.perform(post(apiEndpoint + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("access_token"));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        String body = """
            {"username":"test-user","password":"wrong-password"}
            """;

        mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withNonexistentEmail_returnsUnauthorized() throws Exception {
        String body = """
            {"username":"nobody","password":"whatever"}
            """;

        mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }


    @Test
    void logout_withValidToken_returns204() throws Exception {
        String token = jwtService.generateAuthToken(user.getUsername(), "USER").token();

        mockMvc.perform(post(apiEndpoint + "/auth/logout")
                .cookie(new Cookie("access_token", token)))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_withoutToken_returns204() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/auth/logout"))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_clearsCookiesWithEmptyValue() throws Exception {
        String token = jwtService.generateAuthToken(user.getUsername(), "USER").token();

        mockMvc.perform(post(apiEndpoint + "/auth/logout")
                .cookie(new Cookie("access_token", token)))
            .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().value("access_token", ""))
            .andExpect(cookie().value("refresh_token", ""));
    }


    @Test
    void refresh_withValidRefreshToken_returns204AndSetsNewCookies() throws Exception {
        String refreshToken = jwtService.generateAuthToken(user.getEmail(), "ROLE_USER").refreshToken();

        mockMvc.perform(post(apiEndpoint + "/auth/refresh")
                .cookie(new Cookie("refresh_token", refreshToken)))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("access_token"))
            .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    void refresh_withoutRefreshToken_returnsError() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/auth/refresh"))
            .andExpect(status().isForbidden());
    }

    @Test
    void me_withValidToken_returns200WithUserData() throws Exception {
        String token = jwtService.generateAuthToken(user.getEmail(), "ROLE_USER").token();

        mockMvc.perform(get(apiEndpoint + "/auth/me")
                .cookie(new Cookie("access_token", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void me_withoutToken_returnsForbidden() throws Exception {
        mockMvc.perform(get(apiEndpoint + "/auth/me"))
            .andExpect(status().isForbidden());
    }

}
