package com.bizco.server.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bizco.common.dto.identity.UserResponses.LoginHistorySearchResponse;
import com.bizco.server.identity.entity.LoginHistory;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class LoginHistoryServiceTest {

    private final LoginHistoryRepository repository = mock(LoginHistoryRepository.class);
    private final LoginHistoryService service = new LoginHistoryService(repository);

    @Test
    void searchWithoutUserIdReturnsAllHistoryNewestFirst() {
        final LoginHistory entry = new LoginHistory(null, "unknown-user", "client-1", "10.0.0.5", false, "Invalid credentials");
        final Pageable pageable = PageRequest.of(0, 20);
        when(repository.findAllByOrderByAttemptedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry), pageable, 1));

        final LoginHistorySearchResponse response = service.search(null, 0, 20);

        assertEquals(1, response.data().size());
        assertEquals("unknown-user", response.data().get(0).attemptedUsername());
        assertEquals("10.0.0.5", response.data().get(0).ipAddress());
    }

    @Test
    void searchWithUserIdFiltersByUser() {
        final UUID userId = UUID.randomUUID();
        final Page<LoginHistory> empty = new PageImpl<>(List.of());
        when(repository.findByUserIdOrderByAttemptedAtDesc(eq(userId), any(Pageable.class))).thenReturn(empty);

        service.search(userId, 0, 20);

        verify(repository).findByUserIdOrderByAttemptedAtDesc(eq(userId), any(Pageable.class));
    }
}
