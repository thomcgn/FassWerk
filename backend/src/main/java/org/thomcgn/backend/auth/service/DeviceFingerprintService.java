package org.thomcgn.backend.auth.service;

import org.springframework.stereotype.Service;

@Service
public class DeviceFingerprintService {

    public String toDeviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();
        String os = detectOs(ua);
        String browser = detectBrowser(ua);
        String deviceClass = detectDeviceClass(ua);

        return browser + " on " + os + " (" + deviceClass + ")";
    }

    private String detectOs(String ua) {
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Other OS";
    }

    private String detectBrowser(String ua) {
        if (ua.contains("edg/")) {
            return "Edge";
        }
        if (ua.contains("chrome/") && !ua.contains("edg/")) {
            return "Chrome";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        }
        return "Other Browser";
    }

    private String detectDeviceClass(String ua) {
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "Mobile";
        }
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "Tablet";
        }
        return "Desktop";
    }
}

