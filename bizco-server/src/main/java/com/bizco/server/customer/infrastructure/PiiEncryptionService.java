package com.bizco.server.customer.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PiiEncryptionService {

    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String configuredKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiEncryptionService(@Value("${bizco.security.pii-key-base64:}") final String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public byte[] encrypt(final String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            final byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            final byte[] encrypted = cipher.doFinal(plaintext.trim().getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE_BYTES + encrypted.length).put(nonce).put(encrypted).array();
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("PII encryption failed.", ex);
        }
    }

    public String decrypt(final byte[] ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            final ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            final byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            final byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("PII decryption failed.", ex);
        }
    }

    private SecretKeySpec key() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("Customer PII encryption key is not configured.");
        }
        final byte[] rawKey = Base64.getDecoder().decode(configuredKey);
        if (rawKey.length != 32) {
            throw new IllegalStateException("Customer PII encryption key must be 256 bits.");
        }
        return new SecretKeySpec(Arrays.copyOf(rawKey, rawKey.length), "AES");
    }
}

