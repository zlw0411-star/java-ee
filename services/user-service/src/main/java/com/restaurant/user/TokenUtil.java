package com.restaurant.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 带 HMAC-SHA256 签名的一次性 Token 工具。
 * 格式：base64url(id:username:role:expMillis) + "." + base64url(hmac)
 * 不知道密钥无法伪造；过期自动失效。
 */
@Component
public class TokenUtil {
    private final byte[] secret;
    private final long ttlMillis;

    /** 仓库内旧默认密钥的残留值；若被当作真实密钥使用则直接拒绝启动。 */
    private static final String FORBIDDEN_LEGACY_SECRET = "change-me-restaurant-token-secret";

    public TokenUtil(@Value("${app.token.secret:}") String secret,
                     @Value("${app.token.ttl-minutes:720}") long ttlMinutes) {
        String value = secret == null ? "" : secret.strip();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "缺少 app.token.secret / APP_TOKEN_SECRET：请通过环境变量提供本机密钥，仓库不提供可用的默认值。");
        }
        if (value.equals(FORBIDDEN_LEGACY_SECRET)) {
            throw new IllegalStateException(
                    "app.token.secret / APP_TOKEN_SECRET 仍在用例占位值，请替换为本机私有的随机密钥。");
        }
        this.secret = value.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    public String issue(Long id, String username, String role) {
        long exp = System.currentTimeMillis() + ttlMillis;
        String payload = id + ":" + username + ":" + role + ":" + exp;
        String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + sign(p);
    }

    /** 校验并解析，返回 [id, username, role, exp]；无效或过期返回 null。 */
    public String[] verify(String token) {
        if (token == null || token.isBlank()) return null;
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return null;
        String p = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), sign(p).getBytes(StandardCharsets.UTF_8))) return null;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(p), StandardCharsets.UTF_8);
            String[] parts = payload.split(":", 4);
            if (parts.length != 4) return null;
            if (System.currentTimeMillis() > Long.parseLong(parts[3])) return null;
            return parts;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 Authorization: Bearer xxx 解析。 */
    public String[] fromHeader(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return verify(authorization.substring(7));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 签名失败", e);
        }
    }
}
