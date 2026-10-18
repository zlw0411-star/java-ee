package com.restaurant.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 通过 Nacos 服务发现调用用户服务，获取顾客的会员折扣率。
 * 服务名对应 user-service 的 spring.application.name。
 */
@FeignClient(name = "restaurant-user-service")
public interface UserClient {
    @GetMapping("/users/{id}/discount")
    Map<String, Object> discount(@PathVariable("id") Long id);
}
