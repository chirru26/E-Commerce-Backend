package com.chirru.ecommerce.modules.cart.application;

import com.chirru.ecommerce.modules.cart.api.CartDtos.AddCartItemRequest;
import com.chirru.ecommerce.modules.cart.api.CartDtos.CartItemView;
import com.chirru.ecommerce.modules.cart.api.CartDtos.CartView;
import com.chirru.ecommerce.modules.cart.api.CartDtos.UpdateCartItemRequest;
import com.chirru.ecommerce.modules.cart.domain.Cart;
import com.chirru.ecommerce.modules.cart.domain.CartItem;
import com.chirru.ecommerce.modules.cart.domain.CartStatus;
import com.chirru.ecommerce.modules.cart.infrastructure.CartItemRepository;
import com.chirru.ecommerce.modules.cart.infrastructure.CartRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CartService implements CartCheckoutPort {
    private static final int MAX_DISTINCT_ITEMS = 100;
    private static final int MAX_ITEM_QUANTITY = 1000;

    private final CartRepository carts;
    private final CartItemRepository items;
    private final CatalogProductPort catalogProducts;
    private final InventoryReservationPort inventoryReservations;

    public CartService(CartRepository carts,
                       CartItemRepository items,
                       CatalogProductPort catalogProducts,
                       InventoryReservationPort inventoryReservations) {
        this.carts = carts;
        this.items = items;
        this.catalogProducts = catalogProducts;
        this.inventoryReservations = inventoryReservations;
    }

    public CartView getCart(UUID userId) {
        Cart cart = carts.findByUserIdAndStatus(userId, CartStatus.CHECKOUT_RESERVED)
                .orElseGet(() -> ensureActiveCart(userId));
        return toView(cart);
    }

    public CartView addItem(UUID userId, AddCartItemRequest request) {
        validateQuantity(request.quantity());
        CatalogProductPort.ProductSnapshot product = requirePurchasableProduct(request.productId());

        Cart cart = lockMutableCart(userId);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), request.productId()).orElse(null);
        if (item != null) {
            ensureCartCurrency(cart, product);
            long newQuantity = safeAdd(item.getQuantity(), request.quantity());
            validateQuantity(newQuantity);
            item.changeQuantity(newQuantity);
        } else {
            if (items.findAllByCartIdOrderByCreatedAtAsc(cart.getId()).size() >= MAX_DISTINCT_ITEMS) {
                throw conflict("Cart cannot contain more than " + MAX_DISTINCT_ITEMS + " products");
            }
            ensureCartCurrency(cart, product);
            items.save(new CartItem(cart.getId(), request.productId(), request.quantity()));
        }
        return toView(cart);
    }

    public CartView updateItem(UUID userId, UUID productId, UpdateCartItemRequest request) {
        validateQuantity(request.quantity());
        CatalogProductPort.ProductSnapshot product = requirePurchasableProduct(productId);

        Cart cart = lockMutableCart(userId);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> notFound("Cart item not found"));
        ensureCartCurrency(cart, product);
        item.changeQuantity(request.quantity());
        return toView(cart);
    }

    public void removeItem(UUID userId, UUID productId) {
        Cart cart = lockMutableCart(userId);
        CartItem item = items.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> notFound("Cart item not found"));
        items.delete(item);
    }

    public void clear(UUID userId) {
        Cart cart = lockMutableCart(userId);
        for (CartItem item : items.findAllByCartIdOrderByCreatedAtAsc(cart.getId())) {
            if (item.getReservationId() != null) {
                inventoryReservations.release(item.getReservationId());
                item.clearCheckoutReservation();
            }
        }
        items.deleteAllByCartId(cart.getId());
    }

    @Override
    public CheckoutSnapshot prepareForCheckout(UUID userId, String checkoutReference) {
        String reference = normalizeCheckoutReference(checkoutReference);

        Cart reservedCart = carts.findByUserIdAndStatusForUpdate(userId, CartStatus.CHECKOUT_RESERVED)
                .orElse(null);
        if (reservedCart != null) {
            List<CartItem> existing = items.findAllByCartIdOrderByCreatedAtAsc(reservedCart.getId());
            for (CartItem item : existing) {
                ensureCheckoutReference(item, reference);
            }
            return toCheckoutSnapshot(reservedCart, existing, reference);
        }

        Cart cart = lockMutableCart(userId);
        List<CartItem> cartItems = items.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) {
            throw conflict("Cart is empty");
        }

        for (CartItem item : cartItems) {
            CatalogProductPort.ProductSnapshot product = requirePurchasableProduct(item.getProductId());
            ensureCartCurrency(cart, product);
            String reservationReference = "checkout:" + item.getId() + ":" + UUID.randomUUID();
            UUID reservationId = inventoryReservations.reserve(
                    item.getProductId(), item.getQuantity(), reservationReference);
            item.setCheckoutReservation(reference, reservationReference, reservationId);
        }

        cart.reserveForCheckout();
        return toCheckoutSnapshot(cart, cartItems, reference);
    }

    @Override
    public void releaseCheckout(UUID userId, String checkoutReference) {
        String reference = normalizeCheckoutReference(checkoutReference);
        Cart cart = carts.findByUserIdAndStatusForUpdate(userId, CartStatus.CHECKOUT_RESERVED)
                .orElseThrow(() -> notFound("Checkout-reserved cart not found"));

        List<CartItem> cartItems = items.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        for (CartItem item : cartItems) {
            ensureCheckoutReference(item, reference);
            if (item.getReservationId() != null) {
                inventoryReservations.release(item.getReservationId());
                item.clearCheckoutReservation();
            }
        }
        cart.releaseCheckout();
    }

    @Override
    public void completeCheckout(UUID userId, String checkoutReference) {
        String reference = normalizeCheckoutReference(checkoutReference);
        Cart cart = carts.findByUserIdAndStatusForUpdate(userId, CartStatus.CHECKOUT_RESERVED)
                .orElseThrow(() -> notFound("Checkout-reserved cart not found"));

        List<CartItem> cartItems = items.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        for (CartItem item : cartItems) {
            ensureCheckoutReference(item, reference);
            if (item.getReservationId() == null) {
                throw conflict("Checkout reservation is missing");
            }
            inventoryReservations.consume(item.getReservationId());
            item.clearCheckoutReservation();
        }
        cart.completeCheckout();
    }

    private Cart lockMutableCart(UUID userId) {
        if (userId == null) throw badRequest("User id is required");

        UUID candidateId = UUID.randomUUID();
        carts.createActiveIfAbsent(candidateId, userId);

        Cart active = carts.findByUserIdAndStatusForUpdate(userId, CartStatus.ACTIVE).orElse(null);
        if (active != null) {
            return active;
        }

        if (carts.findByUserIdAndStatusForUpdate(userId, CartStatus.CHECKOUT_RESERVED).isPresent()) {
            throw conflict("Cart is reserved for checkout");
        }
        throw notFound("Active cart not found");
    }

    private Cart ensureActiveCart(UUID userId) {
        if (userId == null) throw badRequest("User id is required");
        UUID candidateId = UUID.randomUUID();
        carts.createActiveIfAbsent(candidateId, userId);
        return carts.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> carts.findByUserIdAndStatus(userId, CartStatus.CHECKOUT_RESERVED)
                        .orElseThrow(() -> notFound("Cart not found")));
    }

    private CatalogProductPort.ProductSnapshot requirePurchasableProduct(UUID productId) {
        CatalogProductPort.ProductSnapshot product = catalogProducts.findById(productId)
                .orElseThrow(() -> notFound("Product not found"));
        if (!product.active() || !product.categoryActive()) {
            throw conflict("Product is not available for purchase");
        }
        return product;
    }

    private void ensureCartCurrency(Cart cart, CatalogProductPort.ProductSnapshot product) {
        List<CartItem> currentItems = items.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        if (currentItems.isEmpty()) return;

        for (CartItem current : currentItems) {
            CatalogProductPort.ProductSnapshot existing = catalogProducts.findById(current.getProductId())
                    .orElse(null);
            if (existing != null && !existing.currency().equalsIgnoreCase(product.currency())) {
                throw conflict("Cart cannot contain products in different currencies");
            }
        }
    }

    private CartView toView(Cart cart) {
        List<CartItem> cartItems = items.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        List<CartItemView> views = cartItems.stream().map(this::toItemView).toList();
        long itemCount = cartItems.stream().mapToLong(CartItem::getQuantity).sum();
        BigDecimal subtotal = views.stream()
                .map(CartItemView::lineTotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = views.isEmpty() ? null : views.getFirst().currency();

        return new CartView(cart.getId(), cart.getStatus(), views, itemCount, subtotal,
                currency, cart.getCreatedAt(), cart.getUpdatedAt());
    }

    private CartItemView toItemView(CartItem item) {
        CatalogProductPort.ProductSnapshot product = catalogProducts.findById(item.getProductId())
                .orElse(null);
        BigDecimal unitPrice = product == null ? null : product.price();
        BigDecimal lineTotal = unitPrice == null
                ? null
                : unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        return new CartItemView(item.getProductId(),
                product == null ? null : product.sku(),
                product == null ? null : product.name(),
                unitPrice,
                product == null ? null : product.currency(),
                item.getQuantity(),
                lineTotal,
                product != null && product.active() && product.categoryActive());
    }

    private CheckoutSnapshot toCheckoutSnapshot(Cart cart, List<CartItem> cartItems,
                                                String checkoutReference) {
        List<CheckoutSnapshot.Item> snapshot = cartItems.stream().map(item -> {
            CatalogProductPort.ProductSnapshot product = catalogProducts.findById(item.getProductId())
                    .orElseThrow(() -> notFound("Product not found"));
            return new CheckoutSnapshot.Item(item.getProductId(), item.getQuantity(),
                    item.getReservationId(), product.sku(), product.name(),
                    product.price(), product.currency());
        }).toList();
        return new CheckoutSnapshot(cart.getId(), cart.getUserId(), checkoutReference, snapshot);
    }

    private static void ensureCheckoutReference(CartItem item, String reference) {
        if (!reference.equals(item.getCheckoutReference())) {
            throw conflict("Checkout reference does not match cart reservation");
        }
    }

    private static long safeAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException exception) {
            throw badRequest("Cart quantity is too large");
        }
    }

    private static void validateQuantity(long quantity) {
        if (quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw badRequest("Quantity must be between 1 and " + MAX_ITEM_QUANTITY);
        }
    }

    private static String normalizeCheckoutReference(String value) {
        if (value == null || value.isBlank()) throw badRequest("Checkout reference is required");
        String reference = value.trim();
        if (reference.length() > 120) throw badRequest("Checkout reference must be 120 characters or fewer");
        return reference;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
