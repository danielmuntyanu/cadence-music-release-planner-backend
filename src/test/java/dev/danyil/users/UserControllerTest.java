package dev.danyil.users;

import dev.danyil.contracts.UserService;
import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.users.dtos.UserAdministrationResponseDTO;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import dev.danyil.users.exceptions.UserAlreadyExistsException;
import dev.danyil.users.exceptions.UserNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

class UserControllerTest {

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserServiceImpl.class);
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .addPlaceholderValue("api-endpoint", "api/v1")
            .build();
    }

    private String validUserJson() {
        return """
            {
              "username": "test_user",
              "email": "user@test.com",
              "password": "user1234",
              "display_name": "Test User",
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
            .andExpect(status().isNotFound()); // если твой handler отдаёт другой код, поправь
    }

    // ---------- updateLocked ----------

    @Test
    void updateLocked_returns200AndPassesFlag() throws Exception {
        when(userService.updateLocked(5L, true)).thenReturn(createMockAdminDTO(5L, "test5"));

        mockMvc.perform(patch("/api/v1/users/5/make-locked")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"make_locked": true}
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
                .content("{\"make_locked\": true}"))
            .andExpect(status().isNotFound());
    }

}
