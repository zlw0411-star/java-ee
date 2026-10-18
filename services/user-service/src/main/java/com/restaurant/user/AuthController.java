package com.restaurant.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final TokenUtil tokens;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public AuthController(UserRepository users, TokenUtil tokens) { this.users = users; this.tokens = tokens; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (users.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "用户名已存在"));
        }
        String role = request.role() == null || request.role().isBlank() ? "CUSTOMER" : request.role();
        if (!role.equals("CUSTOMER")) role = "CUSTOMER";
        User user = users.save(new User(request.username(), encoder.encode(request.password()), request.displayName(), role));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByUsername(request.username()).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(request.password(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "用户名或密码错误"));
        }
        String token = tokens.issue(user.getId(), user.getUsername(), user.getRole());
        java.util.Map<String, Object> info = new java.util.HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("displayName", user.getDisplayName());
        info.put("role", user.getRole());
        users.findVipNameById(user.getId()).ifPresent(v -> info.put("vipName", v));
        users.findVipDiscountById(user.getId()).ifPresent(v -> info.put("vipDiscount", v));
        return ResponseEntity.ok(Map.of("token", token, "user", info));
    }

    public record RegisterRequest(@NotBlank @Pattern(regexp = "^[^:\\s]{2,50}$", message = "用户名不能包含冒号或空格，长度2-50") String username,
                                   @NotBlank @Size(min = 8) String password,
                                   @NotBlank String displayName, String role) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
