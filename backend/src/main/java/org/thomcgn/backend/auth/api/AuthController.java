package org.thomcgn.backend.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thomcgn.backend.auth.api.dto.LogoutRequest;
import org.thomcgn.backend.auth.api.dto.LoginRequest;
import org.thomcgn.backend.auth.api.dto.LoginResponse;
import org.thomcgn.backend.auth.api.dto.RefreshTokenRequest;
import org.thomcgn.backend.auth.api.dto.SessionResponse;
import org.thomcgn.backend.auth.service.ClientMetadata;
import org.thomcgn.backend.auth.service.AuthService;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, Refresh, Sessionverwaltung und Logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Benutzer einloggen")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login erfolgreich"),
            @ApiResponse(responseCode = "401", description = "Anmeldedaten ungueltig")
    })
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, extractMetadata(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access-Token mit Refresh-Token erneuern")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token erneuert"),
            @ApiResponse(responseCode = "401", description = "Refresh-Token ungueltig")
    })
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, extractMetadata(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aktuelle Session ausloggen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Session ausgeloggt")
    public void logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        authService.logout(request, extractBearer(authorizationHeader));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Aktive Sessions des aktuellen Benutzers abrufen")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Sessions geladen")
    public List<SessionResponse> sessions(
            Principal principal,
            @RequestHeader(value = "X-Current-Refresh-Token", required = false) String currentRefreshToken
    ) {
        return authService.listSessions(principal.getName(), currentRefreshToken);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eine Session des aktuellen Benutzers beenden")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Session beendet")
    public void revokeSession(@PathVariable Long id, Principal principal) {
        authService.revokeSession(principal.getName(), id);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Alle Sessions des aktuellen Benutzers beenden")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Alle Sessions beendet")
    public void logoutAll(
            Principal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        authService.logoutAllSessions(principal.getName(), extractBearer(authorizationHeader));
    }

    private String extractBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring(7);
    }

    private ClientMetadata extractMetadata(HttpServletRequest request) {
        return new ClientMetadata(request.getHeader("User-Agent"), request.getRemoteAddr());
    }
}

