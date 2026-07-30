package com.amway.ecommerce.lottery.auth.api;

import com.amway.ecommerce.lottery.auth.JwtService;
import com.amway.ecommerce.lottery.auth.application.AuthService;
import com.amway.ecommerce.lottery.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "登入 / 註冊")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "註冊一般使用者")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.username(), request.password());
        return ApiResponse.ok(null);
    }

    @PostMapping("/login")
    @Operation(summary = "登入取得 JWT")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return ApiResponse.ok(new LoginResponse(result.token(), "Bearer", jwtService.getExpirationMinutes() * 60, result.role()));
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 6, max = 64) String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, String role) {
    }
}
