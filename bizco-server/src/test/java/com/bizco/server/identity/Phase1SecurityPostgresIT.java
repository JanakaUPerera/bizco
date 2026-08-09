package com.bizco.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.identity.AuthResponses.LoginRequest;
import com.bizco.common.dto.identity.AuthResponses.LoginResponse;
import com.bizco.common.dto.identity.RoleRequests.RoleUpsertRequest;
import com.bizco.common.dto.identity.RoleRequests.SecondaryRoleGrantRequest;
import com.bizco.common.dto.identity.RoleResponses.RoleResponse;
import com.bizco.server.identity.entity.Role;
import com.bizco.server.identity.entity.User;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import com.bizco.server.identity.repository.RoleRepository;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.repository.UserSessionRepository;
import com.bizco.server.identity.security.TokenService;
import com.bizco.server.identity.service.AuthService;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.identity.service.PermissionService;
import com.bizco.server.identity.service.RoleService;
import com.bizco.server.identity.service.UserService;
import com.bizco.server.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Rollback
class Phase1SecurityPostgresIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct1!";

    @Autowired
    private AuthService authService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserSessionRepository sessionRepository;
    @Autowired
    private LoginHistoryRepository loginHistoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Test
    void secAuth001SuccessfulLoginStoresOnlyTokenHashAndReturnsEffectivePermissions() {
        final User user = createUser("auth_success", PASSWORD, "CASHIER");

        final LoginResponse response = login("auth_success", PASSWORD, "client-a");

        assertThat(response.data().sessionToken()).isNotBlank();
        assertThat(response.data().user().username()).isEqualTo("auth_success");
        assertThat(response.data().user().effectivePermissions()).contains("invoice.create");
        assertThat(sessionRepository.findByTokenHash(response.data().sessionToken())).isEmpty();
        assertThat(sessionRepository.findByTokenHash(tokenService.hash(response.data().sessionToken()))).isPresent();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void secAuth002And003RejectedLoginsRecordHistoryIncludingUnknownUsername() {
        createUser("auth_reject", PASSWORD, "CASHIER");

        assertThatThrownBy(() -> login("auth_reject", "Wrong1!", "client-a"))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        assertThatThrownBy(() -> login("missing_user", PASSWORD, "client-a"))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_INVALID_CREDENTIALS);

        assertThat(jdbc.queryForObject("""
                select count(*)
                from login_history
                where attempted_username = 'missing_user'
                and user_id is null
                and success = false
                """, Long.class)).isEqualTo(1);
        assertThat(userRepository.findByUsernameIgnoreCase("auth_reject").orElseThrow().getFailedLoginAttempts())
                .isEqualTo(1);
    }

    @Test
    void secAuth004FifthFailureLocksAndSecAuth005ExpiredLockAutoUnlocks() {
        final User user = createUser("auth_lock", PASSWORD, "CASHIER");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> login("auth_lock", "Wrong1!", "client-a"))
                    .isInstanceOf(IdentityException.class);
        }
        assertThat(userRepository.findById(user.getId()).orElseThrow().isLocked()).isTrue();

        entityManager.flush();
        entityManager.clear();
        jdbc.update("""
                update users
                set locked_until = ?, failed_login_attempts = 5, is_locked = true
                where user_id = ?
                """, Timestamp.from(Instant.now().minusSeconds(60)), user.getId());
        entityManager.clear();

        login("auth_lock", PASSWORD, "client-a");

        final User unlocked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unlocked.isLocked()).isFalse();
        assertThat(unlocked.getFailedLoginAttempts()).isZero();
    }

    @Test
    void secAuth006ManualLockAndSecAuth007InactiveUserRejectLogin() {
        final User locked = createUser("auth_manual_lock", PASSWORD, "CASHIER");
        userService.lockUser(locked.getId());

        assertThatThrownBy(() -> login("auth_manual_lock", PASSWORD, "client-a"))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_LOCKED);

        final User inactive = createUser("auth_inactive", PASSWORD, "CASHIER");
        inactive.update(inactive.getDisplayName(), inactive.getPrimaryRole(), false);
        userRepository.saveAndFlush(inactive);

        assertThatThrownBy(() -> login("auth_inactive", PASSWORD, "client-a"))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
    }

    @Test
    void secSession001IdleTimeoutAndSecSession002LogoutRevokesCurrentSession() {
        createUser("session_idle", PASSWORD, "CASHIER");
        final LoginResponse response = login("session_idle", PASSWORD, "client-a");
        final String tokenHash = tokenService.hash(response.data().sessionToken());

        jdbc.update("""
                update user_sessions
                set last_activity_at = ?, expires_at = ?
                where token_hash = ?
                """, Timestamp.from(Instant.now().minusSeconds(901)),
                Timestamp.from(Instant.now().plusSeconds(60)), tokenHash);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> authService.currentSession(response.data().sessionToken()))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_SESSION_EXPIRED);

        final LoginResponse active = login("session_idle", PASSWORD, "client-b");
        authService.logout(active.data().sessionToken());

        assertThat(sessionRepository.findByTokenHash(tokenService.hash(active.data().sessionToken())).orElseThrow()
                .getRevokedAt()).isNotNull();
    }

    @Test
    void secSession003ConcurrentLimitAndSecSession004ForceLogout() {
        final User user = createUser("session_limit", PASSWORD, "CASHIER");
        login("session_limit", PASSWORD, "client-a");
        login("session_limit", PASSWORD, "client-b");
        login("session_limit", PASSWORD, "client-c");

        assertThatThrownBy(() -> login("session_limit", PASSWORD, "client-d"))
                .isInstanceOf(IdentityException.class)
                .extracting("code")
                .isEqualTo(ApiErrorCode.AUTH_CONCURRENT_SESSION_LIMIT);

        authService.revokeUserSessions(user.getId(), "FORCE_LOGOUT");

        assertThat(sessionRepository.findByUserIdAndRevokedAtIsNull(user.getId())).allSatisfy(session ->
                assertThat(session.getRevokedAt()).isNotNull());
    }

    @Test
    void secRbac001To005PermissionsUsePrimaryActiveSecondaryExpiryAndRevocation() {
        final User user = createUser("rbac_union", PASSWORD, "CASHIER");
        final Role manager = roleRepository.findByCode("MANAGER").orElseThrow();

        assertThat(permissionService.effectivePermissions(user.getId()))
                .contains("invoice.create")
                .doesNotContain("role.update");

        final var grant = roleService.grantSecondaryRole(user.getId(),
                new SecondaryRoleGrantRequest(manager.getId(), Instant.now().plusSeconds(3600)));
        assertThat(permissionService.effectivePermissions(user.getId())).contains("role.update");

        roleService.revokeSecondaryRole(grant.id());
        assertThat(permissionService.effectivePermissions(user.getId())).doesNotContain("role.update");

        jdbc.update("""
                insert into user_role_assignments(user_id, role_id, granted_at, expires_at, is_active)
                values (?, ?, ?, ?, true)
                """, user.getId(), manager.getId(), Timestamp.from(Instant.now().minusSeconds(7200)),
                Timestamp.from(Instant.now().minusSeconds(3600)));
        assertThat(permissionService.effectivePermissions(user.getId())).doesNotContain("role.update");
    }

    @Test
    void secRbac006To008ProtectPrimaryRoleAndSystemRolesAndCoverSuperAdmin() {
        final User user = createUser("rbac_protection", PASSWORD, "CASHIER");
        final Role cashier = roleRepository.findByCode("CASHIER").orElseThrow();
        final Role superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();

        assertThatThrownBy(() -> roleService.grantSecondaryRole(user.getId(),
                new SecondaryRoleGrantRequest(cashier.getId(), Instant.now().plusSeconds(3600))))
                .isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> roleService.deleteRole(superAdmin.getId()))
                .isInstanceOf(IdentityException.class);

        final long missingSuperAdminPermissions = jdbc.queryForObject("""
                select count(*)
                from permissions p
                where not exists (
                    select 1
                    from role_permissions rp
                    where rp.role_id = ?
                    and rp.permission_code = p.permission_code
                )
                """, Long.class, superAdmin.getId());
        assertThat(missingSuperAdminPermissions).isZero();
    }

    @Test
    void permissionAssignmentRemovalAndCustomRoleRemainSupported() {
        final String roleCode = "CUSTOM_PHASE1_TEST";
        final RoleResponse created = roleService.createRole(new RoleUpsertRequest(roleCode, "Custom phase 1",
                Map.of("invoice.create", true, "invoice.void", true), true, 0));

        assertThat(created.permissions()).containsEntry("invoice.void", true);

        final RoleResponse updated = roleService.updateRole(created.id(), new RoleUpsertRequest(roleCode,
                "Custom phase 1", Map.of("invoice.create", true, "invoice.void", false), true, created.version()));

        assertThat(updated.permissions()).isEqualTo(Map.of("invoice.create", true));
        roleService.deleteRole(created.id());
        assertThat(roleRepository.findById(created.id())).isEmpty();
    }

    private LoginResponse login(final String username, final String password, final String clientId) {
        return authService.login(new LoginRequest(username, password, clientId), "127.0.0.1");
    }

    private User createUser(final String username, final String rawPassword, final String roleCode) {
        final Role role = roleRepository.findByCode(roleCode).orElseThrow();
        final User user = new User(username, username, passwordEncoder.encode(rawPassword), role);
        return userRepository.saveAndFlush(user);
    }
}
