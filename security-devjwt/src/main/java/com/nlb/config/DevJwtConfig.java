package com.nlb.config;

import java.time.*;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

@Configuration
@Profile({"dev","default"})
public class DevJwtConfig {

    @Value("${security.devjwt.secret:change-me-change-me-change-me-32-bytes-min}")
    private String secret;

    private SecretKeySpec key() { return new SecretKeySpec(secret.getBytes(), "HmacSHA256"); }

    @Bean
    public NimbusJwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(key()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key()));
    }
}