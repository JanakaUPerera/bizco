package com.bizco.server.system.service;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.common.dto.system.SystemRequests.SystemConfigUpsertRequest;
import com.bizco.common.dto.system.SystemResponses.SystemConfigEntryResponse;
import com.bizco.common.dto.system.SystemResponses.SystemConfigListResponse;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.system.entity.SystemConfigEntry;
import com.bizco.server.system.repository.SystemConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SystemConfigService(final SystemConfigRepository repository, final AuditService auditService,
                               final UserRepository userRepository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SystemConfigListResponse list() {
        return new SystemConfigListResponse(repository.findAll().stream()
                .map(this::toResponse)
                .sorted((a, b) -> a.configKey().compareTo(b.configKey()))
                .toList());
    }

    @Transactional(readOnly = true)
    public SystemConfigEntryResponse get(final String configKey) {
        return repository.findById(configKey)
                .map(this::toResponse)
                .orElseThrow(() -> new IdentityException(ApiErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "System configuration key was not found"));
    }

    @Transactional
    public SystemConfigEntryResponse upsert(final String configKey, final SystemConfigUpsertRequest request,
                                            final Authentication authentication) {
        final String newValueJson = encode(request.configValue());
        final SystemConfigEntry entry = repository.findById(configKey)
                .orElseGet(() -> new SystemConfigEntry(configKey, newValueJson, request.description()));
        if (entry.getVersion() != request.version()) {
            throw new IdentityException(ApiErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT,
                    "System configuration was modified by another user");
        }
        final String previousValueJson = entry.getConfigValueJson();
        entry.update(newValueJson, request.description(), actor(authentication));
        final SystemConfigEntry saved = repository.save(entry);
        auditService.record("SYSTEM_CONFIG", configKey, "SYSTEM_CONFIG_UPDATED", actor(authentication),
                Map.of("configKey", configKey), Map.of("configValue", String.valueOf(previousValueJson)), null, null);
        return toResponse(saved);
    }

    private SystemConfigEntryResponse toResponse(final SystemConfigEntry entry) {
        return new SystemConfigEntryResponse(entry.getConfigKey(), decode(entry.getConfigValueJson()),
                entry.getDescription(), entry.getUpdatedAt(), entry.getVersion());
    }

    private String encode(final Object configValue) {
        try {
            return objectMapper.writeValueAsString(configValue);
        } catch (final JsonProcessingException exception) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Configuration value could not be encoded as JSON");
        }
    }

    private Object decode(final String configValueJson) {
        try {
            return objectMapper.readValue(configValueJson, Object.class);
        } catch (final JsonProcessingException exception) {
            return configValueJson;
        }
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .map(user -> user.getId()).orElse(null);
    }
}
