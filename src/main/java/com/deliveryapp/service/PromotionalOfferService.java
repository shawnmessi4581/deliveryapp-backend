package com.deliveryapp.service;

import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.OrderItem;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.enums.OfferType;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.repository.StoreRepository;
import com.deliveryapp.repository.UserRepository;
import com.deliveryapp.util.MathUtil;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Core business logic for {@link PromotionalOffer} lifecycle and application.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>Admin methods accept any product.</li>
 *   <li>Vendor methods always enforce {@code vendorStoreId} — a vendor can only manage
 *       offers they created, on their own store's products.</li>
 * </ul>
 *
 * <h3>Offer application engine</h3>
 * {@link #applyOffers(List)} is called once per checkout with all priced lines. Lines of the same
 * product (e.g. different variants or colors) are pooled, so "Buy 1 Get 1" works across them:
 * <pre>
 *   cycles        = totalQuantity / (buyQty + getQty)
 *   rewardedUnits = cycles * getQty            (the cheapest units in the pool)
 *   discount      = Σ discountPerUnit(unit price) over the rewarded units
 * </pre>
 * When several offers are active on a product, the one worth the most to the customer is applied.
 */
@Service
@RequiredArgsConstructor
public class PromotionalOfferService {

    private final PromotionalOfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MathUtil mathUtil;
    private final UrlUtil urlUtil;

    // =================================================================================
    // ADMIN CRUD
    // =================================================================================

    /** Create a new offer (Admin). Paid by the platform unless {@code fundedByStore} is true. */
    @Transactional
    public OfferResponse adminCreateOffer(OfferRequest request, Long adminUserId) {
        Product product = resolveProduct(request.getProductId());
        boolean fundedByStore = Boolean.TRUE.equals(request.getFundedByStore());
        assertCanBeStoreFunded(product, fundedByStore);

        // Anchor to the product's store so it shows up in that store's listings
        PromotionalOffer offer = buildOffer(request, product, product.getStore(), adminUserId);
        offer.setFundedByStore(fundedByStore);
        offer = offerRepository.save(offer);
        return toResponse(offer);
    }

    /** Get all offers paged (Admin). */
    public Page<OfferResponse> adminGetAllOffers(Pageable pageable) {
        return offerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    /** Get a single offer by ID (Admin). */
    public OfferResponse adminGetOfferById(Long offerId) {
        return toResponse(findById(offerId));
    }

    /** Update any offer (Admin). Who pays is only changed when {@code fundedByStore} is sent. */
    @Transactional
    public OfferResponse adminUpdateOffer(Long offerId, OfferRequest request, Long adminUserId) {
        PromotionalOffer offer = findById(offerId);
        Product product = resolveProduct(request.getProductId());

        // A vendor's own offer stays on that vendor's store's products
        if (isCreatedByVendor(offer) && offer.getStore() != null) {
            assertProductBelongsToStore(product, offer.getStore().getStoreId());
        }

        // Not sent → keep the current payer, so an edit from an older dashboard can't silently move the cost
        boolean fundedByStore = request.getFundedByStore() != null
                ? request.getFundedByStore()
                : isFundedByStore(offer);
        assertCanBeStoreFunded(product, fundedByStore);

        applyRequestToOffer(offer, request, product, product.getStore());
        offer.setFundedByStore(fundedByStore);
        offer.setUpdatedAt(LocalDateTime.now());
        return toResponse(offerRepository.save(offer));
    }

    /** Toggle active status (Admin). */
    @Transactional
    public OfferResponse adminToggleStatus(Long offerId) {
        PromotionalOffer offer = findById(offerId);
        offer.setIsActive(!offer.getIsActive());
        offer.setUpdatedAt(LocalDateTime.now());
        return toResponse(offerRepository.save(offer));
    }

    /** Hard delete (Admin). */
    @Transactional
    public void adminDeleteOffer(Long offerId) {
        if (!offerRepository.existsById(offerId)) {
            throw new ResourceNotFoundException("العرض الترويجي غير موجود برقم: " + offerId);
        }
        offerRepository.deleteById(offerId);
    }

    // =================================================================================
    // VENDOR CRUD
    // =================================================================================

    /** Create a new offer scoped to the vendor's store. Paid by that store. */
    @Transactional
    public OfferResponse vendorCreateOffer(OfferRequest request, Long vendorStoreId, Long vendorUserId) {
        Product product = resolveProduct(request.getProductId());
        assertProductBelongsToStore(product, vendorStoreId);

        Store store = storeRepository.findById(vendorStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        PromotionalOffer offer = buildOffer(request, product, store, vendorUserId);
        offer.setFundedByStore(true); // a vendor's own promotion is always paid by their store
        offer = offerRepository.save(offer);
        return toResponse(offer);
    }

    /** Paged list of offers on the vendor's products (admin offers included, read-only). */
    public Page<OfferResponse> vendorGetMyOffers(Long vendorStoreId, Pageable pageable) {
        return offerRepository.findByStoreStoreIdOrderByCreatedAtDesc(vendorStoreId, pageable)
                .map(this::toResponse);
    }

    /** Get a single offer on the vendor's products by ID (read-only for admin offers). */
    public OfferResponse vendorGetOfferById(Long offerId, Long vendorStoreId) {
        PromotionalOffer offer = findById(offerId);
        assertOfferIsOnStore(offer, vendorStoreId);
        return toResponse(offer);
    }

    /** Update an offer, enforcing store ownership and product scope. */
    @Transactional
    public OfferResponse vendorUpdateOffer(Long offerId, OfferRequest request,
                                           Long vendorStoreId, Long vendorUserId) {
        PromotionalOffer offer = findById(offerId);
        assertOfferBelongsToStore(offer, vendorStoreId);

        Product product = resolveProduct(request.getProductId());
        assertProductBelongsToStore(product, vendorStoreId);

        applyRequestToOffer(offer, request, product, offer.getStore());
        // Even if an admin had sponsored it, the vendor's own changes are paid by their store
        offer.setFundedByStore(true);
        offer.setUpdatedAt(LocalDateTime.now());
        return toResponse(offerRepository.save(offer));
    }

    /** Toggle active status, enforcing store ownership. */
    @Transactional
    public OfferResponse vendorToggleStatus(Long offerId, Long vendorStoreId) {
        PromotionalOffer offer = findById(offerId);
        assertOfferBelongsToStore(offer, vendorStoreId);
        offer.setIsActive(!offer.getIsActive());
        offer.setUpdatedAt(LocalDateTime.now());
        return toResponse(offerRepository.save(offer));
    }

    /** Delete an offer, enforcing store ownership. */
    @Transactional
    public void vendorDeleteOffer(Long offerId, Long vendorStoreId) {
        PromotionalOffer offer = findById(offerId);
        assertOfferBelongsToStore(offer, vendorStoreId);
        offerRepository.delete(offer);
    }

    // =================================================================================
    // PUBLIC
    // =================================================================================

    /** All currently active & valid offers (public customer listing). */
    public List<OfferResponse> getPublicActiveOffers() {
        return offerRepository.findAllActiveAndValid(LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** Active & valid offers for a specific store (public). */
    public List<OfferResponse> getPublicActiveOffersForStore(Long storeId) {
        return offerRepository.findActiveOffersForProductsInStore(storeId, LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =================================================================================
    // OFFER APPLICATION ENGINE  (called by OrderCalculationService for checkout & previews)
    // =================================================================================

    /**
     * Applies the best active offer to every product in the cart, writing
     * {@code appliedOfferId}, {@code offerDiscountAmount} and {@code offerFundedByStore} on the lines.
     *
     * <p>Lines must already carry their SYP {@code unitPrice} and {@code quantity}. Partial deal
     * cycles are never rewarded, and the rewarded units are always the cheapest ones.
     */
    public void applyOffers(List<OrderItem> items) {
        LocalDateTime now = LocalDateTime.now();

        Map<Long, List<OrderItem>> linesByProduct = new LinkedHashMap<>();
        for (OrderItem item : items) {
            item.setAppliedOfferId(null);
            item.setOfferDiscountAmount(0.0);
            item.setStoreOfferDiscountAmount(0.0);
            item.setOfferFundedByStore(false);
            linesByProduct.computeIfAbsent(item.getProduct().getProductId(), id -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<Long, List<OrderItem>> entry : linesByProduct.entrySet()) {
            List<PromotionalOffer> candidates = offerRepository.findActiveOffersForProduct(entry.getKey(), now);
            if (candidates.isEmpty()) {
                continue;
            }

            List<OrderItem> cheapestFirst = new ArrayList<>(entry.getValue());
            cheapestFirst.sort(Comparator.comparingDouble(OrderItem::getUnitPrice));
            int totalQuantity = cheapestFirst.stream().mapToInt(OrderItem::getQuantity).sum();

            // Candidates come latest-first, so a tie keeps the latest offer
            PromotionalOffer best = null;
            double[] bestDiscounts = null;
            double bestTotal = 0.0;
            for (PromotionalOffer offer : candidates) {
                double[] discounts = computeLineDiscounts(offer, cheapestFirst, totalQuantity);
                double total = 0.0;
                for (double d : discounts) total += d;
                if (total > bestTotal) {
                    best = offer;
                    bestDiscounts = discounts;
                    bestTotal = total;
                }
            }
            if (best == null) {
                continue;
            }

            boolean fundedByStore = isFundedByStore(best);
            for (int i = 0; i < cheapestFirst.size(); i++) {
                if (bestDiscounts[i] > 0) {
                    OrderItem line = cheapestFirst.get(i);
                    line.setAppliedOfferId(best.getOfferId());
                    line.setOfferDiscountAmount(bestDiscounts[i]);
                    line.setStoreOfferDiscountAmount(storeShare(line, bestDiscounts[i]));
                    line.setOfferFundedByStore(fundedByStore);
                }
            }
        }
    }

    /**
     * The customer discount converted to the store's own price (store price / customer price), e.g. a free
     * unit costs the store 10,000 while the customer saved 11,000 — the platform absorbs its markup.
     */
    private double storeShare(OrderItem line, double customerDiscount) {
        Double storeUnitPrice = line.getStoreUnitPrice();
        double unitPrice = line.getUnitPrice();
        if (storeUnitPrice == null || unitPrice <= 0 || storeUnitPrice == unitPrice) {
            return customerDiscount;
        }
        return mathUtil.roundMoney(customerDiscount * storeUnitPrice / unitPrice);
    }

    /** Discount per line (same order as {@code cheapestFirst}) if {@code offer} were applied to the pool. */
    private double[] computeLineDiscounts(PromotionalOffer offer, List<OrderItem> cheapestFirst, int totalQuantity) {
        double[] discounts = new double[cheapestFirst.size()];
        int buyQty = offer.getBuyQuantity() != null ? offer.getBuyQuantity() : 0;
        int getQty = offer.getGetQuantity() != null ? offer.getGetQuantity() : 0;
        if (buyQty < 1 || getQty < 1) {
            return discounts;
        }

        int remaining = (totalQuantity / (buyQty + getQty)) * getQty;
        for (int i = 0; i < cheapestFirst.size() && remaining > 0; i++) {
            OrderItem line = cheapestFirst.get(i);
            int units = Math.min(remaining, line.getQuantity());
            discounts[i] = mathUtil.roundMoney(units * computeDiscountPerUnit(offer, line.getUnitPrice()));
            remaining -= units;
        }
        return discounts;
    }

    /**
     * Who pays the discount. Offers saved before {@code fundedByStore} existed fall back to who
     * created them: vendor → store, admin → platform.
     */
    public boolean isFundedByStore(PromotionalOffer offer) {
        if (offer.getFundedByStore() != null) {
            return offer.getFundedByStore();
        }
        return isCreatedByVendor(offer);
    }

    /** Ownership, not funding: vendors may only change offers they created themselves. */
    private boolean isCreatedByVendor(PromotionalOffer offer) {
        if (offer.getCreatedBy() == null) {
            return false;
        }
        return userRepository.findById(offer.getCreatedBy())
                .map(user -> user.getUserType() == UserType.VENDOR)
                .orElse(false);
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private double computeDiscountPerUnit(PromotionalOffer offer, double unitPrice) {
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y_FREE -> unitPrice; // 100 % off
            case BUY_X_GET_Y_PERCENT_OFF -> {
                double pct = offer.getDiscountPercent() != null ? offer.getDiscountPercent() : 0.0;
                yield unitPrice * (Math.min(pct, 100.0) / 100.0);
            }
            case BUY_X_GET_Y_FIXED_OFF -> {
                double fixed = offer.getDiscountValue() != null ? offer.getDiscountValue() : 0.0;
                yield Math.min(fixed, unitPrice); // Cannot discount more than the price
            }
        };
    }

    private PromotionalOffer buildOffer(OfferRequest req, Product product, Store store, Long userId) {
        PromotionalOffer offer = new PromotionalOffer();
        applyRequestToOffer(offer, req, product, store);
        offer.setCreatedBy(userId);
        offer.setCreatedAt(LocalDateTime.now());
        offer.setUpdatedAt(LocalDateTime.now());
        return offer;
    }

    private void applyRequestToOffer(PromotionalOffer offer, OfferRequest req, Product product, Store store) {
        validateRequest(req);
        offer.setTitle(req.getTitle());
        offer.setDescription(req.getDescription());
        offer.setOfferType(req.getOfferType());
        offer.setBuyQuantity(req.getBuyQuantity());
        offer.setGetQuantity(req.getGetQuantity());
        offer.setDiscountPercent(req.getDiscountPercent());
        offer.setDiscountValue(req.getDiscountValue());
        offer.setProduct(product);
        offer.setStore(store);
        offer.setStartDate(req.getStartDate());
        offer.setEndDate(req.getEndDate());
        offer.setIsActive(req.getIsActive() != null ? req.getIsActive() : true);
    }

    private void validateRequest(OfferRequest req) {
        if (req.getOfferType() == OfferType.BUY_X_GET_Y_PERCENT_OFF) {
            if (req.getDiscountPercent() == null || req.getDiscountPercent() <= 0 || req.getDiscountPercent() > 100) {
                throw new InvalidDataException("نسبة الخصم مطلوبة ويجب أن تكون بين 1 و 100");
            }
        }
        if (req.getOfferType() == OfferType.BUY_X_GET_Y_FIXED_OFF) {
            if (req.getDiscountValue() == null || req.getDiscountValue() <= 0) {
                throw new InvalidDataException("قيمة الخصم الثابتة مطلوبة ويجب أن تكون أكبر من 0");
            }
        }
        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new InvalidDataException("تاريخ انتهاء العرض يجب أن يكون بعد تاريخ البداية");
        }
    }

    /** Map entity → response DTO. */
    public OfferResponse toResponse(PromotionalOffer offer) {
        OfferResponse dto = new OfferResponse();
        dto.setOfferId(offer.getOfferId());
        dto.setTitle(offer.getTitle());
        dto.setDescription(offer.getDescription());
        dto.setOfferType(offer.getOfferType());
        dto.setBuyQuantity(offer.getBuyQuantity());
        dto.setGetQuantity(offer.getGetQuantity());
        dto.setDiscountPercent(offer.getDiscountPercent());
        dto.setDiscountValue(offer.getDiscountValue());
        dto.setSummary(buildSummary(offer));

        if (offer.getProduct() != null) {
            dto.setProductId(offer.getProduct().getProductId());
            dto.setProductName(offer.getProduct().getName());
            dto.setProductImage(urlUtil.getFullUrl(offer.getProduct().getImage()));
        }

        if (offer.getStore() != null) {
            dto.setStoreId(offer.getStore().getStoreId());
            dto.setStoreName(offer.getStore().getName());
        }
        dto.setFundedByStore(isFundedByStore(offer));
        dto.setCreatedByVendor(isCreatedByVendor(offer));

        dto.setStartDate(offer.getStartDate());
        dto.setEndDate(offer.getEndDate());
        dto.setIsActive(offer.getIsActive());
        dto.setCreatedBy(offer.getCreatedBy());
        dto.setCreatedAt(offer.getCreatedAt());
        dto.setUpdatedAt(offer.getUpdatedAt());
        return dto;
    }

    /** Generate a concise Arabic human-readable summary of the offer deal. */
    private String buildSummary(PromotionalOffer offer) {
        String base = "اشتر " + offer.getBuyQuantity() + " واحصل على " + offer.getGetQuantity();
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y_FREE -> base + " مجاناً";
            case BUY_X_GET_Y_PERCENT_OFF ->
                    base + " بخصم " + formatNumber(offer.getDiscountPercent()) + "%";
            case BUY_X_GET_Y_FIXED_OFF ->
                    base + " بخصم " + formatNumber(offer.getDiscountValue()) + " ل.س";
        };
    }

    /** 12.0 → "12", 12.5 → "12.5" */
    private String formatNumber(Double value) {
        if (value == null) {
            return "0";
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private PromotionalOffer findById(Long offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("العرض الترويجي غير موجود برقم: " + offerId));
    }

    private Product resolveProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود برقم: " + productId));
    }

    private void assertProductBelongsToStore(Product product, Long storeId) {
        if (product.getStore() == null || !product.getStore().getStoreId().equals(storeId)) {
            throw new InvalidDataException("المنتج لا ينتمي لمتجرك");
        }
    }

    private void assertOfferIsOnStore(PromotionalOffer offer, Long storeId) {
        if (offer.getStore() == null || !Objects.equals(offer.getStore().getStoreId(), storeId)) {
            throw new InvalidDataException("العرض لا ينتمي لمتجرك");
        }
    }

    // Vendors may only change offers they created — admin offers stay read-only, even when the store pays
    private void assertOfferBelongsToStore(PromotionalOffer offer, Long storeId) {
        assertOfferIsOnStore(offer, storeId);
        if (!isCreatedByVendor(offer)) {
            throw new InvalidDataException("هذا العرض من إدارة التطبيق ولا يمكن تعديله من المتجر");
        }
    }

    private void assertCanBeStoreFunded(Product product, boolean fundedByStore) {
        if (fundedByStore && product.getStore() == null) {
            throw new InvalidDataException("لا يمكن تحميل الخصم على متجر لأن المنتج غير مرتبط بمتجر");
        }
    }
}
