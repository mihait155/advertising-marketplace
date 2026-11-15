package com.advertisingmarketplace.auth;

import com.advertisingmarketplace.security.SecurityProperties;
import com.advertisingmarketplace.user.User;
import com.advertisingmarketplace.user.UserRepository;
import com.advertisingmarketplace.user.UserService;
import com.advertisingmarketplace.user.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin properties = securityProperties.bootstrapAdmin();
        if (!properties.isConfigured()) {
            return;
        }
        String email = UserService.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(new User(properties.fullName(), email, null, UserType.ADMIN,
                passwordEncoder.encode(properties.password())));
        log.info("Created bootstrap admin user {}", email);
    }
}
