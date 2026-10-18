package com.restaurant.order;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping
public class OrderController {
    private final TokenUtil tokens;
    private final UserClient userClient;
    private final DiningTableRepository tables; private final DishRepository dishes; private final DishCategoryRepository categories; private final RestaurantOrderRepository orders; private final OrderItemRepository itemRepository;
    public OrderController(TokenUtil tokens, UserClient userClient, DiningTableRepository tables, DishRepository dishes, DishCategoryRepository categories, RestaurantOrderRepository orders, OrderItemRepository itemRepository){this.tokens=tokens;this.userClient=userClient;this.tables=tables;this.dishes=dishes;this.categories=categories;this.orders=orders;this.itemRepository=itemRepository;}

    @GetMapping("/tables") public List<DiningTable> tables(){return tables.findAll();}
    @GetMapping("/dishes") public List<Dish> dishes(){return dishes.findByEnabledTrueOrderByIdDesc();}
    @GetMapping("/categories") public List<DishCategory> categories(){return categories.findAll();}
    @GetMapping("/orders/my") public ResponseEntity<?> my(@RequestHeader(value="Authorization", required=false) String authorization, @RequestParam("customerId") Long customerId){
        String[] identity=tokens.fromHeader(authorization);
        if(identity==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","请先登录"));
        boolean privileged = identity[2].equals("WAITER") || identity[2].equals("ADMIN");
        if(!privileged && !identity[0].equals(String.valueOf(customerId))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权查看他人订单"));
        return ResponseEntity.ok(orders.findByCustomerIdOrderByCreatedAtDesc(customerId));
    }
    @GetMapping("/orders") public ResponseEntity<?> all(@RequestHeader(value="Authorization", required=false) String authorization){
        String[] identity=identity(authorization);
        if(identity==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","请先登录"));
        if(!(identity[2].equals("WAITER")||identity[2].equals("ADMIN"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权查看全部订单"));
        return ResponseEntity.ok(orders.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/orders") @Transactional
    public ResponseEntity<?> create(@RequestHeader(value="Authorization", required=false) String authorization, @Valid @RequestBody CreateOrderRequest request){
        String[] identity = identity(authorization);
        if (identity == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","请先登录"));
        if (!identity[0].equals(String.valueOf(request.creatorId()))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","创建人身份不匹配"));
        if (request.mode()==OrderMode.WAITER && !(identity[2].equals("WAITER") || identity[2].equals("ADMIN"))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","只有服务员或管理员可以代客点餐"));
        if (request.mode()==OrderMode.CUSTOMER && !identity[2].equals("CUSTOMER")) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","只有顾客可以自助点餐"));
        if (request.mode()==OrderMode.CUSTOMER) {
            if (request.customerId() == null) return ResponseEntity.badRequest().body(Map.of("message","顾客点餐必须提供顾客编号"));
            if (!identity[0].equals(String.valueOf(request.customerId()))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","顾客编号与登录身份不匹配"));
        }
        DiningTable table = tables.lockById(request.tableId()).orElse(null);
        if(table==null) return ResponseEntity.notFound().build();
        if(request.mode()==OrderMode.CUSTOMER && (request.tableCode()==null || !request.tableCode().equals(table.getTableCode())))
            return ResponseEntity.badRequest().body(Map.of("message","顾客点餐必须提供正确桌台码和顾客编号"));
        Optional<RestaurantOrder> active = orders.findFirstByTableIdAndStatusIn(table.getId(), List.of(OrderStatus.DRAFT, OrderStatus.SUBMITTED));
        if(active.isPresent()){
            RestaurantOrder ex = active.get();
            if(request.mode()==OrderMode.CUSTOMER){
                if(ex.getCustomerId()==null){
                    ex.setCustomerId(request.customerId());
                    ex.setDiscountRate(resolveDiscountRate(OrderMode.CUSTOMER, request.customerId(), request.discountRate()));
                    ex.recalculate();
                    return ResponseEntity.ok(orders.save(ex));
                }
                if(ex.getCustomerId().equals(request.customerId())) return ResponseEntity.ok(ex);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","本桌订单已由其他顾客加入，请联系服务员"));
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","餐桌已被占用"));
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = date + "-" + table.getTableNo() + "-";
        long seq = orders.countByOrderNoStartingWith(prefix) + 1;
        RestaurantOrder order = new RestaurantOrder(); order.setOrderNo(prefix + String.format("%03d", seq)); order.setTable(table); order.setOrderMode(request.mode()); order.setCreatorId(request.creatorId()); order.setCustomerId(request.customerId()); order.setDiscountRate(resolveDiscountRate(request.mode(), request.customerId(), request.discountRate()));
        table.setStatus(TableStatus.OCCUPIED); tables.save(table); return ResponseEntity.status(HttpStatus.CREATED).body(orders.save(order));
    }

    @PostMapping("/orders/{id}/items") @Transactional
    public ResponseEntity<?> addItem(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable("id") Long id, @Valid @RequestBody AddItemRequest request){
        RestaurantOrder order=orders.findById(id).orElse(null); Dish dish=dishes.findById(request.dishId()).orElse(null);
        if(order==null||dish==null) return ResponseEntity.notFound().build();
        String[] identity=identity(authorization); if(!canEdit(order, identity)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","当前角色不能编辑此订单"));
        if(order.getStatus()!=OrderStatus.DRAFT && order.getStatus()!=OrderStatus.SUBMITTED) return ResponseEntity.badRequest().body(Map.of("message","订单已结束"));
        OrderItem item=new OrderItem(); item.setOrder(order); item.setDish(dish); item.setQuantity(request.quantity()); item.setPrice(dish.getPrice());
        OrderItem savedItem = itemRepository.save(item); order.getItems().add(savedItem); order.recalculate(); return ResponseEntity.ok(orders.save(order));
    }

    @PutMapping("/orders/{id}/items/{itemId}") @Transactional
    public ResponseEntity<?> updateItem(@RequestHeader(value="Authorization", required=false) String authorization,@PathVariable("id") Long id,@PathVariable("itemId") Long itemId,@Valid @RequestBody UpdateItemRequest request){
        RestaurantOrder order=orders.findById(id).orElse(null); OrderItem item=itemRepository.findById(itemId).orElse(null);
        if(order==null||item==null||!item.getOrder().getId().equals(id)) return ResponseEntity.notFound().build();
        if(!canEdit(order,identity(authorization))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","当前角色不能编辑此订单"));
        item.setQuantity(request.quantity()); itemRepository.save(item); order.recalculate(); return ResponseEntity.ok(orders.save(order));
    }

    @DeleteMapping("/orders/{id}/items/{itemId}") @Transactional
    public ResponseEntity<?> deleteItem(@RequestHeader(value="Authorization", required=false) String authorization,@PathVariable("id") Long id,@PathVariable("itemId") Long itemId){
        RestaurantOrder order=orders.findById(id).orElse(null); OrderItem item=itemRepository.findById(itemId).orElse(null);
        if(order==null||item==null||!item.getOrder().getId().equals(id)) return ResponseEntity.notFound().build();
        if(!canEdit(order,identity(authorization))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","当前角色不能编辑此订单"));
        order.getItems().removeIf(existing->existing.getId().equals(itemId)); itemRepository.delete(item); order.recalculate(); return ResponseEntity.ok(orders.save(order));
    }

    @PostMapping("/orders/{id}/submit") @Transactional
    public ResponseEntity<?> submit(@RequestHeader(value="Authorization", required=false) String authorization,@PathVariable("id") Long id){
        RestaurantOrder order=orders.findById(id).orElse(null); if(order==null)return ResponseEntity.notFound().build();
        if(!canEdit(order,identity(authorization))) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权提交此订单"));
        if(order.getStatus()!=OrderStatus.DRAFT) return ResponseEntity.badRequest().body(Map.of("message","仅未提交订单可提交"));
        if(order.getItems().isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","请至少选择一个菜品"));
        order.setStatus(OrderStatus.SUBMITTED); return ResponseEntity.ok(orders.save(order));
    }

    @PostMapping("/orders/{id}/settle") @Transactional
    public ResponseEntity<?> settle(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable("id") Long id){
        RestaurantOrder order=orders.findById(id).orElse(null); if(order==null)return ResponseEntity.notFound().build();
        String[] identity=identity(authorization); if(!canClose(order, identity)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权结算此订单"));
        if(order.getStatus()!=OrderStatus.SUBMITTED) return ResponseEntity.badRequest().body(Map.of("message","仅已提交订单可结算"));
        order.setStatus(OrderStatus.SETTLED); order.getTable().setStatus(TableStatus.AVAILABLE); order.setSettledAt(LocalDateTime.now()); orders.save(order); tables.save(order.getTable()); return ResponseEntity.ok(order);
    }
    @PostMapping("/orders/{id}/cancel") @Transactional
    public ResponseEntity<?> cancel(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable("id") Long id){
        RestaurantOrder order=orders.findById(id).orElse(null); if(order==null)return ResponseEntity.notFound().build();
        String[] identity=identity(authorization); if(!canClose(order, identity)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权取消此订单"));
        if(order.getStatus()!=OrderStatus.DRAFT && order.getStatus()!=OrderStatus.SUBMITTED) return ResponseEntity.badRequest().body(Map.of("message","仅未结束订单可取消"));
        order.setStatus(OrderStatus.CANCELLED); order.getTable().setStatus(TableStatus.AVAILABLE); orders.save(order); tables.save(order.getTable()); return ResponseEntity.ok(order);
    }
    @GetMapping("/orders/{id}") public ResponseEntity<?> get(@RequestHeader(value="Authorization", required=false) String authorization, @PathVariable("id") Long id){
        String[] identity=tokens.fromHeader(authorization);
        if(identity==null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","请先登录"));
        RestaurantOrder order=orders.findById(id).orElse(null); if(order==null) return ResponseEntity.notFound().build();
        if(identity[2].equals("CUSTOMER") && !String.valueOf(order.getCustomerId()).equals(identity[0])) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","无权查看他人订单"));
        return ResponseEntity.ok(order);
    }

    public record CreateOrderRequest(@NotNull Long tableId, @NotNull OrderMode mode, @NotNull Long creatorId, Long customerId, String tableCode, BigDecimal discountRate){}
    public record AddItemRequest(@NotNull Long dishId, @NotNull @Min(1) Integer quantity){}
    public record UpdateItemRequest(@NotNull @Min(1) Integer quantity){}

    private BigDecimal resolveDiscountRate(OrderMode mode, Long customerId, BigDecimal requested) {
        if (mode == OrderMode.CUSTOMER) {
            if (customerId == null) return BigDecimal.ONE;
            try {
                Object d = userClient.discount(customerId).get("discount");
                return d == null ? BigDecimal.ONE : new BigDecimal(d.toString());
            } catch (Exception e) { return BigDecimal.ONE; }
        }
        if (requested != null && requested.compareTo(BigDecimal.ZERO) > 0 && requested.compareTo(BigDecimal.ONE) <= 0) return requested;
        return BigDecimal.ONE;
    }

    private String[] identity(String authorization) {
        return tokens.fromHeader(authorization);
    }

    private boolean canEdit(RestaurantOrder order, String[] identity) {
        if(identity==null) return false;
        if(identity[2].equals("WAITER") || identity[2].equals("ADMIN")) return true;
        return identity[2].equals("CUSTOMER") && String.valueOf(order.getCustomerId()).equals(identity[0]);
    }

    private boolean canClose(RestaurantOrder order, String[] identity) {
        if(identity==null) return false;
        if(identity[2].equals("WAITER") || identity[2].equals("ADMIN")) return true;
        return identity[2].equals("CUSTOMER") && String.valueOf(order.getCustomerId()).equals(identity[0]);
    }
}
