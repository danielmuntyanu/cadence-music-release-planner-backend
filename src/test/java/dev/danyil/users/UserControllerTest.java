package dev.danyil.users;

import dev.danyil.contracts.UserService;
import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.auth.CustomUserDetails;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserRequestPasswordDTO;
import dev.danyil.users.dtos.UserRequestUpdateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import dev.danyil.users.exceptions.UserAlreadyExistsException;
import dev.danyil.users.exceptions.UserNotFoundException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

class UserControllerTest {

    // Outside the Long cache range (-128..127) on purpose, like in the artist tests.
    private static final long CURRENT_USER_ID = 1000L;
    private static final String ME_URL = "/api/v1/users/me";

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(
                new PageableHandlerMethodArgumentResolver(),
                new AuthenticationPrincipalArgumentResolver())
            .addPlaceholderValue("api-endpoint", "api/v1")
            .build();

        // Same principal type the JWT filter puts into the SecurityContext in production.
        UserEntity user = new UserEntity();
        user.setId(CURRENT_USER_ID);
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        // The context is thread-bound: without clearing, the user leaks into other test classes.
        SecurityContextHolder.clearContext();
    }

    private String validUserJson() {
        return """
            {
              "username": "test_user",
              "email": "user@test.com",
              "password": "user1234",
              "displayName": "Test User",
              "bio": "Some bio about me"
            }
            """;
    }

    private String notValidUserJson() {
        return """
            {
              "email": "user@test.com",
              "bio": "Some bio about me"
            }
            """;
    }

    @Test
    void createUser_withValidData_returns201AndCreatedUser() throws Exception {
        
        Long mockId = 5L;
        UserResponseDTO savedUser = UserResponseDTO.builder()
            .id(mockId)
            .username("test_user")
            .build();

        when(userService.register(any(UserRequestCreateDTO.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUserJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(mockId.toString()))
            .andExpect(jsonPath("$.username").value("test_user"));
    }

    @Test
    void createUser_withBlankFirstName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(notValidUserJson()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withDuplicateEmail_returns409() throws Exception {
        when(userService.register(any(UserRequestCreateDTO.class)))
            .thenThrow(new UserAlreadyExistsException("email"));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUserJson()))
            .andExpect(status().isConflict());
    }

    private UserAdministrationResponseDTO createMockAdminDTO(Long id, String username) {
        return UserAdministrationResponseDTO.builder()
            .id(id)
            .username(username)
            .email(username + "@test.com")
            .enabled(true)
            .locked(false)
            .createdAt(Instant.now())
        .build();
    }

    @Test
    void getAll_returns200AndPageContent() throws Exception {
        List<UserAdministrationResponseDTO> content = List.of(
            createMockAdminDTO(1L, "test1"),
            createMockAdminDTO(2L, "test2"),
            createMockAdminDTO(3L, "test3"));
        Pageable pageable = PageRequest.of(0, 20);
        when(userService.getAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(content, pageable, content.size()));

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].username").value("test1"))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void getAll_passesPagingParamsToService() throws Exception {
        when(userService.getAll(any(Pageable.class)))
            .thenReturn(Page.empty(PageRequest.of(2, 5)));

        mockMvc.perform(get("/api/v1/users").param("page", "2").param("size", "5"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userService).getAll(captor.capture());
        assertThat(captor.getValue().getPageNumber(), is(equalTo(2)));
        assertThat(captor.getValue().getPageSize(), is(equalTo(5)));
    }

    // ---------- getById ----------

    @Test
    void getById_returns200() throws Exception {
        when(userService.getById(5L)).thenReturn(createMockAdminDTO(5L, "test5"));

        mockMvc.perform(get("/api/v1/users/5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test5"));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(userService.getById(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/v1/users/99"))
            .andExpect(status().isNotFound());
    }

    // ---------- updateLocked ----------

    @Test
    void updateLocked_returns200AndPassesFlag() throws Exception {
        when(userService.updateLocked(5L, true)).thenReturn(createMockAdminDTO(5L, "test5"));

        mockMvc.perform(patch("/api/v1/users/5/make-locked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"makeLocked": true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test5"));

        verify(userService).updateLocked(5L, true);
    }

    @Test
    void updateLocked_missingUser_returns404() throws Exception {
        when(userService.updateLocked(eq(99L), anyBoolean())).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(patch("/api/v1/users/99/make-locked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"makeLocked\": true}"))
            .andExpect(status().isNotFound());
    }

    // ---------- PATCH /users/me ----------

    private UserCurrentResponseDTO currentUserResponse(String username) {
        return UserCurrentResponseDTO.builder()
            .id(CURRENT_USER_ID)
            .username(username)
            .build();
    }

    @Test
    void updateProfile_validRequest_returns200AndUpdatesCurrentUser() throws Exception {
        when(userService.update(eq(CURRENT_USER_ID), any(UserRequestUpdateDTO.class)))
            .thenReturn(currentUserResponse("new_name"));

        mockMvc.perform(patch(ME_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "new_name", "bio": "New bio" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("new_name"));

        // The id comes from the authenticated principal, never from the request.
        ArgumentCaptor<UserRequestUpdateDTO> captor = ArgumentCaptor.forClass(UserRequestUpdateDTO.class);
        verify(userService).update(eq(CURRENT_USER_ID), captor.capture());
        assertThat(captor.getValue().username(), is("new_name"));
        assertThat(captor.getValue().bio(), is("New bio"));
        assertThat(captor.getValue().email(), is(nullValue()));
    }

    @Test
    void updateProfile_invalidEmail_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(patch(ME_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "not-an-email" }
                    """))
            .andExpect(status().isBadRequest());

        verify(userService, never()).update(anyLong(), any());
    }

    @Test
    void updateProfile_usernameTaken_returns409() throws Exception {
        when(userService.update(eq(CURRENT_USER_ID), any(UserRequestUpdateDTO.class)))
            .thenThrow(new UserAlreadyExistsException("username"));

        mockMvc.perform(patch(ME_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "username": "taken" }
                    """))
            .andExpect(status().isConflict());
    }

    // ---------- PUT /users/me/password ----------

    @Test
    void updatePassword_validRequest_returns204() throws Exception {
        mockMvc.perform(put(ME_URL + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "current123", "newPassword": "newPassword123" }
                    """))
            .andExpect(status().isNoContent());

        verify(userService).updatePassword(CURRENT_USER_ID,
            new UserRequestPasswordDTO("current123", "newPassword123"));
    }

    @Test
    void updatePassword_blankNewPassword_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(put(ME_URL + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "current123", "newPassword": "" }
                    """))
            .andExpect(status().isBadRequest());

        verify(userService, never()).updatePassword(anyLong(), any());
    }

    @Test
    void updatePassword_wrongCurrentPassword_returns400NotUnauthorized() throws Exception {
        // 401 would make the frontend think the session expired and try to refresh the token.
        doThrow(new BadCredentialsException("Wrong current password"))
            .when(userService).updatePassword(eq(CURRENT_USER_ID), any(UserRequestPasswordDTO.class));

        mockMvc.perform(put(ME_URL + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "currentPassword": "wrong", "newPassword": "newPassword123" }
                    """))
            .andExpect(status().isBadRequest());
    }

    // ---------- DELETE /users/me ----------

    @Test
    void deleteMe_returns204AndDeletesCurrentUser() throws Exception {
        mockMvc.perform(delete(ME_URL))
            .andExpect(status().isNoContent());

        verify(userService).delete(CURRENT_USER_ID);
    }

    @Test
    void deleteMe_userMissing_returns404() throws Exception {
        doThrow(new UserNotFoundException(CURRENT_USER_ID)).when(userService).delete(CURRENT_USER_ID);

        mockMvc.perform(delete(ME_URL))
            .andExpect(status().isNotFound());
    }
}