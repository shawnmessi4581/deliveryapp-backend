package com.deliveryapp.service;

import com.deliveryapp.dto.order.CouponCheckRequest;
import com.deliveryapp.dto.order.CouponCheckResponse;
import com.deliveryapp.dto.order.DeliveryFeeRequest;
import com.deliveryapp.dto.order.DeliveryFeeResponse;
import com.deliveryapp.dto.order.OrderItemRequest;
import com.deliveryapp.dto.order.OrderQuote;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.ColorRepository;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.ProductVariantRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserAddressRepository;
import com.deliveryapp.util.DistanceUtil;
import com.deliveryapp.util.MathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 💰 Single source of truth for order pricing: placeOrder and every preview endpoint
 * (/quote, /calc-fee, /verify-coupon, store listings) go through the same rules.
 *
 * <ol>
 *   <li>Line prices (product + variant, SYP).</li>
 *   <li>Promotional offers (pooled per product, cheapest units rewarded).</li>
 *   <li>Delivery: a store is free when freeDelivery is on, or its own subtotal after offers reaches its
 *       enabled threshold. The customer pays the route fee of the remaining stores only.</li>
 *   <li>Coupon / flash sale on the items it covers, after offers. FREE_DELIVERY waives the delivery
 *       of the stores it covers.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class OrderCalculationService {

    private final StoreRepository storeRepository;
    private final UserAddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ColorRepository colorRepository;
    private final DistanceUtil distanceUtil;
    private final MathUtil mathUtil;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final PromotionalOfferService promotionalOfferService;

    // =================================================================================
    // CART PRICING
    // =================================================================================

    /**
     * Builds priced lines (not yet attached to an order) from the cart and applies promotional offers.
     */
    public List<OrderItem> priceItems(List<OrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty())
            throw new InvalidDataException("لم يتم تحديد أي عناصر.");

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemReq : itemRequests) {
            if (itemReq.getProductId() == null)
                throw new InvalidDataException("معرّف المنتج مطلوب");
            // A zero/negative quantity would lower the subtotal
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1)
                throw new InvalidDataException("الكمية يجب أن تكون 1 على الأقل");

            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود: " + itemReq.getProductId()));
            if (product.getStore() == null)
                throw new InvalidDataException("المنتج غير مرتبط بمتجر: " + product.getName());

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(itemReq.getQuantity());
            item.setNotes(itemReq.getNotes());

            if (itemReq.getColorId() != null) {
                Color color = colorRepository.findById(itemReq.getColorId())
                        .orElseThrow(() -> new ResourceNotFoundException("اللون غير موجود"));

                boolean isValidColor = product.getColors() != null && product.getColors().stream()
                        .anyMatch(c -> c.getColorId().equals(color.getColorId()));

                if (!isValidColor)
                    throw new InvalidDataException("اللون غير متوفر لهذا المنتج");
                item.setSelectedColor(color);
            }

            // 💹 Two prices per line: the store's own (what it's paid on) and the customer's (markup included,
            // each part marked up separately exactly like the catalog shows them)
            double storePrice = pricingService.getFinalPriceInSYP(product);
            double customerPrice = pricingService.getCustomerFinalPrice(product);
            if (itemReq.getVariantId() != null && itemReq.getVariantId() != 0) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("النوع غير موجود: " + itemReq.getVariantId()));

                if (!variant.getProduct().getProductId().equals(product.getProductId())) {
                    throw new InvalidDataException("هذا النوع لا ينتمي لهذا المنتج");
                }

                item.setVariant(variant);
                item.setVariantDetails(variant.getVariantValue());
                storePrice += pricingService.getVariantFinalPriceInSYP(variant);
                customerPrice += pricingService.getCustomerVariantPrice(variant);
            }

            item.setUnitPrice(customerPrice);
            item.setTotalPrice(customerPrice * itemReq.getQuantity());
            item.setStoreUnitPrice(storePrice);
            item.setStoreTotalPrice(storePrice * itemReq.getQuantity());
            items.add(item);
        }

        promotionalOfferService.applyOffers(items);
        return items;
    }

    /**
     * Prices a cart end to end: subtotal, offers, delivery, coupon and total.
     *
     * @param items    lines from {@link #priceItems}
     * @param userLat  delivery location; when null/unknown the delivery fee is left at 0
     */
    public OrderQuote quote(List<OrderItem> items, Long userId, String couponCode, Double userLat, Double userLng) {
        OrderQuote quote = new OrderQuote();
        quote.setItems(items);

        Map<Long, Double> netSubtotalByStore = new HashMap<>();
        List<Store> stores = collectStores(items, netSubtotalByStore);
        quote.setStores(stores);

        double subtotal = items.stream().mapToDouble(OrderItem::getTotalPrice).sum();
        double offerDiscount = items.stream()
                .mapToDouble(item -> item.getOfferDiscountAmount() != null ? item.getOfferDiscountAmount() : 0.0)
                .sum();
        quote.setSubtotal(subtotal);
        quote.setOfferDiscountAmount(offerDiscount);

        // Delivery — the customer pays only for stores that don't qualify for free delivery
        Set<Long> freeStoreIds = findFreeDeliveryStoreIds(stores, netSubtotalByStore);
        quote.setFreeDeliveryStoreIds(freeStoreIds);
        boolean hasLocation = userLat != null && userLng != null;
        double deliveryFee = hasLocation ? calculatePayingStoresFee(stores, freeStoreIds, userLat, userLng) : 0.0;

        // Coupon / flash sale
        double itemCouponDiscount = 0.0;
        if (couponCode != null && !couponCode.isBlank()) {
            Coupon coupon = couponService.validateCouponForOrder(couponCode, userId, items);
            List<OrderItem> eligibleItems = couponService.findEligibleItems(coupon, items);
            double couponDiscount = 0.0;

            if (coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
                // Waives delivery for the stores the coupon covers (every store for an ALL-scope coupon)
                if (hasLocation) {
                    Set<Long> coveredStoreIds = new HashSet<>(freeStoreIds);
                    eligibleItems.forEach(item -> coveredStoreIds.add(item.getProduct().getStore().getStoreId()));
                    double feeAfterCoupon = Math.min(
                            calculatePayingStoresFee(stores, coveredStoreIds, userLat, userLng), deliveryFee);
                    couponDiscount = deliveryFee - feeAfterCoupon;
                    deliveryFee = feeAfterCoupon;
                }
            } else {
                double eligibleSubtotal = eligibleItems.stream().mapToDouble(OrderItem::getNetTotalPrice).sum();
                couponDiscount = couponService.calculateDiscount(coupon, eligibleSubtotal);
                itemCouponDiscount = couponDiscount;
            }

            quote.setCoupon(coupon);
            quote.setCouponDiscountAmount(couponDiscount);
            if (couponDiscount > 0) {
                Long fundingStoreId = couponService.findFundingStoreId(coupon);
                quote.setCouponFundedByStoreId(fundingStoreId);
                if (fundingStoreId != null) {
                    quote.setCouponStoreDiscountAmount(storeShareOfCoupon(coupon, couponDiscount, eligibleItems));
                }
            }
        }

        quote.setDeliveryFee(deliveryFee);
        quote.setTotalAmount(Math.max(subtotal - offerDiscount - itemCouponDiscount + deliveryFee, 0.0));
        return quote;
    }

    /**
     * What the funding store pays for its coupon, at its own prices: the customer saving scaled by
     * store price / customer price of the covered items (the markup part is absorbed by the platform).
     * A waived delivery fee has no markup, so the store pays all of it.
     */
    private double storeShareOfCoupon(Coupon coupon, double couponDiscount, List<OrderItem> eligibleItems) {
        if (coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY) {
            return couponDiscount;
        }
        double customerBase = eligibleItems.stream().mapToDouble(OrderItem::getNetTotalPrice).sum();
        double storeBase = eligibleItems.stream().mapToDouble(OrderItem::getStoreNetTotalPrice).sum();
        if (customerBase <= 0) {
            return couponDiscount;
        }
        return mathUtil.roundMoney(couponDiscount * storeBase / customerBase);
    }

    // =================================================================================
    // PREVIEW ENDPOINTS
    // =================================================================================

    public DeliveryFeeResponse calculateDeliveryFee(Long storeId, Long addressId) {
        DeliveryFeeRequest request = new DeliveryFeeRequest();
        request.setStoreIds(List.of(storeId));
        request.setAddressId(addressId);
        return calculateMultiStoreFee(request);
    }

    /**
     * Delivery fee preview. Send {@code items} to have each store's free-delivery threshold checked
     * against its own subtotal; with only {@code storeIds} just the freeDelivery flag can apply.
     */
    public DeliveryFeeResponse calculateMultiStoreFee(DeliveryFeeRequest request) {
        if (request.getAddressId() == null) {
            throw new InvalidDataException("عنوان التوصيل مطلوب.");
        }
        UserAddress address = addressRepository.findById(request.getAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        List<Store> stores;
        Map<Long, Double> netSubtotalByStore = null;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            netSubtotalByStore = new HashMap<>();
            stores = collectStores(priceItems(request.getItems()), netSubtotalByStore);
        } else {
            if (request.getStoreIds() == null || request.getStoreIds().isEmpty()) {
                throw new InvalidDataException("لم يتم توفير متاجر لحساب الرسوم");
            }
            stores = storeRepository.findAllById(request.getStoreIds());
            if (stores.isEmpty()) {
                throw new ResourceNotFoundException("لم يتم العثور على متاجر صالحة");
            }
        }

        double userLat = address.getLatitude();
        double userLng = address.getLongitude();
        Set<Long> freeStoreIds = findFreeDeliveryStoreIds(stores, netSubtotalByStore);
        List<DeliveryFeeResponse.RouteSegmentResponse> segments = buildRouteSegments(
                stores, userLat, userLng, address.getLabel() != null ? address.getLabel() : "User");

        DeliveryFeeResponse response = new DeliveryFeeResponse();
        response.setDeliveryFee(calculatePayingStoresFee(stores, freeStoreIds, userLat, userLng));
        response.setEstimatedTime(stores.get(0).getEstimatedDeliveryTime());
        response.setMaxMinimumDeliveryFee(maxMinimumDeliveryFee(excludeStores(stores, freeStoreIds)));
        response.setTotalDistanceKm(segments.stream()
                .mapToDouble(DeliveryFeeResponse.RouteSegmentResponse::getDistanceKm).sum());
        response.setRouteSegments(segments);
        response.setFreeDeliveryStoreIds(new ArrayList<>(freeStoreIds));
        return response;
    }

    /**
     * Coupon preview with the same engine as checkout. Send {@code addressId} to get the amount a
     * FREE_DELIVERY coupon waives.
     */
    public CouponCheckResponse verifyCoupon(CouponCheckRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new InvalidDataException("رمز القسيمة مطلوب");
        }

        List<OrderItem> items = priceItems(request.getItems());

        Double userLat = null;
        Double userLng = null;
        if (request.getAddressId() != null) {
            UserAddress address = findUserAddress(request.getAddressId(), request.getUserId());
            userLat = address.getLatitude();
            userLng = address.getLongitude();
        }

        OrderQuote quote = quote(items, request.getUserId(), request.getCode(), userLat, userLng);
        Coupon coupon = quote.getCoupon();
        boolean freeDeliveryCoupon = coupon.getDiscountType() == Coupon.DiscountType.FREE_DELIVERY;

        String message = "تم تطبيق القسيمة بنجاح";
        // Without an address the delivery saving isn't known yet, so a free-delivery code is just "valid"
        if (!quote.isCouponApplied() && (!freeDeliveryCoupon || userLat != null)) {
            message = freeDeliveryCoupon
                    ? "التوصيل مجاني لهذا الطلب بالفعل، لن يتم استخدام القسيمة"
                    : "القسيمة لا تمنح أي خصم على هذا الطلب";
        }

        return new CouponCheckResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                quote.getCouponDiscountAmount(),
                message,
                coupon.getDiscountType().name());
    }

    /** Loads an address and makes sure it belongs to the user. */
    public UserAddress findUserAddress(Long addressId, Long userId) {
        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود برقم: " + addressId));
        if (address.getUser() == null || !address.getUser().getUserId().equals(userId)) {
            throw new ResourceNotFoundException("العنوان لا يخص هذا المستخدم");
        }
        return address;
    }

    // =================================================================================
    // DELIVERY FEE RULES
    // =================================================================================

    /**
     * Estimated fee for one store in listings. Pass the store subtotal at checkout to apply its
     * threshold; pass null in listings (threshold is shown on the UI but can't apply yet).
     */
    public double computeFeeForStore(Store store, Double userLat, Double userLng, Double subtotal) {
        if (qualifiesForFreeDelivery(store, subtotal)) return 0.0;
        if (userLat == null || userLng == null) return 0.0;
        return calculateRouteFee(List.of(store), userLat, userLng);
    }

    /**
     * Convenience overload for store listing — subtotal not known yet.
     */
    public double computeFeeForStore(Store store, Double userLat, Double userLng) {
        return computeFeeForStore(store, userLat, userLng, null);
    }

    /**
     * Stores whose delivery is free: freeDelivery is on, or the store's own subtotal (after offers,
     * before coupons) reached its enabled threshold. {@code netSubtotalByStore} may be null when the
     * cart isn't known.
     */
    public Set<Long> findFreeDeliveryStoreIds(List<Store> stores, Map<Long, Double> netSubtotalByStore) {
        Set<Long> freeStoreIds = new LinkedHashSet<>();
        for (Store store : stores) {
            Double storeSubtotal = netSubtotalByStore != null ? netSubtotalByStore.get(store.getStoreId()) : null;
            if (qualifiesForFreeDelivery(store, storeSubtotal)) {
                freeStoreIds.add(store.getStoreId());
            }
        }
        return freeStoreIds;
    }

    private boolean qualifiesForFreeDelivery(Store store, Double storeSubtotal) {
        if (Boolean.TRUE.equals(store.getFreeDelivery())) return true;
        return storeSubtotal != null
                && Boolean.TRUE.equals(store.getFreeDeliveryThresholdEnabled())
                && store.getFreeDeliveryThreshold() != null
                && storeSubtotal >= store.getFreeDeliveryThreshold();
    }

    /**
     * Fee for the stores not in {@code freeStoreIds}. Never more than the fee for the whole route,
     * so a free-delivery store can't make the order more expensive.
     */
    private double calculatePayingStoresFee(List<Store> stores, Set<Long> freeStoreIds, double userLat, double userLng) {
        List<Store> payingStores = excludeStores(stores, freeStoreIds);
        if (payingStores.isEmpty()) return 0.0;

        double fee = calculateRouteFee(payingStores, userLat, userLng);
        if (payingStores.size() < stores.size()) {
            fee = Math.min(fee, calculateRouteFee(stores, userLat, userLng));
        }
        return fee;
    }

    /**
     * Route distance × highest per-km rate, rounded up to the nearest 10, never below the highest
     * minimum delivery fee of the given stores.
     */
    public double calculateRouteFee(List<Store> stores, double userLat, double userLng) {
        if (stores.isEmpty()) return 0.0;

        double maxFeePerKm = stores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        double fee = mathUtil.roundUpToNearestTen(calculateOptimizedDistance(stores, userLat, userLng) * maxFeePerKm);
        return Math.max(fee, maxMinimumDeliveryFee(stores));
    }

    public double calculateOptimizedDistance(List<Store> stores, double userLat, double userLng) {
        return buildRouteSegments(stores, userLat, userLng, "User").stream()
                .mapToDouble(DeliveryFeeResponse.RouteSegmentResponse::getDistanceKm)
                .sum();
    }

    /** The driver's route: farthest pickup first, the customer last (stores without coordinates are skipped). */
    private List<DeliveryFeeResponse.RouteSegmentResponse> buildRouteSegments(
            List<Store> stores, double userLat, double userLng, String userLabel) {
        List<Store> route = optimizedPickupOrder(stores, userLat, userLng);
        List<DeliveryFeeResponse.RouteSegmentResponse> segments = new ArrayList<>();

        for (int i = 0; i < route.size(); i++) {
            Store from = route.get(i);
            if (i < route.size() - 1) {
                Store to = route.get(i + 1);
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        from.getName(),
                        to.getName(),
                        distanceUtil.calculateDistance(from.getLatitude(), from.getLongitude(),
                                to.getLatitude(), to.getLongitude()),
                        "STORE_TO_STORE"));
            } else {
                segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                        from.getName(),
                        userLabel,
                        distanceUtil.calculateDistance(from.getLatitude(), from.getLongitude(), userLat, userLng),
                        "STORE_TO_USER"));
            }
        }
        return segments;
    }

    /** Nearest-neighbour from the customer outwards, then reversed into driving order. */
    private List<Store> optimizedPickupOrder(List<Store> stores, double userLat, double userLng) {
        List<Store> unvisited = new ArrayList<>(stores);
        List<Store> route = new ArrayList<>();
        double currentLat = userLat;
        double currentLng = userLng;

        while (!unvisited.isEmpty()) {
            Store closestStore = null;
            double minDist = Double.MAX_VALUE;

            for (Store s : unvisited) {
                if (s.getLatitude() == null || s.getLongitude() == null)
                    continue;

                double d = distanceUtil.calculateDistance(currentLat, currentLng, s.getLatitude(), s.getLongitude());
                if (d < minDist) {
                    minDist = d;
                    closestStore = s;
                }
            }

            if (closestStore == null) {
                break;
            }
            route.add(closestStore);
            currentLat = closestStore.getLatitude();
            currentLng = closestStore.getLongitude();
            unvisited.remove(closestStore);
        }

        Collections.reverse(route);
        return route;
    }

    private double maxMinimumDeliveryFee(List<Store> stores) {
        return stores.stream()
                .mapToDouble(s -> s.getMinimumDeliveryFee() != null ? s.getMinimumDeliveryFee() : 0.0)
                .max().orElse(0.0);
    }

    private List<Store> excludeStores(List<Store> stores, Set<Long> excludedStoreIds) {
        return stores.stream().filter(s -> !excludedStoreIds.contains(s.getStoreId())).toList();
    }

    /** Distinct stores of the lines (cart order); fills each store's subtotal after offers. */
    private List<Store> collectStores(List<OrderItem> items, Map<Long, Double> netSubtotalByStore) {
        Map<Long, Store> storesById = new LinkedHashMap<>();
        for (OrderItem item : items) {
            Store store = item.getProduct().getStore();
            storesById.putIfAbsent(store.getStoreId(), store);
            netSubtotalByStore.merge(store.getStoreId(), item.getNetTotalPrice(), Double::sum);
        }
        return new ArrayList<>(storesById.values());
    }
}
