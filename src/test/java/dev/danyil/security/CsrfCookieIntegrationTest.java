package dev.danyil.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import dev.danyil.enums.UserRole;
import dev.danyil.users.UserEntity;
import dev.danyil.users.UserProfileEntity;
import dev.danyil.users.UserRepository;
import dev.danyil.users.UserProfileRepository;
import jakarta.servlet.http.Cookie;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = "csrf-cookie-test=true")
@Transactional
class CsrfCookieIntegrationTest {

    private static final String USERNAME = "csrf-admin";
    private static final String EMAIL = "csrf-admin@test.com";
    private static final String PASSWORD = "admin-password";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    @BeforeEach
    void setUp() {
        UserEntity admin = new UserEntity();
        admin.setUsername(USERNAME);
        admin.setEmail(EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRoles(Set.of(UserRole.ADMIN));
        userRepository.save(admin);

        UserProfileEntity adminProfile = new UserProfileEntity();
        adminProfile.setUser(admin);
        adminProfile.setDisplayName(USERNAME);
        userProfileRepository.save(adminProfile);
    }

    private MvcResult login() throws Exception {
        String body = """
            {"username":"%s","password":"%s"}
            """.formatted(USERNAME, PASSWORD);

        return mockMvc.perform(post(apiEndpoint + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();
    }

    @Test
    void authenticatedRequest_doesNotClearCsrfCookie() throws Exception {
        MvcResult login = login();
        Cookie accessToken = login.getResponse().getCookie("access_token");
        Cookie csrfToken = login.getResponse().getCookie("XSRF-TOKEN");

        MvcResult list = mockMvc.perform(get(apiEndpoint + "/users")
                .cookie(accessToken, csrfToken))
            .andExpect(status().isOk())
            .andReturn();

        Cookie csrfAfter = list.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfAfter == null || !csrfAfter.getValue().isEmpty()).isTrue();
    }

    @Test
    void patchWithCsrfHeader_isNotRejectedByCsrf() throws Exception {
        MvcResult login = login();
        Cookie accessToken = login.getResponse().getCookie("access_token");
        Cookie csrfToken = login.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(patch(apiEndpoint + "/users/" + 100 + "/make-locked")
                .cookie(accessToken, csrfToken)
                .header("X-XSRF-TOKEN", csrfToken.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"make_locked\": true}"))
            .andExpect(status().isNotFound());
    }

}
