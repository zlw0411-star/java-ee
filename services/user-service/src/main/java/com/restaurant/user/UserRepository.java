package com.restaurant.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Query(value = "SELECT v.name FROM vip_level v JOIN sys_user u ON u.vip_level_id = v.id WHERE u.id = :id", nativeQuery = true)
    Optional<String> findVipNameById(@Param("id") Long id);

    @Query(value = "SELECT v.discount FROM vip_level v JOIN sys_user u ON u.vip_level_id = v.id WHERE u.id = :id", nativeQuery = true)
    Optional<BigDecimal> findVipDiscountById(@Param("id") Long id);
}
