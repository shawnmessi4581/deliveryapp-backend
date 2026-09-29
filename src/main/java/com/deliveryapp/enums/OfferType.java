package com.deliveryapp.enums;

/**
 * Defines the type of promotional offer.
 *
 * <p>Each type drives the engine in {@link com.deliveryapp.service.PromotionalOfferService}
 * that decides how to discount the qualifying item(s).
 *
 * <ul>
 *   <li>{@link #BUY_X_GET_Y_FREE}         – Buy X units, get Y units free (100 % off).</li>
 *   <li>{@link #BUY_X_GET_Y_PERCENT_OFF}  – Buy X units, get Y units at {@code discountPercent} % off.</li>
 *   <li>{@link #BUY_X_GET_Y_FIXED_OFF}    – Buy X units, get Y units at a fixed {@code discountValue} (SYP) off.</li>
 * </ul>
 *
 * All types operate on the <em>same product</em> (e.g. "Buy 1 Get 1 Free" on Product A).
 * Multi-product cross-sells are out of scope for this version.
 */
public enum OfferType {

    /**
     * Buy {@code buyQuantity} units → the next {@code getQuantity} units are FREE.
     * Example: Buy 2 Get 1 Free → discountPercent is ignored; the cheapest unit price is zeroed out.
     */
    BUY_X_GET_Y_FREE,

    /**
     * Buy {@code buyQuantity} units → the next {@code getQuantity} units are
     * discounted by {@code discountPercent} %.
     * Example: Buy 1 Get 1 at 50 % off.
     */
    BUY_X_GET_Y_PERCENT_OFF,

    /**
     * Buy {@code buyQuantity} units → the next {@code getQuantity} units receive a
     * fixed {@code discountValue} (SYP) reduction per unit.
     * Example: Buy 2 Get 1 with 5 000 SYP off the third unit.
     */
    BUY_X_GET_Y_FIXED_OFF
}
