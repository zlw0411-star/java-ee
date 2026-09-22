package com.restaurant.order;

import jakarta.transaction.Transactional;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final DiningTableRepository tables; private final DishRepository dishes; private final DishCategoryRepository categories;
    private final TokenUtil tokens;
    public AdminController(DiningTableRepository tables, DishRepository dishes, DishCategoryRepository categories, TokenUtil tokens){this.tables=tables;this.dishes=dishes;this.categories=categories;this.tokens=tokens;}

    @PostMapping("/tables") public ResponseEntity<?> createTable(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody TableRequest r){if(!admin(auth))return forbidden(); DiningTable t=new DiningTable();t.setTableNo(r.tableNo());t.setTableCode(r.tableCode());t.setSeats(r.seats());return ResponseEntity.status(HttpStatus.CREATED).body(tables.save(t));}
    @PutMapping("/tables/{id}") public ResponseEntity<?> updateTable(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id,@RequestBody TableRequest r){if(!admin(auth))return forbidden();return tables.findById(id).map(t->{t.setTableNo(r.tableNo());t.setTableCode(r.tableCode());t.setSeats(r.seats());return ResponseEntity.ok(tables.save(t));}).orElseGet(()->ResponseEntity.notFound().build());}
    @DeleteMapping("/tables/{id}") public ResponseEntity<?> deleteTable(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id){if(!admin(auth))return forbidden();tables.deleteById(id);return ResponseEntity.noContent().build();}

    @PostMapping("/categories") public ResponseEntity<?> createCategory(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody NameRequest r){if(!admin(auth))return forbidden();DishCategory c=new DishCategory();c.setName(r.name());return ResponseEntity.status(HttpStatus.CREATED).body(categories.save(c));}
    @PutMapping("/categories/{id}") public ResponseEntity<?> updateCategory(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id,@RequestBody NameRequest r){if(!admin(auth))return forbidden();return categories.findById(id).map(c->{c.setName(r.name());return ResponseEntity.ok(categories.save(c));}).orElseGet(()->ResponseEntity.notFound().build());}
    @DeleteMapping("/categories/{id}") public ResponseEntity<?> deleteCategory(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id){if(!admin(auth))return forbidden();categories.deleteById(id);return ResponseEntity.noContent().build();}

    @GetMapping("/dishes") public ResponseEntity<?> allDishes(@RequestHeader(value="Authorization",required=false) String auth){if(!admin(auth))return forbidden(); return ResponseEntity.ok(dishes.findAll());}
    @PostMapping("/dishes") public ResponseEntity<?> createDish(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody DishRequest r){if(!admin(auth))return forbidden();DishCategory c=categories.findById(r.categoryId()).orElse(null);if(c==null)return ResponseEntity.badRequest().body(Map.of("message","分类不存在"));Dish d=new Dish();d.setCode(r.code());d.setName(r.name());d.setCategory(c);d.setPrice(r.price());d.setImageUrl(r.imageUrl());d.setEnabled(r.enabled()==null||r.enabled());return ResponseEntity.status(HttpStatus.CREATED).body(dishes.save(d));}
    @PutMapping("/dishes/{id}") public ResponseEntity<?> updateDish(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id,@RequestBody DishRequest r){if(!admin(auth))return forbidden();return dishes.findById(id).map(d->{DishCategory c=categories.findById(r.categoryId()).orElse(null);if(c==null)return ResponseEntity.badRequest().body(Map.of("message","分类不存在"));d.setCode(r.code());d.setName(r.name());d.setCategory(c);d.setPrice(r.price());d.setImageUrl(r.imageUrl());if(r.enabled()!=null)d.setEnabled(r.enabled());return ResponseEntity.ok(dishes.save(d));}).orElseGet(()->ResponseEntity.notFound().build());}
    @DeleteMapping("/dishes/{id}") public ResponseEntity<?> deleteDish(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable("id") Long id){if(!admin(auth))return forbidden();dishes.deleteById(id);return ResponseEntity.noContent().build();}

    public record TableRequest(String tableNo,String tableCode,Integer seats){} public record NameRequest(String name){}
    public record DishRequest(String code,String name,Long categoryId,BigDecimal price,String imageUrl,Boolean enabled){}
    private boolean admin(String auth){String[] p=tokens.fromHeader(auth);return p!=null&&p[2].equals("ADMIN");}
    private ResponseEntity<?> forbidden(){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","仅管理员可以操作基础资料"));}
}
