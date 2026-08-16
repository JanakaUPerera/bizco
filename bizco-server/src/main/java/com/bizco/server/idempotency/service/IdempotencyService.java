package com.bizco.server.idempotency.service;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.idempotency.repository.IdempotencyRecordRepository;
import com.bizco.server.idempotency.repository.IdempotencyRecordRepository.ExistingRecord;
import com.bizco.server.identity.service.IdentityException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps a critical posting command (invoice post, payment, refund, credit note, GRN post,
 * supplier return, supplier payment, job part consumption, stock adjustment approval, cash
 * closing, backup, restore - ApiContracts.md &sect;3.3) so that a client retry with the same
 * {@code Idempotency-Key} and the same logical request replays the original result instead of
 * running the command again, and a retry with the same key but a different request is rejected
 * with {@code IDEMPOTENCY_CONFLICT} (ApiContracts.md &sect;49).
 *
 * <p>Callers must already be inside the same transaction as the business action - see
 * {@link Propagation#MANDATORY} below - so that if the action rolls back, the claim on the
 * Idempotency-Key rolls back with it and a client can safely retry a genuinely failed request.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(final IdempotencyRecordRepository repository, final ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResult<T> execute(final UUID requestId, final String operation, final Object requestPayload,
                                           final Class<T> responseType, final Supplier<T> action) {
        final String hash = hash(requestPayload);
        if (repository.claim(requestId, operation, hash)) {
            final T response = action.get();
            repository.complete(requestId, HttpStatus.OK.value(), encode(response), null, null);
            return new IdempotentResult<>(response, false);
        }
        final ExistingRecord existing = repository.find(requestId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT,
                "Idempotency-Key is being processed by a concurrent request; retry shortly"));
        if (!existing.operation().equals(operation) || !existing.requestHash().equals(hash)) {
            throw new IdentityException(ApiErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT,
                    "Idempotency-Key was already used for a different request");
        }
        if (!existing.isComplete()) {
            throw new IdentityException(ApiErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT,
                    "A request with this Idempotency-Key is already being processed; retry shortly");
        }
        return new IdempotentResult<>(decode(existing.responseBodyJson(), responseType), true);
    }

    private String hash(final Object payload) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] canonical = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        } catch (final JsonProcessingException exception) {
            throw new IdentityException(ApiErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                    "Request payload could not be hashed for idempotency checking");
        }
    }

    private String encode(final Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Idempotent response could not be encoded as JSON", exception);
        }
    }

    private <T> T decode(final String json, final Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotent response could not be decoded", exception);
        }
    }

    /** {@code replayed} tells the caller whether to set the {@code Idempotent-Replay} response header. */
    public record IdempotentResult<T>(T response, boolean replayed) {
    }
}
