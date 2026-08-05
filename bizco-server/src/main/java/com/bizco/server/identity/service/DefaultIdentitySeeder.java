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
        ensureUser("admin", "Administrator", ensureRole("ADMIN", "Administrator", Map.of(
                "identity.user.read", true,
                "identity.user.write", true,
                "identity.role.read", true,
                "identity.role.write", true,
                "settings.business.read", true,
                "settings.business.write", true)), initialAdminPassword);
        roleRepository.findByCode("MANAGER").ifPresent(role ->
                ensureUser("manager", "Manager", role, initialAdminPassword));
        roleRepository.findByCode("CASHIER").ifPresent(role ->
                ensureUser("cashier", "Cashier", role, initialAdminPassword));
    }

    private Role ensureRole(final String code, final String name, final Map<String, Boolean> permissions) {
        return roleRepository.findByCode(code)
                .orElseGet(() -> roleRepository.save(new Role(code, name, permissions, true)));
    }

    private void ensureUser(final String username, final String displayName, final Role role, final String password) {
        userRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> userRepository.save(new User(username, displayName,
                        passwordEncoder.encode(password), role)));
    }
}
