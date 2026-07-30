package com.amway.ecommerce.lottery.auth.application;

import com.amway.ecommerce.lottery.auth.JwtService;
import com.amway.ecommerce.lottery.auth.domain.AppUser;
import com.amway.ecommerce.lottery.auth.domain.AppUserRepository;
import com.amway.ecommerce.lottery.auth.domain.Role;
import com.amway.ecommerce.lottery.common.exception.BusinessException;
import com.amway.ecommerce.lottery.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void register(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "使用者名稱已存在");
        }
        AppUser user = new AppUser(username, passwordEncoder.encode(rawPassword), Role.USER);
        userRepository.save(user);
    }

    public record LoginResult(String token, String role) {}

    @Transactional(readOnly = true)
    public LoginResult login(String username, String rawPassword) {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "帳號或密碼錯誤"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "帳號或密碼錯誤");
        }
        return new LoginResult(jwtService.generateToken(user), user.getRole().name());
    }
}
