package dev.danyil.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class PublicEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    @Test
    void authLogin_withoutToken_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void authLogout_withoutToken_isNotBlockedBySecurity() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/auth/logout")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void authRefresh_withoutToken_isNotBlockedBySecurityButReturn403() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/auth/refresh"))
            .andExpect(status().isForbidden()); // without refresh token it should return 403
    }

    @Test
    void postUsers_withoutToken_isBlockedByRoutingOrderBug() throws Exception {
        mockMvc.perform(post(apiEndpoint + "/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden()); 
    }
}
