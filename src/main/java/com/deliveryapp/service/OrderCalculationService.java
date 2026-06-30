package com.deliveryapp.service;

import com.deliveryapp.dto.order.CouponCheckRequest;
import com.deliveryapp.dto.order.CouponCheckResponse;
import com.deliveryapp.dto.order.DeliveryFeeResponse;
import com.deliveryapp.dto.order.OrderItemRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.ProductVariantRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserAddressRepository;
import com.deliveryapp.util.DistanceUtil;
import com.deliveryapp.util.MathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCalculationService {

    private final StoreRepository storeRepository;
    private final UserAddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final DistanceUtil distanceUtil;
    private final MathUtil mathUtil;
    private final PricingService pricingService;
    private final CouponService couponService;

    public DeliveryFeeResponse calculateDeliveryFee(Long storeId, Long addressId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        double distance = calculateOptimizedDistance(
                List.of(store),
                address.getLatitude(),
                address.getLongitude());

        Double feePerKm = store.getDeliveryFeeKM() != null ? store.getDeliveryFeeKM() : 0.0;
        Double minFee = store.getMinimumDeliveryFee() != null ? store.getMinimumDeliveryFee() : 0.0;

        double rawDeliveryFee = distance * feePerKm;
        double deliveryFee = mathUtil.roundUpToNearestTen(rawDeliveryFee);

        if (deliveryFee < minFee) {
            deliveryFee = minFee;
        }

        DeliveryFeeResponse response = new DeliveryFeeResponse();
        response.setDeliveryFee(deliveryFee);
        response.setEstimatedTime(store.getEstimatedDeliveryTime());
        response.setMaxMinimumDeliveryFee(minFee);
        response.setTotalDistanceKm(distance);
        response.setRouteSegments(List.of(
                new DeliveryFeeResponse.RouteSegmentResponse(
                        store.getName(),
                        address.getLabel() != null ? address.getLabel() : "User",
                        distance,
                        "STORE_TO_USER")));

        return response;
    }

    public DeliveryFeeResponse calculateMultiStoreFee(List<Long> storeIds, Long addressId) {
        if (storeIds == null || storeIds.isEmpty()) {
            throw new InvalidDataException("لم يتم توفير متاجر لحساب الرسوم");
        }

        List<Store> stores = storeRepository.findAllById(storeIds);
        if (stores.isEmpty()) {
            throw new ResourceNotFoundException("لم يتم العثور على متاجر صالحة");
        }

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("العنوان غير موجود"));

        double maxFeePerKm = stores.stream()
                .mapToDouble(s -> s.getDeliveryFeeKM() != null ? s.getDeliveryFeeKM() : 0.0)
                .max().orElse(0.0);

        double maxMinimumDeliveryFee = stores.stream()
                .mapToDouble(s -> s.getMinimumDeliveryFee() != null ? s.getMinimumDeliveryFee() : 0.0)
                .max().orElse(0.0);

        double totalDistanceKm = calculateOptimizedDistance(stores, address.getLatitude(), address.getLongitude());

        double rawDeliveryFee = totalDistanceKm * maxFeePerKm;
        double deliveryFee = mathUtil.roundUpToNearestTen(rawDeliveryFee);

        if (deliveryFee < maxMinimumDeliveryFee) {
            deliveryFee = maxMinimumDeliveryFee;
        }

        String estimatedTime = stores.get(0).getEstimatedDeliveryTime();

        List<DeliveryFeeResponse.RouteSegmentResponse> segments = new ArrayList<>();
        for (int i = 0; i < stores.size() - 1; i++) {
            Store fromStore = stores.get(i);
            Store toStore = stores.get(i + 1);
            double segmentDistance = distanceUtil.calculateDistance(
                    fromStore.getLatitude(),
                    fromStore.getLongitude(),
                    toStore.getLatitude(),
                    toStore.getLongitude());
            segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                    fromStore.getName(),
                    toStore.getName(),
                    segmentDistance,
                    "STORE_TO_STORE"));
        }

        if (!stores.isEmpty()) {
            Store lastStore = stores.get(stores.size() - 1);
            double lastSegmentDistance = distanceUtil.calculateDistance(
                    lastStore.getLatitude(),
                    lastStore.getLongitude(),
                    address.getLatitude(),
                    address.getLongitude());
            segments.add(new DeliveryFeeResponse.RouteSegmentResponse(
                    lastStore.getName(),
                    address.getLabel() != null ? address.getLabel() : "User",
                    lastSegmentDistance,
                    "STORE_TO_USER"));
        }

        DeliveryFeeResponse response = new DeliveryFeeResponse();
        response.setDeliveryFee(deliveryFee);
        response.setEstimatedTime(estimatedTime);
        response.setMaxMinimumDeliveryFee(maxMinimumDeliveryFee);
        response.setTotalDistanceKm(totalDistanceKm);
        response.setRouteSegments(segments);

        return response;
    }

    public CouponCheckResponse verifyCoupon(CouponCheckRequest request) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        List<OrderItem> tempItems = new ArrayList<>();
        double subtotal = 0.0;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود"));

            double price = pricingService.getFinalPriceInSYP(product);
            if (itemReq.getVariantId() != null) {
                ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("النوع غير موجود"));
                price += pricingService.getVariantFinalPriceInSYP(variant);
            }

            OrderItem tempItem = new OrderItem();
            tempItem.setProduct(product);
            tempItem.setQuantity(itemReq.getQuantity());
            tempItem.setUnitPrice(price);
            tempItem.setTotalPrice(price * itemReq.getQuantity());

            tempItems.add(tempItem);
            subtotal += tempItem.getTotalPrice();
        }

        Coupon coupon = couponService.validateCouponForOrder(
                request.getCode(),
                request.getUserId(),
                tempItems,
                store);

        double discount = couponService.calculateDiscount(coupon, subtotal, 0.0);

        return new CouponCheckResponse(
                coupon.getCouponId(),
                coupon.getCode(),
                discount,
                "تم تطبيق القسيمة بنجاح",
                coupon.getDiscountType().name());
    }

    public double calculateOptimizedDistance(List<Store> stores, double userLat, double userLng) {
        if (stores.isEmpty())
            return 0.0;

        List<Store> unvisited = new ArrayList<>(stores);
        double currentLat = userLat;
        double currentLng = userLng;
        double totalDistance = 0.0;

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

            if (closestStore != null) {
                totalDistance += minDist;
                currentLat = closestStore.getLatitude();
                currentLng = closestStore.getLongitude();
                unvisited.remove(closestStore);
            } else {
                break;
            }
        }

        return totalDistance;
    }
}