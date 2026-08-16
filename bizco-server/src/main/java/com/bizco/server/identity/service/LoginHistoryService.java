package com.bizco.server.identity.service;

import com.bizco.common.dto.identity.UserResponses.LoginHistoryEntryResponse;
import com.bizco.common.dto.identity.UserResponses.LoginHistorySearchResponse;
import com.bizco.server.identity.entity.LoginHistory;
import com.bizco.server.identity.repository.LoginHistoryRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginHistoryService {

    private final LoginHistoryRepository repository;

    public LoginHistoryService(final LoginHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LoginHistorySearchResponse search(final UUID userId, final int page, final int size) {
        final Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        final Page<LoginHistory> result = userId == null
                ? repository.findAllByOrderByAttemptedAtDesc(pageable)
                : repository.findByUserIdOrderByAttemptedAtDesc(userId, pageable);
        return new LoginHistorySearchResponse(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private LoginHistoryEntryResponse toResponse(final LoginHistory entry) {
        return new LoginHistoryEntryResponse(entry.getId(), entry.getUserId(), entry.getUsername(),
                entry.getClientId(), entry.getIpAddress() == null ? null : entry.getIpAddress().getHostAddress(),
                entry.isSuccess(), entry.getFailureReason(), entry.getAttemptedAt());
    }
}
