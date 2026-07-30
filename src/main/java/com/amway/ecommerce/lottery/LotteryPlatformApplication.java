package com.amway.ecommerce.lottery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// auth is fully handled by the JWT filter + AuthService, so we don't want the
// default in-memory user (and its generated-password log line) that Spring Security
// would otherwise create.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LotteryPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotteryPlatformApplication.class, args);
    }
}
