package com.restaurant.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderControllerCustomerIdentityTest {
    private TokenUtil tokens;
    private UserClient userClient;
    private DiningTableRepository tables;
    private DishRepository dishes;
    private DishCategoryRepository categories;
    private RestaurantOrderRepository orders;
    private OrderItemRepository items;
    private OrderController controller;
    private DiningTable table;

    @BeforeEach
    void setUp() {
        tokens = new TokenUtil("test-secret", 720);
        userClient = mock(UserClient.class);
        tables = mock(DiningTableRepository.class);
        dishes = mock(DishRepository.class);
        categories = mock(DishCategoryRepository.class);
        orders = mock(RestaurantOrderRepository.class);
        items = mock(OrderItemRepository.class);
        controller = new OrderController(tokens, userClient, tables, dishes, categories, orders, items);
        table = new DiningTable();
        table.id = 9L;
        table.tableNo = "T9";
        table.tableCode = "code9";
        when(tables.lockById(9L)).thenReturn(Optional.of(table));
        when(orders.findFirstByTableIdAndStatusIn(eq(9L), any())).thenReturn(Optional.empty());
        when(orders.countByOrderNoStartingWith(any())).thenReturn(0L);
        when(orders.save(any(RestaurantOrder.class))).thenAnswer(call -> call.getArgument(0));
        when(userClient.discount(any())).thenReturn(Map.of("discount", "1.00"));
    }

    @Test
    void customerCannotCreateOrderForDifferentCustomerBeforeTableOrDiscountAccess() {
        ResponseEntity<?> response = controller.create(customerToken(101L, "CUSTOMER"),
                request(OrderMode.CUSTOMER, 101L, 202L, "code9"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(tables, orders, userClient);
    }

    @Test
    void customerIdIsRequiredBeforeAcquiringTable() {
        ResponseEntity<?> response = controller.create(customerToken(101L, "CUSTOMER"),
                request(OrderMode.CUSTOMER, 101L, null, "code9"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(tables, orders, userClient);
    }

    @Test
    void customerCanCreateOrderForSelf() {
        ResponseEntity<?> response = controller.create(customerToken(101L, "CUSTOMER"),
                request(OrderMode.CUSTOMER, 101L, 101L, "code9"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        RestaurantOrder created = (RestaurantOrder) response.getBody();
        assertEquals(101L, created.getCustomerId());
        verify(tables).lockById(9L);
        verify(userClient).discount(101L);
    }

    @Test
    void customerCanClaimUnassignedActiveOrderOnlyForSelf() {
        RestaurantOrder existing = new RestaurantOrder();
        when(orders.findFirstByTableIdAndStatusIn(eq(9L), any())).thenReturn(Optional.of(existing));

        ResponseEntity<?> denied = controller.create(customerToken(101L, "CUSTOMER"),
                request(OrderMode.CUSTOMER, 101L, 202L, "code9"));
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNull(existing.getCustomerId());

        ResponseEntity<?> accepted = controller.create(customerToken(101L, "CUSTOMER"),
                request(OrderMode.CUSTOMER, 101L, 101L, "code9"));
        assertEquals(HttpStatus.OK, accepted.getStatusCode());
        assertEquals(101L, existing.getCustomerId());
    }

    @Test
    void waiterMayStillCreateOrderForAssignedCustomer() {
        ResponseEntity<?> response = controller.create(customerToken(303L, "WAITER"),
                request(OrderMode.WAITER, 303L, 202L, null));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(202L, ((RestaurantOrder) response.getBody()).getCustomerId());
        verifyNoInteractions(userClient);
    }

    @Test
    void customerCannotResubmitTerminalOrder() {
        for (OrderStatus status : List.of(OrderStatus.SETTLED, OrderStatus.CANCELLED)) {
            RestaurantOrder order = ownedOrder(status, true);
            when(orders.findById((long) status.ordinal() + 1)).thenReturn(Optional.of(order));

            ResponseEntity<?> response = controller.submit(customerToken(101L, "CUSTOMER"), (long) status.ordinal() + 1);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(status, order.getStatus());
        }
        verify(orders, never()).save(any(RestaurantOrder.class));
        verify(tables, never()).save(any(DiningTable.class));
    }

    @Test
    void replayingCloseOnOldTerminalOrdersDoesNotFreeReusedTable() {
        table.setStatus(TableStatus.OCCUPIED); // A newer active order owns this table.
        for (OrderStatus status : List.of(OrderStatus.SETTLED, OrderStatus.CANCELLED)) {
            RestaurantOrder oldOrder = ownedOrder(status, true);
            oldOrder.setTable(table);
            Long id = (long) status.ordinal() + 1;
            when(orders.findById(id)).thenReturn(Optional.of(oldOrder));

            assertEquals(HttpStatus.BAD_REQUEST,
                    controller.cancel(customerToken(101L, "CUSTOMER"), id).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                    controller.settle(customerToken(101L, "CUSTOMER"), id).getStatusCode());
            assertEquals(TableStatus.OCCUPIED, table.getStatus());
        }
        verify(orders, never()).save(any(RestaurantOrder.class));
        verify(tables, never()).save(any(DiningTable.class));
    }

    @Test
    void draftAndSubmittedOrdersStillSupportAllowedTransitions() {
        RestaurantOrder draft = ownedOrder(OrderStatus.DRAFT, true);
        draft.setTable(table);
        when(orders.findById(31L)).thenReturn(Optional.of(draft));
        assertEquals(HttpStatus.OK,
                controller.submit(customerToken(101L, "CUSTOMER"), 31L).getStatusCode());
        assertEquals(OrderStatus.SUBMITTED, draft.getStatus());

        RestaurantOrder submitted = ownedOrder(OrderStatus.SUBMITTED, true);
        submitted.setTable(table);
        when(orders.findById(32L)).thenReturn(Optional.of(submitted));
        assertEquals(HttpStatus.OK,
                controller.settle(customerToken(101L, "CUSTOMER"), 32L).getStatusCode());
        assertEquals(OrderStatus.SETTLED, submitted.getStatus());

        RestaurantOrder draftToCancel = ownedOrder(OrderStatus.DRAFT, true);
        draftToCancel.setTable(table);
        when(orders.findById(33L)).thenReturn(Optional.of(draftToCancel));
        assertEquals(HttpStatus.OK,
                controller.cancel(customerToken(101L, "CUSTOMER"), 33L).getStatusCode());

        RestaurantOrder submittedToCancel = ownedOrder(OrderStatus.SUBMITTED, true);
        submittedToCancel.setTable(table);
        when(orders.findById(34L)).thenReturn(Optional.of(submittedToCancel));
        assertEquals(HttpStatus.OK,
                controller.cancel(customerToken(101L, "CUSTOMER"), 34L).getStatusCode());
    }

    private static RestaurantOrder ownedOrder(OrderStatus status, boolean withItem) {
        RestaurantOrder order = new RestaurantOrder();
        order.setCustomerId(101L);
        order.setStatus(status);
        if (withItem) order.getItems().add(new OrderItem());
        return order;
    }

    private String customerToken(long id, String role) {
        return "Bearer " + tokens.issue(id, "user" + id, role);
    }

    private static OrderController.CreateOrderRequest request(OrderMode mode, Long creatorId, Long customerId,
                                                              String tableCode) {
        return new OrderController.CreateOrderRequest(9L, mode, creatorId, customerId, tableCode,
                BigDecimal.ONE);
    }
}
