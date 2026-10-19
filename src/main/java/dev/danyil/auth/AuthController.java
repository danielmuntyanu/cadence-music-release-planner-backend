package dev.danyil.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.Cookie;
import org.springframework.util.StringUtils;


@RestController
@RequestMapping(path = "${api-endpoint}/auth")
@RequiredArgsConstructor 
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    
    @Value("/${api-endpoint}/auth/")
    private String refreshPath;

    @Value("${cookie-same-site}")
    private String sameSite;

    @Value("${refresh-cookie-name}")
    private String refreshCookieName;
    
    @Value("${access-cookie-name}")
    private String accessCookieName;

    @Value("${access-token-duration-minutes}")
    private int accessTokenDurationMinutes;

    @Value("${refresh-token-duration-days}")
    private int refreshTokenDurationDays;

    @PostMapping("login")
    public ResponseEntity<UserCurrentResponseDTO> loginHandler(@RequestBody @Valid CredentialsDTO credentials, HttpServletResponse response) {
        
        UserCurrentResponseDTO userDto = authService.login(credentials);
        
        JwtAuthenticationDTO authDTO = authService.getAuth(userDto.username(), userDto.roles());
        
        response.addCookie(accessCookie(authDTO.token()));
        response.addCookie(refreshCookie(authDTO.refreshToken()));
        
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("logout")
    public ResponseEntity<Void> logoutHandler(HttpServletResponse response) {
        
        response.addCookie(accessCookie(""));
        response.addCookie(refreshCookie(""));
        
        return ResponseEntity.noContent().build();
    }

    @PostMapping("refresh")
    public ResponseEntity<Void> refreshHandler(
        @CookieValue(name = "${refresh-cookie-name}", required = false) String refreshToken, 
        HttpServletResponse response
    ) {
        JwtAuthenticationDTO authDto = authService.updateAuth(refreshToken);

        response.addCookie(accessCookie(authDto.token()));
        response.addCookie(refreshCookie(authDto.refreshToken()));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("me")
    public ResponseEntity<UserCurrentResponseDTO> getMeHandler(
            @AuthenticationPrincipal CustomUserDetails userPrincipal,
            @CookieValue(name = "${refresh-cookie-name}", required = false) String refreshToken) {
        if (userPrincipal != null) return ResponseEntity.ok(userService.getCurrent(userPrincipal.getUsername()));
        
        // if access token is expired, but a valid refresh token exists we will answer with 401 to let interceptor make a refresh request
        if (StringUtils.hasText(refreshToken)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        return ResponseEntity.noContent().build();
    }

    // HELPERS

    private Cookie generateCookie(String key, String value, String path, int maxAge) {
        Cookie cookie = new Cookie(key, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        cookie.setAttribute("SameSite", sameSite);      
        return cookie;
    }

    private Cookie accessCookie(String token) {
        int maxAge = token.isBlank() ? 0 : (int) Duration.ofMinutes(accessTokenDurationMinutes).toSeconds();

        return generateCookie(
            accessCookieName, 
            token, 
            "/", 
            maxAge
        );
    }

    private Cookie refreshCookie(String token) {
        int maxAge = token.isBlank() ? 0 : (int) Duration.ofDays(refreshTokenDurationDays).toSeconds();

        return generateCookie(
            refreshCookieName, 
            token, 
            refreshPath, 
            maxAge
        );
    }
 
}
