package com.chirru.ecommerce.modules.cart.api;

import com.chirru.ecommerce.modules.cart.api.CartDtos.AddCartItemRequest;
import com.chirru.ecommerce.modules.cart.api.CartDtos.CartView;
import com.chirru.ecommerce.modules.cart.api.CartDtos.UpdateCartItemRequest;
import com.chirru.ecommerce.modules.cart.application.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartView getCart(Authentication authentication) {
        return cartService.getCart(currentUserId(authentication));
    }

    @PostMapping("/items")
    public CartView addItem(Authentication authentication,
                            @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(currentUserId(authentication), request);
    }

    @PutMapping("/items/{productId}")
    public CartView updateItem(Authentication authentication,
                               @PathVariable UUID productId,
                               @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(currentUserId(authentication), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(Authentication authentication, @PathVariable UUID productId) {
        cartService.removeItem(currentUserId(authentication), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        cartService.clear(currentUserId(authentication));
    }

    private static UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
