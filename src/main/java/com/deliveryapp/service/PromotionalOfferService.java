package com.deliveryapp.service;

import com.deliveryapp.dto.offer.OfferApplicationResult;
import com.deliveryapp.dto.offer.OfferRequest;
import com.deliveryapp.dto.offer.OfferResponse;
import com.deliveryapp.entity.Product;
import com.deliveryapp.entity.PromotionalOffer;
import com.deliveryapp.entity.Store;
import com.deliveryapp.enums.OfferType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.ProductRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for {@link PromotionalOffer} lifecycle and application.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>Admin methods accept any {@code storeId} (may be null for global offers).</li>
 *   <li>Vendor methods always enforce {@code vendorStoreId} — a vendor can only manage
 *       offers that belong to their own store and their own products.</li>
 * </ul>
 *
 * <h3>Offer application engine</h3>
 * {@link #applyOfferToItem(Long, int, double)} is called once per order line-item during
 * checkout.  It fetches the best active offer for the product and computes the total
 * discount for the requested quantity using the "complete cycles" model:
 * <pre>
 *   cycles = quantity / (buyQty + getQty)
 *   rewardedUnits = cycles * getQty
 *   discount = rewardedUnits * discountPerUnit
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class PromotionalOfferService {

    private final PromotionalOfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    // =================================================================================
    // ADMIN CRUD
    // =================================================================================

    /** Create a new offer (Admin). {@code storeId} in the request may be null for global. */
    @Transactional
    public OfferResponse adminCreateOffer(OfferRequest request, Long adminUserId) {
        Product product = resolveProduct(request.getProductId());
        Store store = request.getProductId() != null ? product.getStore() : null;

        // Admin may optionally anchor to the product's store automatically
        PromotionalOffer offer = buildOffer(request, product, store, adminUserId);
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

    /** Update any offer (Admin). */
    @Transactional
    public OfferResponse adminUpdateOffer(Long offerId, OfferRequest request, Long adminUserId) {
        PromotionalOffer offer = findById(offerId);
        Product product = resolveProduct(request.getProductId());

        applyRequestToOffer(offer, request, product, offer.getStore());
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

    /** Create a new offer scoped to the vendor's store. */
    @Transactional
    public OfferResponse vendorCreateOffer(OfferRequest request, Long vendorStoreId, Long vendorUserId) {
        Product product = resolveProduct(request.getProductId());
        assertProductBelongsToStore(product, vendorStoreId);

        Store store = storeRepository.findById(vendorStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        PromotionalOffer offer = buildOffer(request, product, store, vendorUserId);
        offer = offerRepository.save(offer);
        return toResponse(offer);
    }

    /** Paged list of the vendor's own offers. */
    public Page<OfferResponse> vendorGetMyOffers(Long vendorStoreId, Pageable pageable) {
        return offerRepository.findByStoreStoreIdOrderByCreatedAtDesc(vendorStoreId, pageable)
                .map(this::toResponse);
    }

    /** Get a single offer by ID, enforcing store ownership. */
    public OfferResponse vendorGetOfferById(Long offerId, Long vendorStoreId) {
        PromotionalOffer offer = findById(offerId);
        assertOfferBelongsToStore(offer, vendorStoreId);
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
    // OFFER APPLICATION ENGINE  (called by OrderService at checkout)
    // =================================================================================

    /**
     * Applies the best active promotional offer for a product to a given quantity,
     * and returns the total SYP discount to deduct from that line-item.
     *
     * <p>Uses the "complete deal cycles" model so partial cycles are never rewarded:
     * <pre>
     *   totalGroupSize = buyQty + getQty
     *   completeCycles = Math.floor(quantity / totalGroupSize)
     *   rewardedUnits  = completeCycles * getQty
     *   discount       = rewardedUnits * discountPerUnit
     * </pre>
     *
     * @param productId   the product being ordered
     * @param quantity    units the customer is ordering
     * @param unitPrice   the SYP unit price (after any product-level offer price, etc.)
     * @return an {@link OfferApplicationResult} — never null; use {@link OfferApplicationResult#none()} if inapplicable
     */
    public OfferApplicationResult applyOfferToItem(Long productId, int quantity, double unitPrice) {
        List<PromotionalOffer> candidates =
                offerRepository.findActiveOffersForProduct(productId, LocalDateTime.now());

        if (candidates.isEmpty()) {
            return OfferApplicationResult.none();
        }

        // Take the first candidate (already ordered: vendor > admin, latest > oldest)
        PromotionalOffer offer = candidates.get(0);

        int buyQty = offer.getBuyQuantity();
        int getQty = offer.getGetQuantity();
        int totalGroupSize = buyQty + getQty;

        if (quantity < totalGroupSize) {
            // Not enough units to complete even one deal cycle
            return OfferApplicationResult.none();
        }

        int completeCycles = quantity / totalGroupSize;
        int rewardedUnits  = completeCycles * getQty;

        double discountPerUnit = computeDiscountPerUnit(offer, unitPrice);
        double totalDiscount   = rewardedUnits * discountPerUnit;

        String rewardDesc = buildRewardDescription(offer, rewardedUnits, discountPerUnit);

        return new OfferApplicationResult(
                offer.getOfferId(),
                offer.getTitle(),
                totalDiscount,
                rewardDesc
        );
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private double computeDiscountPerUnit(PromotionalOffer offer, double unitPrice) {
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y_FREE -> unitPrice; // 100 % off
            case BUY_X_GET_Y_PERCENT_OFF -> {
                double pct = offer.getDiscountPercent() != null ? offer.getDiscountPercent() : 0.0;
                yield unitPrice * (pct / 100.0);
            }
            case BUY_X_GET_Y_FIXED_OFF -> {
                double fixed = offer.getDiscountValue() != null ? offer.getDiscountValue() : 0.0;
                yield Math.min(fixed, unitPrice); // Cannot discount more than the price
            }
        };
    }

    private String buildRewardDescription(PromotionalOffer offer, int rewardedUnits, double discountPerUnit) {
        return switch (offer.getOfferType()) {
            case BUY_X_GET_Y_FREE ->
                    rewardedUnits + (rewardedUnits == 1 ? " وحدة مجانية" : " وحدات مجانية");
            case BUY_X_GET_Y_PERCENT_OFF ->
                    "خصم " + offer.getDiscountPercent().intValue() + "% على "
                    + rewardedUnits + (rewardedUnits == 1 ? " وحدة" : " وحدات");
            case BUY_X_GET_Y_FIXED_OFF ->
                    "خصم " + (long) discountPerUnit + " ل.س على "
                    + rewardedUnits + (rewardedUnits == 1 ? " وحدة" : " وحدات");
        };
    }

    private PromotionalOffer buildOffer(OfferRequest req, Product product, Store store, Long userId) {
        validateRequest(req);
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
            dto.setProductImage(offer.getProduct().getImage());
        }

        if (offer.getStore() != null) {
            dto.setStoreId(offer.getStore().getStoreId());
            dto.setStoreName(offer.getStore().getName());
        }

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
                    base + " بخصم " + (offer.getDiscountPercent() != null
                            ? offer.getDiscountPercent().intValue() : 0) + "%";
            case BUY_X_GET_Y_FIXED_OFF ->
                    base + " بخصم " + (offer.getDiscountValue() != null
                            ? offer.getDiscountValue().longValue() : 0) + " ل.س";
        };
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

    private void assertOfferBelongsToStore(PromotionalOffer offer, Long storeId) {
        if (offer.getStore() == null || !offer.getStore().getStoreId().equals(storeId)) {
            throw new InvalidDataException("العرض لا ينتمي لمتجرك");
        }
    }
}
