package com.restaurant.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class UserDataInitializer {
    @Bean
    CommandLineRunner seedUsers(UserRepository users, jakarta.persistence.EntityManager em) {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            createIfMissing(users, encoder, "admin", "Admin123", "System Admin", "ADMIN");
            createIfMissing(users, encoder, "waiter", "Waiter123", "Front Waiter", "WAITER");
            createIfMissing(users, encoder, "customer", "Customer123", "Demo Customer", "CUSTOMER");
            try {
                em.createNativeQuery("UPDATE sys_user SET vip_level_id=(SELECT id FROM vip_level WHERE name='银卡会员') WHERE username='customer' AND vip_level_id IS NULL").executeUpdate();
            } catch (Exception ignored) { }
        };
    }

    private void createIfMissing(UserRepository users, BCryptPasswordEncoder encoder, String username,
                                 String password, String displayName, String role) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new User(username, encoder.encode(password), displayName, role));
        }
    }
}
