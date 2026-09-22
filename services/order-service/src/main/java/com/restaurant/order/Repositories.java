package com.restaurant.order;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

interface DiningTableRepository extends JpaRepository<DiningTable, Long> {
    Optional<DiningTable> findByTableCode(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from DiningTable t where t.id=:id") Optional<DiningTable> lockById(@Param("id") Long id);
}
interface DishRepository extends JpaRepository<Dish, Long> { List<Dish> findByEnabledTrueOrderByIdDesc(); }
interface DishCategoryRepository extends JpaRepository<DishCategory, Long> {}
interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, Long> {
    List<RestaurantOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    List<RestaurantOrder> findAllByOrderByCreatedAtDesc();
    Optional<RestaurantOrder> findFirstByTableIdAndStatusIn(Long tableId, Collection<OrderStatus> statuses);
    long countByOrderNoStartingWith(String prefix);
}
interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
