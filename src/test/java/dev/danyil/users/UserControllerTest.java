package dev.danyil.users;

import dev.danyil.global.GlobalExceptionHandler;
import dev.danyil.users.dtos.UserRequestCreateDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import dev.danyil.users.exceptions.UserAlreadyExistsException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mockMvc;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserServiceImpl.class);
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
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

}
