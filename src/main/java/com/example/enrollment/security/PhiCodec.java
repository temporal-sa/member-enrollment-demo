package com.example.enrollment.security;

import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.EncodingKeys;
import io.temporal.payload.codec.PayloadCodec;
import io.temporal.payload.codec.PayloadCodecException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM payload codec. The key ID is
 * stored in each payload's metadata, so keys can be rotated: new payloads use the current key
 * while old ones are still decrypted with the key they were written with.
 *
 * The key lookup is a function so the source can change without touching the codec. In a real
 * deployment that function would call a key manager such as HashiCorp Vault, AWS KMS or Azure Key Vault and cache the result.
 */
public class PhiCodec implements PayloadCodec {

    static final ByteString ENCODING = ByteString.copyFromUtf8("binary/encrypted");
    static final String CIPHER_KEY = "encryption-cipher";
    static final String KEY_ID_KEY = "encryption-key-id";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String currentKeyId;
    private final Function<String, SecretKey> keyLookup;

    public PhiCodec(String currentKeyId, Function<String, SecretKey> keyLookup) {
        this.currentKeyId = currentKeyId;
        this.keyLookup = keyLookup;
    }

    public static SecretKey keyFromString(String key) {
        return new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
    }

    @Override
    public List<Payload> encode(List<Payload> payloads) {
        return payloads.stream().map(this::encrypt).collect(Collectors.toList());
    }

    @Override
    public List<Payload> decode(List<Payload> payloads) {
        return payloads.stream().map(this::decrypt).collect(Collectors.toList());
    }

    private Payload encrypt(Payload payload) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keyLookup.apply(currentKeyId), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(payload.toByteArray());
            byte[] data = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();

            return Payload.newBuilder()
                    .putMetadata(EncodingKeys.METADATA_ENCODING_KEY, ENCODING)
                    .putMetadata(CIPHER_KEY, ByteString.copyFromUtf8(CIPHER))
                    .putMetadata(KEY_ID_KEY, ByteString.copyFromUtf8(currentKeyId))
                    .setData(ByteString.copyFrom(data))
                    .build();
        } catch (Exception e) {
            throw new DataConverterException(e);
        }
    }

    private Payload decrypt(Payload payload) {
        if (!ENCODING.equals(payload.getMetadataOrDefault(EncodingKeys.METADATA_ENCODING_KEY, null))) {
            return payload; // not one of ours, pass through
        }
        try {
            String keyId = payload.getMetadataOrThrow(KEY_ID_KEY).toString(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(payload.getData().toByteArray());
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keyLookup.apply(keyId), new GCMParameterSpec(TAG_BITS, nonce));
            return Payload.parseFrom(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new PayloadCodecException(e);
        }
    }
}
