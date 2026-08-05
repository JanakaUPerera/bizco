package com.bizco.server.identity.service;

import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultIdentitySeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialAdminPassword;

    public DefaultIdentitySeeder(final RoleRepository roleRepository, final UserRepository userRepository,
                                 final PasswordEncoder passwordEncoder,
                                 @Value("${bizco.initial-admin-password:}") final String initialAdminPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialAdminPassword = initialAdminPassword;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        if (initialAdminPassword == null || initialAdminPassword.isBlank()) {
            return;
        }
        final Role adminRole = roleRepository.findByCode("ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ADMIN", "Administrator", Map.of(
                        "identity.user.read", true,
                        "identity.user.write", true,
                        "identity.role.read", true,
                        "identity.role.write", true
                ), true)));
        userRepository.findByUsernameIgnoreCase("admin")
                .orElseGet(() -> userRepository.save(new User("admin", "Administrator",
                        passwordEncoder.encode(initialAdminPassword), adminRole)));
    }
}

