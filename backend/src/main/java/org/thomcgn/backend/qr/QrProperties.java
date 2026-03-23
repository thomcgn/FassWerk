package org.thomcgn.backend.qr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.qr")
public record QrProperties(String scanBaseUrl) {
}

