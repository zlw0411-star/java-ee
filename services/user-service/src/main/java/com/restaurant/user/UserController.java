package com.restaurant.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository users;
    private final TokenUtil tokens;
    public UserController(UserRepository users, TokenUtil tokens) { this.users = users; this.tokens = tokens; }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        return ResponseEntity.ok(users.findAll().stream().map(UserView::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable("id") Long id) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        return users.findById(id).<ResponseEntity<?>>map(u -> ResponseEntity.ok(UserView.from(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable("id") Long id, @RequestBody UpdateRequest request) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        User user = users.findById(id).orElse(null);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.role() != null && List.of("ADMIN", "WAITER", "CUSTOMER").contains(request.role())) user.setRole(request.role());
        if (request.enabled() != null) user.setEnabled(request.enabled());
        if (request.vipLevelId() != null) user.setVipLevelId(request.vipLevelId() == 0 ? null : request.vipLevelId());
        users.save(user);
        return ResponseEntity.ok(UserView.from(user));
    }

    /** 内部接口：供 order-service 经服务发现调用，返回顾客折扣率（无会员为 1）。仅内网使用。 */
    @GetMapping("/{id}/discount")
    public Map<String, Object> discount(@PathVariable("id") Long id) {
        BigDecimal d = users.findVipDiscountById(id).orElse(BigDecimal.ONE);
        return Map.of("discount", d);
    }

    private boolean admin(String auth) {
        String[] p = tokens.fromHeader(auth);
        return p != null && p[2].equals("ADMIN");
    }
    private ResponseEntity<?> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可操作")); }
    private ResponseEntity<?> unauthorized() { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录")); }

    public record UpdateRequest(String displayName, String role, Boolean enabled, Long vipLevelId) {}
    public record UserView(Long id, String username, String displayName, String role, boolean enabled, Long vipLevelId) {
        static UserView from(User user) { return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isEnabled(), user.getVipLevelId()); }
    }
}
