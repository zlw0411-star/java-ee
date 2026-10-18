package com.restaurant.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 30)
    private String displayName;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "vip_level_id")
    private Long vipLevelId;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {}
    public User(String username, String passwordHash, String displayName, String role) {
        this.username = username; this.passwordHash = passwordHash; this.displayName = displayName; this.role = role;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Long getVipLevelId() { return vipLevelId; }
    public void setVipLevelId(Long vipLevelId) { this.vipLevelId = vipLevelId; }
    public void setRole(String role) { this.role = role; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
