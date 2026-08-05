package com.cp.ecommerce.adapter.security.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.cp.ecommerce.adapter.SecurityTestConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.JwkSetUriJwtDecoderBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Integration test verifying that the Spring Boot autoconfigured {@link JwtDecoder} correctly retrieves and validates signing
 * keys against a JWKS endpoint stubbed with WireMock, exercising the same {@code jwk-set-uri}/{@code issuer-uri} wiring as
 * {@link WebSecurityConfiguration} in production.
 *
 * <p>
 * Each test signs its JWT with a freshly generated key (unique key ID) so that {@link JwtDecoder}'s internal JWK cache never
 * short-circuits the call to the stubbed JWKS endpoint.
 * </p>
 */
@SpringBootTest
@Import({ SecurityTestConfiguration.class, JwtDecoderWireMockTest.JwtDecoderTestConfiguration.class })
class JwtDecoderWireMockTest {

    private static final String JWKS_PATH = "/realms/ecommerce/protocol/openid-connect/certs";

    private static final String ISSUER = "http://localhost:8081/realms/ecommerce";

    private static final String SUBJECT = "test-user";

    private RSAKey signingKey;

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Autowired
    private transient JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void jwkSetUri(final DynamicPropertyRegistry registry) {

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> wireMock.baseUrl() + JWKS_PATH);
    }

    @BeforeEach
    void setUp() throws Exception {

        signingKey = generateRsaKey();
        stubJwksEndpoint(signingKey);
    }

    @Test
    void shouldDecodeJwtSignedWithKeyPublishedOnJwksEndpoint() throws Exception {

        final String token = createSignedJwt(signingKey, Instant.now().minusSeconds(5), Instant.now().plusSeconds(60));
        final Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(SUBJECT);
        wireMock.verify(getRequestedFor(urlEqualTo(JWKS_PATH)));
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {

        final String token = createSignedJwt(signingKey, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectJwtSignedWithKeyUnknownToJwksEndpoint() throws Exception {

        final RSAKey unknownKey = generateRsaKey();
        final String token = createSignedJwt(unknownKey, Instant.now().minusSeconds(5), Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(BadJwtException.class);
    }

    private static void stubJwksEndpoint(final RSAKey signingKey) {

        wireMock.stubFor(
                get(urlEqualTo(JWKS_PATH)).willReturn(okJson("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}")));
    }

    private static RSAKey generateRsaKey() throws Exception {

        return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    }

    private static String createSignedJwt(final RSAKey signingKey, final Instant issuedAt, final Instant expiresAt)
            throws Exception {

        final JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(SUBJECT)
                .issuer(ISSUER)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();

        final SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        signedJwt.sign(new RSASSASigner(signingKey));

        return signedJwt.serialize();
    }

    /**
     * Nimbus' default JWKS resource retriever times out after 500ms, which is too aggressive on a loaded CI runner and makes
     * the JWKS fetch flaky. This customizer widens the connect/read timeouts used by {@link JwtDecoder} when talking to the
     * (stubbed) JWKS endpoint.
     */
    @TestConfiguration
    static class JwtDecoderTestConfiguration {

        @Bean
        JwkSetUriJwtDecoderBuilderCustomizer jwkSetUriJwtDecoderBuilderCustomizer() {

            return builder -> builder.restOperations(
                    new RestTemplateBuilder().connectTimeout(Duration.ofSeconds(10))
                            .readTimeout(Duration.ofSeconds(10))
                            .build());
        }

    }

}
