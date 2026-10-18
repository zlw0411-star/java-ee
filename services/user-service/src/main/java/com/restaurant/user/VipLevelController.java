package com.restaurant.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/vip-levels")
public class VipLevelController {
    private final VipLevelRepository vipLevels;
    private final TokenUtil tokens;
    public VipLevelController(VipLevelRepository vipLevels, TokenUtil tokens) { this.vipLevels = vipLevels; this.tokens = tokens; }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        return ResponseEntity.ok(vipLevels.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String auth, @RequestBody VipRequest r) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        VipLevel v = new VipLevel();
        v.setName(r.name());
        v.setDiscount(r.discount());
        return ResponseEntity.status(HttpStatus.CREATED).body(vipLevels.save(v));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable("id") Long id, @RequestBody VipRequest r) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        return vipLevels.findById(id).map(v -> {
            v.setName(r.name());
            v.setDiscount(r.discount());
            return ResponseEntity.ok(vipLevels.save(v));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable("id") Long id) {
        if (tokens.fromHeader(auth) == null) return unauthorized();
        if (!admin(auth)) return forbidden();
        vipLevels.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record VipRequest(String name, BigDecimal discount) {}

    private boolean admin(String auth) { String[] p = tokens.fromHeader(auth); return p != null && p[2].equals("ADMIN"); }
    private ResponseEntity<?> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可操作")); }
    private ResponseEntity<?> unauthorized() { return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录")); }
}
