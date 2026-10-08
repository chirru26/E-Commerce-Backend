package com.chirru.ecommerce.modules.cart.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartDomainTest {

    @Test
    void cartItemCanChangeQuantityBeforeCheckoutReservation() {
        CartItem item = new CartItem(UUID.randomUUID(), UUID.randomUUID(), 2);

        item.changeQuantity(5);

        assertEquals(5, item.getQuantity());
    }

    @Test
    void reservedCartItemCannotBeModified() {
        CartItem item = new CartItem(UUID.randomUUID(), UUID.randomUUID(), 2);
        item.setCheckoutReservation("checkout-1", "reservation-1", UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> item.changeQuantity(3));
    }

    @Test
    void cartTransitionsThroughCheckoutLifecycle() {
        Cart cart = new Cart(UUID.randomUUID());

        cart.reserveForCheckout();
        assertEquals(CartStatus.CHECKOUT_RESERVED, cart.getStatus());

        cart.releaseCheckout();
        assertEquals(CartStatus.ACTIVE, cart.getStatus());

        cart.reserveForCheckout();
        cart.completeCheckout();
        assertEquals(CartStatus.CHECKED_OUT, cart.getStatus());
    }
}
