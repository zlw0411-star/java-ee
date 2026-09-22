package com.restaurant.order;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

enum TableStatus { AVAILABLE, OCCUPIED }
enum OrderMode { WAITER, CUSTOMER }
enum OrderStatus { DRAFT, SUBMITTED, SETTLED, CANCELLED }

@Entity @Table(name = "dining_table")
class DiningTable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, unique = true, length = 30) String tableNo;
    @Column(nullable = false, unique = true, length = 50) String tableCode;
    @Column(nullable = false) Integer seats;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20) TableStatus status = TableStatus.AVAILABLE;
    public Long getId(){return id;} public String getTableNo(){return tableNo;} public String getTableCode(){return tableCode;}
    public Integer getSeats(){return seats;} public TableStatus getStatus(){return status;} public void setStatus(TableStatus s){status=s;}
    public void setTableNo(String value){tableNo=value;} public void setTableCode(String value){tableCode=value;} public void setSeats(Integer value){seats=value;}
}

@Entity @Table(name = "dish_category")
class DishCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true, length=50) String name;
    public Long getId(){return id;} public String getName(){return name;} public void setName(String value){name=value;}
}

@Entity @Table(name = "dish")
class Dish {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true, length=30) String code;
    @Column(nullable=false, length=80) String name;
    @ManyToOne(optional=false) @JoinColumn(name="category_id") DishCategory category;
    @Column(nullable=false, precision=10, scale=2) BigDecimal price;
    @Column(length=500) String imageUrl;
    @Column(nullable=false) boolean enabled = true;
    public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public BigDecimal getPrice(){return price;} public boolean isEnabled(){return enabled;}
    public DishCategory getCategory(){return category;} public String getImageUrl(){return imageUrl;}
    public void setCode(String value){code=value;} public void setName(String value){name=value;} public void setCategory(DishCategory value){category=value;}
    public void setPrice(BigDecimal value){price=value;} public void setImageUrl(String value){imageUrl=value;} public void setEnabled(boolean value){enabled=value;}
}

@Entity @Table(name = "orders")
class RestaurantOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true, length=40) String orderNo;
    @ManyToOne(optional=false) @JoinColumn(name="table_id") DiningTable table;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false, length=20) OrderMode orderMode;
    @Column(nullable=false) Long creatorId;
    Long customerId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable=false, length=20) OrderStatus status = OrderStatus.DRAFT;
    @Column(nullable=false, precision=10, scale=2) BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=10, scale=2) BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(nullable=false, precision=5, scale=2) BigDecimal discountRate = BigDecimal.ONE;
    @Column(nullable=false, precision=10, scale=2) BigDecimal payAmount = BigDecimal.ZERO;
    @Column(nullable=false) LocalDateTime createdAt = LocalDateTime.now();
    LocalDateTime settledAt;
    public Long getId(){return id;} public String getOrderNo(){return orderNo;} public DiningTable getTable(){return table;} public void setTable(DiningTable t){table=t;}
    public void setOrderNo(String n){orderNo=n;} public void setOrderMode(OrderMode m){orderMode=m;} public void setCreatorId(Long id){creatorId=id;} public void setCustomerId(Long id){customerId=id;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getSettledAt(){return settledAt;}
    public void setSettledAt(LocalDateTime v){settledAt=v;}
    public void setDiscountRate(BigDecimal r){ if(r != null && r.compareTo(BigDecimal.ZERO)>0 && r.compareTo(BigDecimal.ONE)<=0) discountRate=r; }
    public OrderMode getOrderMode(){return orderMode;} public Long getCreatorId(){return creatorId;} public Long getCustomerId(){return customerId;} public OrderStatus getStatus(){return status;}
    public void setStatus(OrderStatus s){status=s;} public BigDecimal getTotalAmount(){return totalAmount;} public BigDecimal getDiscountAmount(){return discountAmount;} public BigDecimal getPayAmount(){return payAmount;}
    public void recalculate(){ totalAmount = items.stream().map(i -> i.price.multiply(BigDecimal.valueOf(i.quantity))).reduce(BigDecimal.ZERO, BigDecimal::add); discountAmount = totalAmount.multiply(BigDecimal.ONE.subtract(discountRate)); payAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO); }
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL, orphanRemoval=true, fetch=FetchType.EAGER) java.util.List<OrderItem> items = new java.util.ArrayList<>();
    public java.util.List<OrderItem> getItems(){return items;}
}

@Entity @Table(name = "order_item")
class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @JsonIgnore @ManyToOne(optional=false) @JoinColumn(name="order_id") RestaurantOrder order;
    @ManyToOne(optional=false) @JoinColumn(name="dish_id") Dish dish;
    @Column(nullable=false) Integer quantity;
    @Column(nullable=false, precision=10, scale=2) BigDecimal price;
    public Long getId(){return id;} public RestaurantOrder getOrder(){return order;} public Dish getDish(){return dish;} public Integer getQuantity(){return quantity;} public BigDecimal getPrice(){return price;} public void setQuantity(Integer q){quantity=q;}
    public void setOrder(RestaurantOrder o){order=o;} public void setDish(Dish d){dish=d;} public void setPrice(BigDecimal p){price=p;}
}
