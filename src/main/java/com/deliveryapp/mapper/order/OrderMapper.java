package com.deliveryapp.mapper.order;

import com.deliveryapp.dto.catalog.StoreResponse;
import com.deliveryapp.dto.order.DeliveryFeeResponse;
import com.deliveryapp.dto.order.OrderItemResponse;
import com.deliveryapp.dto.order.OrderQuote;
import com.deliveryapp.dto.order.OrderQuoteResponse;
import com.deliveryapp.dto.order.OrderResponse;
import com.deliveryapp.dto.order.StorePayoutResponse;
import com.deliveryapp.dto.order.OrderOfferResponse;
import com.deliveryapp.dto.order.VendorOrderResponse;
import com.deliveryapp.entity.Coupon;
import com.deliveryapp.entity.Order;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Store;
import com.deliveryapp.mapper.catalog.CatalogMapper;
import com.deliveryapp.mapper.user.UserMapper;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.service.OrderProfitService;
import com.deliveryapp.service.StorePayoutService;
import com.deliveryapp.util.DistanceUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final CatalogMapper catalogMapper;
    private final UserMapper userMapper;
    private final DistanceUtil distanceUtil;
    private final StorePayoutService storePayoutService;
    private final OrderProfitService orderProfitService;
    private final CouponRepository couponRepository;
    private final PromotionalOfferRepository offerRepository;

    /** Everything {@link #toOrderResponse} has, plus the platform's profit on the order — admins only. */
    public OrderResponse toAdminOrderResponse(Order order) {
        OrderResponse response = toOrderResponse(order);
        response.setProfit(orderProfitService.breakdown(order, response.getStorePayouts()));
        return response;
    }

    public OrderResponse toOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());

        // --- MAP LIST OF STORES ---
        if (order.getStores() != null && !order.getStores().isEmpty()) {
            List<StoreResponse> storeDtos = order.getStores().stream()
                    .map(catalogMapper::toStoreResponse)
                    .collect(Collectors.toList());
            response.setStores(storeDtos);

            // Set first store name for flat display if needed
            response.setStoreName(
                    order.getStores().get(0).getName() + (order.getStores().size() > 1 ? " & others" : ""));
        }
        // --- Driver Info ---
        if (order.getDriver() != null) {
            response.setDriverId(order.getDriver().getUserId());
            response.setDriverName(order.getDriver().getName());
            response.setDriverPhone(order.getDriver().getPhoneNumber());
        }
        response.setDriverOrderStatus(order.getDriverOrderStatus());

        // --- Customer Info (Reusing UserMapper) ---
        if (order.getUser() != null) {
            response.setCustomerDetails(userMapper.toOrderCustomerResponse(order.getUser()));
        }

        // --- Location & Status ---
        response.setDeliveryAddress(order.getDeliveryAddress());
        response.setDeliveryLatitude(order.getDeliveryLatitude());
        response.setDeliveryLongitude(order.getDeliveryLongitude());

        if (order.getStores() != null && !order.getStores().isEmpty()
                && order.getDeliveryLatitude() != null && order.getDeliveryLongitude() != null) {
            List<Store> validStores = new ArrayList<>();
            for (Store store : order.getStores()) {
                if (store != null && store.getLatitude() != null && store.getLongitude() != null) {
                    validStores.add(store);
                }
            }

            validStores.sort((left, right) -> {
                double leftDistance = distanceUtil.calculateDistance(
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude(),
                        left.getLatitude(),
                        left.getLongitude());
                double rightDistance = distanceUtil.calculateDistance(
                        order.getDeliveryLatitude(),
                        order.getDeliveryLongitude(),
                        right.getLatitude(),
                        right.getLongitude());
                return Double.compare(rightDistance, leftDistance);
            });

            response.setMaxMinimumDeliveryFee(validStores.stream()
                    .mapToDouble(store -> store.getMinimumDeliveryFee() != null ? store.getMinimumDeliveryFee() : 0.0)
                    .max()
                    .orElse(0.0));

            response.setTotalDistanceKm(distanceUtil.calculateOptimizedDistance(
                    validStores,
                    order.getDeliveryLatitude(),
                    order.getDeliveryLongitude()));

            List<DeliveryFeeResponse.RouteSegmentResponse> segments = new ArrayList<>();
            for (int i = 0; i < validStores.size() - 1; i++) {
                Store fromStore = validStores.get(i);
                Store toStore = validStores.get(i + 1);
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        fromStore.getName(),
                        toStore.getName(),
                        distanceUtil.calculateDistance(
                                fromStore.getLatitude(),
                                fromStore.getLongitude(),
                                toStore.getLatitude(),
                                toStore.getLongitude()),
                        "STORE_TO_STORE"));
            }

            if (!validStores.isEmpty()) {
                Store lastStore = validStores.get(validStores.size() - 1);
                String userName = order.getUser() != null && order.getUser().getName() != null
                        ? order.getUser().getName()
                        : "User";
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        lastStore.getName(),
                        userName,
                        distanceUtil.calculateDistance(
                                lastStore.getLatitude(),
                                lastStore.getLongitude(),
                                order.getDeliveryLatitude(),
                                order.getDeliveryLongitude()),
                        "STORE_TO_USER"));
            }

            response.setRouteSegments(segments);
        }

        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setDeliveredAt(order.getDeliveredAt());
        // 🟢 FIX: Map Instructions and Notes
        response.setSelectedInstruction(order.getSelectedInstruction());
        response.setOrderNote(order.getOrderNote());

        // --- Financials ---
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryFee(order.getDeliveryFee());
        response.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0);
        response.setCouponId(order.getCouponId());
        response.setOfferDiscountAmount(order.getOfferDiscountAmount() != null ? order.getOfferDiscountAmount() : 0.0);
        response.setTotalAmount(order.getTotalAmount());

        // --- What each store is owed (store-paid discounts + commission applied server-side) ---
        if (order.getStores() != null) {
            response.setStorePayouts(order.getStores().stream()
                    .map(store -> storePayoutService.calculate(order, store))
                    .collect(Collectors.toList()));
        } else {
            response.setStorePayouts(Collections.emptyList());
        }

        // --- Coupon and offer details (who pays is hidden again in the customer view) ---
        Coupon coupon = couponOf(order);
        if (coupon != null) {
            response.setCouponCode(coupon.getCode());
            response.setCouponTitle(coupon.getTitle());
            response.setCouponDiscountType(discountTypeOf(coupon));
            response.setCouponDiscountValue(discountValueOf(coupon));
        }
        Long payingStoreId = order.getCouponFundedByStoreId();
        response.setCouponFundedByStore(payingStoreId != null);
        response.setCouponFundedByStoreId(payingStoreId);
        response.setCouponStoreDiscountAmount(0.0);
        // The paying store's share comes from its payout, so both always agree
        response.getStorePayouts().stream()
                .filter(payout -> payout.getStoreId() != null && payout.getStoreId().equals(payingStoreId))
                .findFirst()
                .ifPresent(payout -> {
                    response.setCouponFundedByStoreName(payout.getStoreName());
                    response.setCouponStoreDiscountAmount(payout.getStoreCouponDiscountAmount());
                });
        response.setOffers(offersOf(order.getOrderItems() != null ? order.getOrderItems() : List.of()));

        // --- Order Items ---
        if (order.getOrderItems() != null) {
            response.setItems(order.getOrderItems().stream()
                    .map(this::toOrderItemResponse)
                    .collect(Collectors.toList()));
        } else {
            response.setItems(Collections.emptyList());
        }

        return response;
    }

    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        OrderItemResponse r = new OrderItemResponse();

        // Map Product details
        if (item.getProduct() != null) {
            r.setProductId(item.getProduct().getProductId());
        }
        if (item.getVariant() != null) {
            r.setVariantId(item.getVariant().getVariantId());
        }
        r.setProductName(item.getProductName());
        String variantDetails = item.getVariantDetails();
        if ((variantDetails == null || variantDetails.isBlank()) && item.getVariant() != null) {
            variantDetails = item.getVariant().getVariantValue();
        }
        r.setVariantDetails(variantDetails);
        r.setQuantity(item.getQuantity());
        r.setUnitPrice(item.getUnitPrice());
        r.setTotalPrice(item.getTotalPrice());
        r.setStoreUnitPrice(item.getEffectiveStoreUnitPrice());
        r.setStoreTotalPrice(item.getEffectiveStoreTotalPrice());
        r.setNotes(item.getNotes());
        r.setSelectedColor(item.getSelectedColor());
        r.setRewardOfferId(item.getRewardOfferId());
        r.setAppliedOfferId(item.getAppliedOfferId());
        r.setOfferDiscountAmount(item.getOfferDiscountAmount() != null ? item.getOfferDiscountAmount() : 0.0);
        r.setOfferFundedByStore(Boolean.TRUE.equals(item.getOfferFundedByStore()));
        r.setStoreOfferDiscountAmount(item.getEffectiveStoreOfferDiscount());

        // 👉 Map Store details for grouping and payout math
        if (item.getProduct() != null && item.getProduct().getStore() != null) {
            r.setStoreId(item.getProduct().getStore().getStoreId());
            r.setStoreName(item.getProduct().getStore().getName());

            // Safely get commission (default to 0.0 if null)
            Double commission = item.getProduct().getStore().getCommissionPercentage();
            r.setStoreCommissionPercentage(commission != null ? commission : 0.0);
        }

        return r;
    }

    /**
     * What customers get: no store prices, payouts or commission — the price markup is platform profit
     * and must stay invisible to them. Admin, employee and driver use {@link #toOrderResponse}.
     */
    public OrderResponse toCustomerOrderResponse(Order order) {
        OrderResponse response = toOrderResponse(order);
        response.setStorePayouts(null);
        response.getItems().forEach(this::hideStoreSide);
        // The customer sees their coupon and the offers, not who pays for them
        response.setCouponFundedByStore(null);
        response.setCouponFundedByStoreId(null);
        response.setCouponFundedByStoreName(null);
        response.setCouponStoreDiscountAmount(null);
        response.getOffers().forEach(offer -> offer.setFundedByStore(null));
        return response;
    }

    private OrderItemResponse hideStoreSide(OrderItemResponse item) {
        item.setStoreUnitPrice(null);
        item.setStoreTotalPrice(null);
        item.setStoreOfferDiscountAmount(null);
        item.setOfferFundedByStore(null);
        item.setStoreCommissionPercentage(null);
        return item;
    }

    /** Checkout preview — the same numbers placeOrder will save (customer view). */
    public OrderQuoteResponse toQuoteResponse(OrderQuote quote) {
        OrderQuoteResponse response = new OrderQuoteResponse();
        response.setSubtotal(quote.getSubtotal());
        response.setOfferDiscountAmount(quote.getOfferDiscountAmount());
        response.setDeliveryFee(quote.getDeliveryFee());
        response.setFreeDeliveryStoreIds(new ArrayList<>(quote.getFreeDeliveryStoreIds()));
        if (quote.isCouponApplied()) {
            response.setCouponId(quote.getCoupon().getCouponId());
            response.setCouponDiscountType(quote.getCoupon().getDiscountType().name());
            response.setDiscountAmount(quote.getCouponDiscountAmount());
        } else {
            response.setDiscountAmount(0.0);
        }
        response.setTotalAmount(quote.getTotalAmount());
        response.setItems(quote.getItems().stream()
                .map(this::toOrderItemResponse)
                .map(this::hideStoreSide)
                .collect(Collectors.toList()));
        return response;
    }

    // 🟢 NEW: Mapper specifically for the Vendor App
    public VendorOrderResponse toVendorOrderResponse(Order order, Long vendorStoreId) {
        VendorOrderResponse response = new VendorOrderResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNumber(order.getOrderNumber());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setOrderNote(order.getOrderNote());

        if (order.getUser() != null) {
            response.setCustomerName(order.getUser().getName());
        }

        // 1. Filter Items: ONLY keep items belonging to this vendor's store
        List<OrderItem> vendorItems = order.getOrderItems().stream()
                .filter(item -> item.getProduct() != null &&
                        item.getProduct().getStore() != null &&
                        item.getProduct().getStore().getStoreId().equals(vendorStoreId))
                .collect(Collectors.toList());

        // 2. Map the filtered items to DTOs
        response.setItems(vendorItems.stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList()));

        // 3. Vendor Financials — shared with Telegram so both always show the same payout
        Store vendorStore = order.getStores() == null ? null : order.getStores().stream()
                .filter(store -> store.getStoreId().equals(vendorStoreId))
                .findFirst()
                .orElse(null);
        if (vendorStore == null && !vendorItems.isEmpty()) {
            vendorStore = vendorItems.get(0).getProduct().getStore();
        }

        if (vendorStore != null) {
            StorePayoutResponse payout = storePayoutService.calculate(order, vendorStore);
            response.setStoreSubtotal(payout.getItemsSubtotal());
            response.setStoreDiscountAmount(payout.getStoreDiscountAmount());
            response.setCommissionAmount(payout.getCommissionAmount());
            response.setStorePayout(payout.getStorePayout());
            response.setCommissionPercentage(payout.getCommissionPercentage());
            response.setStoreOfferDiscountAmount(payout.getStoreOfferDiscountAmount());
            response.setStoreCouponDiscountAmount(payout.getStoreCouponDiscountAmount());
        } else {
            response.setStoreSubtotal(0.0);
            response.setStoreDiscountAmount(0.0);
            response.setCommissionAmount(0.0);
            response.setStorePayout(0.0);
            response.setCommissionPercentage(0.0);
            response.setStoreOfferDiscountAmount(0.0);
            response.setStoreCouponDiscountAmount(0.0);
        }

        // 4. Coupon on the order — the code may be gone if its flash sale was deleted since
        response.setCouponFundedByStore(vendorStoreId.equals(order.getCouponFundedByStoreId()));
        response.setCouponDiscountAmount(order.getCouponId() != null && order.getDiscountAmount() != null
                ? order.getDiscountAmount() : 0.0);
        Coupon coupon = couponOf(order);
        if (coupon != null) {
            response.setCouponCode(coupon.getCode());
            response.setCouponTitle(coupon.getTitle());
            response.setCouponDiscountType(discountTypeOf(coupon));
            response.setCouponDiscountValue(discountValueOf(coupon));
        }

        // 5. Names of the offers on this store's lines (items point to them by appliedOfferId)
        response.setOffers(offersOf(vendorItems));

        return response;
    }

    // ── Coupon & offer helpers (vendor and staff views) ──────────────────────

    /** The order's coupon as it is now, or null when none was used or it was deleted since (e.g. its flash sale). */
    private Coupon couponOf(Order order) {
        return order.getCouponId() == null ? null : couponRepository.findById(order.getCouponId()).orElse(null);
    }

    private static String discountTypeOf(Coupon coupon) {
        return coupon.getDiscountType() != null ? coupon.getDiscountType().name() : null;
    }

    /** 10 = 10% for PERCENTAGE, an SYP amount for FIXED_AMOUNT; a free-delivery coupon has no value (stored as 0). */
    private static Double discountValueOf(Coupon coupon) {
        if (coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY || coupon.getDiscountValue() == null) {
            return null;
        }
        return coupon.getDiscountValue().doubleValue();
    }

    /** The offers on these lines, in line order, with their store and who pays. */
    private List<OrderOfferResponse> offersOf(List<OrderItem> items) {
        Map<Long, OrderOfferResponse> byId = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getAppliedOfferId() == null) {
                continue;
            }
            OrderOfferResponse offer = byId.computeIfAbsent(item.getAppliedOfferId(), id -> {
                Store store = item.getProduct() != null ? item.getProduct().getStore() : null;
                return new OrderOfferResponse(id, null, false,
                        store != null ? store.getStoreId() : null, store != null ? store.getName() : null);
            });
            if (Boolean.TRUE.equals(item.getOfferFundedByStore())) {
                offer.setFundedByStore(true);
            }
        }
        if (byId.isEmpty()) {
            return new ArrayList<>();
        }
        offerRepository.findAllById(byId.keySet())
                .forEach(offer -> byId.get(offer.getOfferId()).setTitle(offer.getTitle()));
        return new ArrayList<>(byId.values());
    }
}