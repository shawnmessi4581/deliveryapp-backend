package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.ProductResponse;
import com.deliveryapp.dto.offer.AvailableRewardResponse;
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
import com.deliveryapp.mapper.catalog.CatalogMapper;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core business logic for {@link PromotionalOffer} lifecycle and application.
 *
 * <h3>Security contract</h3>
 * <ul>
 *   <li>Admin methods accept any products, as long as one offer's products all come from one store.</li>
 *   <li>Vendor methods always enforce {@code vendorStoreId} — a vendor can only manage
 *       offers they created, on their own store's products.</li>
 * </ul>
 *
 * <h3>Offer application engine</h3>
 * {@link #applyOffers(List)} is called once per checkout with all priced lines. Every offer has buy
 * options (what the customer pays for) and reward options (what they get); all variants count.
 * <ul>
 *   <li><b>Chosen rewards</b> – lines the customer marked with {@code rewardOfferId} are rewarded, as
 *       many as the paid buy-option units earn ({@code buyQty} paid units → {@code getQty} rewards).
 *       If they picked more than they earned, the dearest picks are rewarded; the rest stay full price.</li>
 *   <li><b>Automatic</b> – without chosen rewards (e.g. older app versions) the cheapest reward-option
 *       units are rewarded, as many as the cart earns. For a single product this is the old rule:
 *       {@code cycles = units / (buyQty + getQty)}, the cheapest {@code cycles × getQty} units rewarded.</li>
 * </ul>
 * Offers are applied best-first (most saving for the customer; ties go to the latest offer). A unit
 * counts toward one offer only and a line is discounted by one offer only. A deal repeats within an
 * order up to {@code maxUsesPerOrder}.
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
    private final CatalogMapper catalogMapper;

    // =================================================================================
    // ADMIN CRUD
    // =================================================================================

    /** Create a new offer (Admin). Paid by the platform unless {@code fundedByStore} is true. */
    @Transactional
    public OfferResponse adminCreateOffer(OfferRequest request, Long adminUserId) {
        OfferProducts products = resolveOfferProducts(request, null);
        boolean fundedByStore = Boolean.TRUE.equals(request.getFundedByStore());
        assertCanBeStoreFunded(products.main(), fundedByStore);

        // Anchor to the products' store so it shows up in that store's listings
        PromotionalOffer offer = buildOffer(request, products, products.main().getStore(), adminUserId);
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
        OfferProducts products = resolveOfferProducts(request, offer);

        // A vendor's own offer stays on that vendor's store's products
        if (isCreatedByVendor(offer) && offer.getStore() != null) {
            assertProductsBelongToStore(products, offer.getStore().getStoreId());
        }

        // Not sent → keep the current payer, so an edit from an older dashboard can't silently move the cost
        boolean fundedByStore = request.getFundedByStore() != null
                ? request.getFundedByStore()
                : isFundedByStore(offer);
        assertCanBeStoreFunded(products.main(), fundedByStore);

        applyRequestToOffer(offer, request, products, products.main().getStore());
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
        OfferProducts products = resolveOfferProducts(request, null);
        assertProductsBelongToStore(products, vendorStoreId);

        Store store = storeRepository.findById(vendorStoreId)
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        PromotionalOffer offer = buildOffer(request, products, store, vendorUserId);
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

        OfferProducts products = resolveOfferProducts(request, offer);
        assertProductsBelongToStore(products, vendorStoreId);

        applyRequestToOffer(offer, request, products, offer.getStore());
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

    /** Paged list of currently active & valid offers (public customer listing). */
    public Page<OfferResponse> getPublicActiveOffers(Pageable pageable) {
        return offerRepository.findAllActiveAndValid(LocalDateTime.now(), pageable)
                .map(this::toResponse);
    }

    /** Paged list of active & valid offers for a specific store (public). */
    public Page<OfferResponse> getPublicActiveOffersForStore(Long storeId, Pageable pageable) {
        return offerRepository.findActiveOffersForProductsInStore(storeId, LocalDateTime.now(), pageable)
                .map(this::toResponse);
    }

    /** One currently active & valid offer with its product lists (public — the "choose your reward" screen). */
    public OfferResponse getPublicOfferById(Long offerId) {
        PromotionalOffer offer = findById(offerId);
        if (!isLive(offer, LocalDateTime.now())) {
            throw new ResourceNotFoundException("العرض الترويجي غير متاح حالياً");
        }
        return toResponse(offer);
    }

    // =================================================================================
    // PRODUCT CLEANUP  (called before a product is deleted)
    // =================================================================================

    /**
     * Takes a product out of every offer. An offer left without buy or reward options is deleted;
     * one that lost its main product moves to its next buy option.
     */
    @Transactional
    public void removeProductFromOffers(Long productId) {
        for (PromotionalOffer offer : offerRepository.findAllUsingProduct(productId)) {
            List<Product> buy = without(buyProductsOf(offer), productId);
            List<Product> reward = without(rewardProductsOf(offer), productId);
            if (buy.isEmpty() || reward.isEmpty()) {
                offerRepository.delete(offer);
                continue;
            }
            offer.setProduct(buy.get(0));
            offer.setBuyProducts(new ArrayList<>(buy));
            offer.setRewardProducts(new ArrayList<>(reward));
            offer.setUpdatedAt(LocalDateTime.now());
            offerRepository.save(offer);
        }
    }

    // =================================================================================
    // OFFER APPLICATION ENGINE  (called by OrderCalculationService for checkout & previews)
    // =================================================================================

    /**
     * Applies the best active offers to the cart, writing {@code appliedOfferId},
     * {@code offerDiscountAmount}, {@code storeOfferDiscountAmount} and {@code offerFundedByStore}
     * on the rewarded lines. Lines must already carry their SYP {@code unitPrice} and {@code quantity}.
     */
    public void applyOffers(List<OrderItem> items) {
        for (OrderItem item : items) {
            item.setAppliedOfferId(null);
            item.setOfferDiscountAmount(0.0);
            item.setStoreOfferDiscountAmount(0.0);
            item.setOfferFundedByStore(false);
        }

        for (Plan plan : allocate(items).applied()) {
            boolean fundedByStore = isFundedByStore(plan.offer());
            plan.rewards().forEach((line, units) -> {
                double discount = lineDiscount(plan.offer(), line, units);
                line.item.setAppliedOfferId(plan.offer().getOfferId());
                line.item.setOfferDiscountAmount(discount);
                line.item.setStoreOfferDiscountAmount(storeShare(line.item, discount));
                line.item.setOfferFundedByStore(fundedByStore);
            });
        }
    }

    /**
     * Rewards the cart has earned but the customer hasn't added yet, so the app can ask them to
     * choose: the unused paid units of each offer's buy options, up to its uses left in this order.
     */
    public List<AvailableRewardResponse> findAvailableRewards(List<OrderItem> items) {
        Allocation allocation = allocate(items);
        List<AvailableRewardResponse> rewards = new ArrayList<>();

        for (PromotionalOffer offer : allocation.offers()) {
            int buyQty = quantityOf(offer.getBuyQuantity());
            int getQty = quantityOf(offer.getGetQuantity());
            if (buyQty < 1 || getQty < 1) {
                continue;
            }
            Set<Long> buyIds = productIds(buyProductsOf(offer));
            int paidUnits = allocation.lines().stream()
                    .filter(line -> line.chosenRewardOf == null && buyIds.contains(line.productId))
                    .mapToInt(line -> line.available)
                    .sum();
            int uses = Math.min(paidUnits / buyQty, usesLeft(offer, allocation.usesOf(offer)));
            if (uses > 0) {
                rewards.add(new AvailableRewardResponse(
                        offer.getOfferId(),
                        offer.getTitle(),
                        buildSummary(offer),
                        uses * getQty,
                        new ArrayList<>(productIds(rewardProductsOf(offer)))));
            }
        }
        return rewards;
    }

    /** One cart line while offers are allocated. */
    private static final class CartLine {
        final OrderItem item;
        final Long productId;
        final double unitPrice;
        /** The offer the customer picked this line as a reward for, when that pick is valid. */
        PromotionalOffer chosenRewardOf;
        /** Units not used by any offer yet. */
        int available;
        /** A line is discounted by one offer only. */
        boolean rewarded;

        CartLine(OrderItem item) {
            this.item = item;
            this.productId = item.getProduct().getProductId();
            this.unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0.0;
            this.available = item.getQuantity() != null ? item.getQuantity() : 0;
        }
    }

    /** What one offer does to the cart: units used as payment, units rewarded, and the saving. */
    private record Plan(PromotionalOffer offer, int uses, Map<CartLine, Integer> paid,
                        Map<CartLine, Integer> rewards, double discount) {
    }

    private record Allocation(List<CartLine> lines, List<PromotionalOffer> offers, List<Plan> applied) {
        int usesOf(PromotionalOffer offer) {
            return applied.stream()
                    .filter(plan -> Objects.equals(plan.offer().getOfferId(), offer.getOfferId()))
                    .mapToInt(Plan::uses)
                    .sum();
        }
    }

    /** Best-first allocation of the active offers to the cart. Reads the lines, never changes them. */
    private Allocation allocate(List<OrderItem> items) {
        List<CartLine> lines = items.stream().map(CartLine::new).toList();
        if (lines.isEmpty()) {
            return new Allocation(lines, List.of(), List.of());
        }

        Set<Long> cartProductIds = lines.stream().map(line -> line.productId).collect(Collectors.toSet());
        List<PromotionalOffer> offers =
                offerRepository.findActiveOffersForProducts(cartProductIds, LocalDateTime.now());

        // A pick only counts for a live offer that has this product among its reward options
        Map<Long, PromotionalOffer> offersById = offers.stream()
                .collect(Collectors.toMap(PromotionalOffer::getOfferId, Function.identity(), (a, b) -> a));
        for (CartLine line : lines) {
            PromotionalOffer offer = offersById.get(line.item.getRewardOfferId());
            if (offer != null && productIds(rewardProductsOf(offer)).contains(line.productId)) {
                line.chosenRewardOf = offer;
            }
        }

        // Offers come latest-first, so a tie keeps the latest offer
        List<PromotionalOffer> remaining = new ArrayList<>(offers);
        List<Plan> applied = new ArrayList<>();
        while (true) {
            Plan best = null;
            for (PromotionalOffer offer : remaining) {
                Plan plan = plan(offer, lines);
                if (plan != null && (best == null || plan.discount() > best.discount())) {
                    best = plan;
                }
            }
            if (best == null) {
                break;
            }
            commit(best);
            applied.add(best);
            remaining.remove(best.offer());
        }
        return new Allocation(lines, offers, applied);
    }

    /** What {@code offer} would do with the units still available, or null when it saves nothing. */
    private Plan plan(PromotionalOffer offer, List<CartLine> lines) {
        int buyQty = quantityOf(offer.getBuyQuantity());
        int getQty = quantityOf(offer.getGetQuantity());
        if (buyQty < 1 || getQty < 1) {
            return null;
        }
        int maxUses = usesLeft(offer, 0);
        Set<Long> buyIds = productIds(buyProductsOf(offer));
        Set<Long> rewardIds = productIds(rewardProductsOf(offer));

        // Lines picked as anyone's reward are never used as payment
        List<CartLine> paidPool = lines.stream()
                .filter(line -> line.available > 0 && line.chosenRewardOf == null
                        && buyIds.contains(line.productId))
                .toList();
        List<CartLine> chosen = lines.stream()
                .filter(line -> line.available > 0 && !line.rewarded && line.chosenRewardOf != null
                        && Objects.equals(line.chosenRewardOf.getOfferId(), offer.getOfferId()))
                .toList();

        return chosen.isEmpty()
                ? automaticPlan(offer, buyQty, getQty, maxUses, paidPool, rewardIds, lines)
                : chosenRewardsPlan(offer, buyQty, getQty, maxUses, paidPool, chosen);
    }

    /** The customer picked their rewards: reward as many picks as the paid units earn, dearest first. */
    private Plan chosenRewardsPlan(PromotionalOffer offer, int buyQty, int getQty, int maxUses,
                                   List<CartLine> paidPool, List<CartLine> chosen) {
        int paidUnits = paidPool.stream().mapToInt(line -> line.available).sum();
        int chosenUnits = chosen.stream().mapToInt(line -> line.available).sum();
        int uses = Math.min(Math.min(paidUnits / buyQty, ceilDiv(chosenUnits, getQty)), maxUses);
        if (uses == 0) {
            return null;
        }

        Map<CartLine, Integer> rewards = take(sorted(chosen, false), Math.min(chosenUnits, uses * getQty), Map.of());
        Map<CartLine, Integer> paid = take(sorted(paidPool, false), uses * buyQty, Map.of());
        return toPlan(offer, uses, paid, rewards);
    }

    /**
     * No picks: reward the cheapest reward-option units. A product in both lists can pay or be the
     * reward, so it's only used as a reward while enough units are left to pay for the deal.
     */
    private Plan automaticPlan(PromotionalOffer offer, int buyQty, int getQty, int maxUses,
                               List<CartLine> paidPool, Set<Long> rewardIds, List<CartLine> lines) {
        List<CartLine> rewardPool = lines.stream()
                .filter(line -> line.available > 0 && !line.rewarded && line.chosenRewardOf == null
                        && rewardIds.contains(line.productId))
                .toList();

        int eitherUnits = 0;
        for (CartLine line : rewardPool) {
            if (paidPool.contains(line)) eitherUnits += line.available;
        }
        int paidOnlyUnits = paidPool.stream().mapToInt(line -> line.available).sum() - eitherUnits;
        int rewardOnlyUnits = rewardPool.stream().mapToInt(line -> line.available).sum() - eitherUnits;

        int uses = Math.min(Math.min((rewardOnlyUnits + eitherUnits) / getQty, (paidOnlyUnits + eitherUnits) / buyQty),
                Math.min((paidOnlyUnits + rewardOnlyUnits + eitherUnits) / (buyQty + getQty), maxUses));
        if (uses == 0) {
            return null;
        }

        int[] eitherForRewards = {eitherUnits - Math.max(0, uses * buyQty - paidOnlyUnits)};
        Map<CartLine, Integer> rewards = new LinkedHashMap<>();
        int needed = uses * getQty;
        for (CartLine line : sorted(rewardPool, true)) {
            if (needed == 0) break;
            boolean either = paidPool.contains(line);
            int units = Math.min(line.available, needed);
            if (either) units = Math.min(units, eitherForRewards[0]);
            if (units <= 0) continue;
            rewards.put(line, units);
            needed -= units;
            if (either) eitherForRewards[0] -= units;
        }

        Map<CartLine, Integer> paid = take(sorted(paidPool, false), uses * buyQty, rewards);
        return toPlan(offer, uses, paid, rewards);
    }

    private Plan toPlan(PromotionalOffer offer, int uses, Map<CartLine, Integer> paid, Map<CartLine, Integer> rewards) {
        double discount = rewards.entrySet().stream()
                .mapToDouble(entry -> lineDiscount(offer, entry.getKey(), entry.getValue()))
                .sum();
        return discount > 0 ? new Plan(offer, uses, paid, rewards, discount) : null;
    }

    private void commit(Plan plan) {
        plan.paid().forEach((line, units) -> line.available -= units);
        plan.rewards().forEach((line, units) -> {
            line.available -= units;
            line.rewarded = true;
        });
    }

    /** Takes {@code count} units from the lines in order, skipping units already {@code taken}. */
    private Map<CartLine, Integer> take(List<CartLine> lines, int count, Map<CartLine, Integer> taken) {
        Map<CartLine, Integer> result = new LinkedHashMap<>();
        for (CartLine line : lines) {
            if (count == 0) break;
            int units = Math.min(line.available - taken.getOrDefault(line, 0), count);
            if (units <= 0) continue;
            result.put(line, units);
            count -= units;
        }
        return result;
    }

    private List<CartLine> sorted(List<CartLine> lines, boolean cheapestFirst) {
        Comparator<CartLine> byPrice = Comparator.comparingDouble(line -> line.unitPrice);
        return lines.stream().sorted(cheapestFirst ? byPrice : byPrice.reversed()).toList();
    }

    private double lineDiscount(PromotionalOffer offer, CartLine line, int units) {
        return mathUtil.roundMoney(units * computeDiscountPerUnit(offer, line.unitPrice));
    }

    private int usesLeft(PromotionalOffer offer, int usedSoFar) {
        Integer max = offer.getMaxUsesPerOrder();
        return max != null ? Math.max(max - usedSoFar, 0) : Integer.MAX_VALUE;
    }

    private static int quantityOf(Integer quantity) {
        return quantity != null ? quantity : 0;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
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

    /** The offer's buy and reward options — they exist and all come from one store. */
    private record OfferProducts(List<Product> buy, List<Product> reward) {
        Product main() {
            return buy.get(0);
        }
    }

    private OfferProducts resolveOfferProducts(OfferRequest req, PromotionalOffer existing) {
        List<Long> buyIds = distinctIds(req.getBuyProductIds());
        List<Long> rewardIds = distinctIds(req.getRewardProductIds());

        if (buyIds.isEmpty()) {
            if (!rewardIds.isEmpty()) {
                throw new InvalidDataException("يجب اختيار المنتجات المطلوب شراؤها في العرض");
            }
            // Older dashboards only know one product: keep the lists while it's still the main product
            if (existing != null && (req.getProductId() == null || isMainProduct(existing, req.getProductId()))) {
                return new OfferProducts(buyProductsOf(existing), rewardProductsOf(existing));
            }
            if (req.getProductId() == null) {
                throw new InvalidDataException("يجب اختيار منتجات العرض");
            }
            Product product = resolveProduct(req.getProductId());
            return new OfferProducts(List.of(product), List.of(product));
        }

        List<Product> buy = findProducts(buyIds);
        List<Product> reward = rewardIds.isEmpty() ? buy : findProducts(rewardIds);
        assertSameStore(buy, reward);
        return new OfferProducts(buy, reward);
    }

    private PromotionalOffer buildOffer(OfferRequest req, OfferProducts products, Store store, Long userId) {
        PromotionalOffer offer = new PromotionalOffer();
        applyRequestToOffer(offer, req, products, store);
        offer.setCreatedBy(userId);
        offer.setCreatedAt(LocalDateTime.now());
        offer.setUpdatedAt(LocalDateTime.now());
        return offer;
    }

    private void applyRequestToOffer(PromotionalOffer offer, OfferRequest req, OfferProducts products, Store store) {
        validateRequest(req);
        offer.setTitle(req.getTitle());
        offer.setDescription(req.getDescription());
        offer.setOfferType(req.getOfferType());
        offer.setBuyQuantity(req.getBuyQuantity());
        offer.setGetQuantity(req.getGetQuantity());
        offer.setDiscountPercent(req.getDiscountPercent());
        offer.setDiscountValue(req.getDiscountValue());
        offer.setProduct(products.main());
        offer.setBuyProducts(new ArrayList<>(products.buy()));
        offer.setRewardProducts(new ArrayList<>(products.reward()));
        offer.setMaxUsesPerOrder(req.getMaxUsesPerOrder());
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
        dto.setBuyProducts(buyProductsOf(offer).stream().map(this::toOfferProductResponse).toList());
        dto.setRewardProducts(rewardProductsOf(offer).stream().map(this::toOfferProductResponse).toList());
        dto.setMaxUsesPerOrder(offer.getMaxUsesPerOrder());

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

    /** The full product card (price, variants, colors) so the app can add it to the cart; the offer already names the store. */
    private ProductResponse toOfferProductResponse(Product product) {
        ProductResponse dto = catalogMapper.toProductResponse(product);
        dto.setStore(null);
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

    // ── Product lists ────────────────────────────────────────────────────────

    /** Offers saved before product lists existed use their single product for both lists. */
    private List<Product> buyProductsOf(PromotionalOffer offer) {
        List<Product> buy = offer.getBuyProducts();
        return buy != null && !buy.isEmpty() ? buy : singleProduct(offer);
    }

    private List<Product> rewardProductsOf(PromotionalOffer offer) {
        List<Product> reward = offer.getRewardProducts();
        return reward != null && !reward.isEmpty() ? reward : singleProduct(offer);
    }

    private List<Product> singleProduct(PromotionalOffer offer) {
        return offer.getProduct() != null ? List.of(offer.getProduct()) : List.of();
    }

    private static Set<Long> productIds(List<Product> products) {
        return products.stream().map(Product::getProductId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Product> without(List<Product> products, Long productId) {
        return products.stream().filter(p -> !Objects.equals(p.getProductId(), productId)).toList();
    }

    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private boolean isMainProduct(PromotionalOffer offer, Long productId) {
        return offer.getProduct() != null && Objects.equals(offer.getProduct().getProductId(), productId);
    }

    private boolean isLive(PromotionalOffer offer, LocalDateTime now) {
        return Boolean.TRUE.equals(offer.getIsActive())
                && (offer.getStartDate() == null || !offer.getStartDate().isAfter(now))
                && (offer.getEndDate() == null || !offer.getEndDate().isBefore(now));
    }

    private PromotionalOffer findById(Long offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("العرض الترويجي غير موجود برقم: " + offerId));
    }

    private Product resolveProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود برقم: " + productId));
    }

    /** The products in the order given; any unknown id is an error. */
    private List<Product> findProducts(List<Long> ids) {
        Map<Long, Product> byId = new HashMap<>();
        productRepository.findAllById(ids).forEach(product -> byId.put(product.getProductId(), product));
        List<Product> products = new ArrayList<>();
        for (Long id : ids) {
            Product product = byId.get(id);
            if (product == null) {
                throw new ResourceNotFoundException("المنتج غير موجود برقم: " + id);
            }
            products.add(product);
        }
        return products;
    }

    private void assertSameStore(List<Product> buy, List<Product> reward) {
        Long storeId = storeIdOf(buy.get(0));
        boolean sameStore = Stream.concat(buy.stream(), reward.stream())
                .allMatch(product -> Objects.equals(storeIdOf(product), storeId));
        if (!sameStore) {
            throw new InvalidDataException("يجب أن تكون جميع منتجات العرض من نفس المتجر");
        }
    }

    private static Long storeIdOf(Product product) {
        return product.getStore() != null ? product.getStore().getStoreId() : null;
    }

    private void assertProductsBelongToStore(OfferProducts products, Long storeId) {
        Stream.concat(products.buy().stream(), products.reward().stream())
                .forEach(product -> assertProductBelongsToStore(product, storeId));
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
