package com.freestone.pettycash.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Property mappings for JWT configuration, bound to app.security.jwt prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "app.security.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for HS256 JWT signature verification. Must be at least 256 bits (32 characters).
     */
    private String secret = "defaultsecretkeythatislongerthan32charactersforhs256signatureverificationkey";

    /**
     * Expiration duration in hours. Default is 8 hours.
     */
    private int expirationHours = 8;
}
