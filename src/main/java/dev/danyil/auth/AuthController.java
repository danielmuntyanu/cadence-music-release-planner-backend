package dev.danyil.auth;

import dev.danyil.users.UserServiceImpl;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.danyil.auth.dtos.CredentialsDTO;
import dev.danyil.contracts.AuthService;
import dev.danyil.contracts.UserService;
import dev.danyil.security.dtos.JwtAuthenticationDTO;
import dev.danyil.users.dtos.UserCurrentResponseDTO;
import dev.danyil.users.dtos.UserResponseDTO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.Cookie;

@RestController
@RequestMapping(path = "${api-endpoint}/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final UserServiceImpl userServiceImpl;

    private AuthService authService;
    private UserService userService;
    
    @Value("/${api-endpoint}/auth/refresh")
    private String refreshPath;

    @Value("${cookie-same-site}")
    private String sameSite;

    @Value("${access-token-duration-minutes}")
    private int accessTokenDurationMinutes;

    @Value("${refresh-token-duration-days}")
    private int refreshTokenDurationDays;

    @PostMapping("login")
    public ResponseEntity<UserCurrentResponseDTO> loginHandler(@RequestBody @Valid CredentialsDTO credentials, HttpServletResponse response) {
        
        UserCurrentResponseDTO userDto = authService.login(credentials);
        
        JwtAuthenticationDTO authDTO = authService.getAuth(userDto.username(), userDto.roles());
        
        Cookie cookieAccess = generateCookie("access_token", authDTO.token(), "/");
        Cookie cookieRefresh = generateCookie("refresh_token", authDTO.refreshToken(), refreshPath);
        response.addCookie(cookieAccess);
        response.addCookie(cookieRefresh);
        
        return ResponseEntity.ok(userDto);
    }

    @GetMapping("logout")
    public ResponseEntity<Void> logoutHandler(HttpServletResponse response) {
        
        Cookie cookieAccess = generateCookie("access_token", "", "/");
        Cookie cookieRefresh = generateCookie("refresh_token", "", refreshPath);
        response.addCookie(cookieAccess);
        response.addCookie(cookieRefresh);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("refresh")
    public ResponseEntity<Void> refreshHandler(
        @CookieValue(name = "refresh_token", required = false) String refreshToken, 
        HttpServletResponse response
    ) {
        JwtAuthenticationDTO authDto = authService.updateAuth(refreshToken);

        Cookie cookieAccess = generateCookie("access_token", authDto.token(), "/");
        Cookie cookieRefresh = generateCookie("refresh_token", authDto.refreshToken(), refreshPath);
        response.addCookie(cookieAccess);
        response.addCookie(cookieRefresh);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("me")
    public ResponseEntity<UserCurrentResponseDTO> getMeHandler(@AuthenticationPrincipal CustomUserDetails userPrincipal) {
        return ResponseEntity.ok(userService.getCurrent(userPrincipal.getUsername()));
    }

    private Cookie generateCookie(String key, String value, String path) {
        int maxAge;
        switch (key) {
            case "access_token" -> maxAge = (int) Duration.ofMinutes(accessTokenDurationMinutes).toSeconds();
            case "refresh_token" -> maxAge = (int) Duration.ofDays(refreshTokenDurationDays).toSeconds();
            default -> maxAge = (int) Duration.ofMinutes(30).toSeconds();
        }
        
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        cookie.setAttribute("SameSite", sameSite);      
        return cookie;
    }

}
