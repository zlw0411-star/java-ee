package com.restaurant.order;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
public class ReportController {
    @PersistenceContext EntityManager entityManager;
    private final TokenUtil tokens;
    public ReportController(TokenUtil tokens) { this.tokens = tokens; }

    @GetMapping("/revenue")
    public ResponseEntity<?> revenue(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        String[] identity = tokens.fromHeader(auth);
        if (identity == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录"));
        if (!identity[2].equals("ADMIN")) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可查看营收"));
        List<?> rows = entityManager.createNativeQuery("CALL p_revenue_report(:from, :to)")
                .setParameter("from", from).setParameter("to", to).getResultList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/hot-dishes")
    public ResponseEntity<?> hotDishes(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        if (tokens.fromHeader(auth) == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录"));
        List<?> rows = entityManager.createNativeQuery("CALL p_hot_dishes(:from, :to)")
                .setParameter("from", from).setParameter("to", to).getResultList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/revenue/daily")
    public ResponseEntity<?> revenueDaily(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to) {
        String[] identity = tokens.fromHeader(auth);
        if (identity == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "请先登录"));
        if (!identity[2].equals("ADMIN")) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可查看营收"));
        List<?> rows = entityManager.createNativeQuery("CALL p_revenue_daily(:from, :to)")
                .setParameter("from", from).setParameter("to", to).getResultList();
        return ResponseEntity.ok(rows);
    }
}
