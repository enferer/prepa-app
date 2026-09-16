package app.prepa.infra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration applicative, prefixe {@code prepa} dans application.yml. */
@ConfigurationProperties(prefix = "prepa")
public record AppProperties(Auth auth, Crypto crypto) {

    public record Auth(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Crypto(String key) {}
}
