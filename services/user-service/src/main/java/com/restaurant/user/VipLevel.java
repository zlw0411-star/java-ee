package com.restaurant.user;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "vip_level")
public class VipLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 30) String name;
    @Column(nullable = false, precision = 5, scale = 2) BigDecimal discount = BigDecimal.ONE;
    public Long getId(){return id;}
    public String getName(){return name;}
    public BigDecimal getDiscount(){return discount;}
    public void setName(String value){name=value;}
    public void setDiscount(BigDecimal value){discount=value;}
}
