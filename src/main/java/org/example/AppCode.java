package org.example;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

public class AppCode {
    private final String appID;
    private final UUID kid;
    private final byte[] keyBytes;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public AppCode(String appID, String kid, String secret) {
        this.appID = appID;
        this.kid = UUID.fromString(kid);
        this.keyBytes = this.hexStringToBytes(secret);
    }

    private byte[] hexStringToBytes(String hex) {
        int length = hex.length();
        byte[] data = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String encode(byte[] bytes) {
        return encoder.encodeToString(bytes);
    }

    private static String encode(Map<String, Object> map) throws JsonProcessingException {
        return encoder.encodeToString(mapper.writeValueAsBytes(map));
    }

    private byte[] createSignature(String message) {
        Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(this.keyBytes, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKeyParams);
        signer.update(message.getBytes(StandardCharsets.UTF_8), 0, message.length());
        return signer.generateSignature();
    }

    public String generate() throws JsonProcessingException {
        Map<String, Object> headerMap = Map.of(
                "typ", "JWT",
                "alg", "EdDSA",
                "kid", this.kid.toString()
        );

        Map<String, Object> claimsMap = Map.of(
                "app_id", this.appID,
                "expire", Instant.now().plusSeconds(10 * 60).getEpochSecond()
        );

        String header = AppCode.encode(headerMap);
        String claims = AppCode.encode(claimsMap);
        String message = header + "." + claims;

        byte[] signature = this.createSignature(message);
        String encodedSignature = AppCode.encode(signature);

        return message + "." + encodedSignature;
    }
}