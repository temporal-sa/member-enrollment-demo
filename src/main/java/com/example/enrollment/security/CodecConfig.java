package com.example.enrollment.security;

import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CodecConfig {

    @Bean
    public PhiCodec phiCodec(
            @Value("${enrollment.codec.key-id}") String keyId, @Value("${enrollment.codec.key}") String key) {
        // Only one key in the demo. A real lookup would resolve any key ID from a vault.
        return new PhiCodec(keyId, id -> PhiCodec.keyFromString(key));
    }

    /**
     * When ENCRYPT_PAYLOADS=true, the Temporal Spring Boot starter uses this converter for the
     * client and the workers. Failure messages are encoded too (the "true" argument), because
     * exception text can contain PHI.
     */
    @Bean
    @ConditionalOnProperty(name = "enrollment.encrypt", havingValue = "true")
    public DataConverter encryptedDataConverter(PhiCodec codec) {
        return new CodecDataConverter(DefaultDataConverter.newDefaultInstance(), List.of(codec), true);
    }
}
