package com.deliveryapp.config;

import org.springframework.beans.propertyeditors.CustomBooleanEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Global data binding fix for multipart/form-data requests.
 *
 * <p>Problem: Spring's default Boolean binding via {@code @ModelAttribute} treats
 * an absent or empty form field as {@code null}. When a Flutter (or any HTTP client)
 * sends {@code isAvailable=false} in a multipart form, some binders silently
 * produce {@code null} instead of {@code Boolean.FALSE}, causing the service's
 * null-check to skip the update entirely.
 *
 * <p>Fix: Register a {@link CustomBooleanEditor} with {@code allowEmpty = true}
 * (preserving {@code null} semantics for fields that are genuinely absent) and
 * explicit true/false string mappings so that <em>any</em> well-formed boolean
 * string ("true", "false", "1", "0", "yes", "no") is always converted correctly.
 */
@ControllerAdvice
public class GlobalBindingAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // allowEmpty = true  → absent fields remain null (partial-update friendly)
        // The editor handles: "true"/"false", "True"/"False", "1"/"0", "yes"/"no"
        CustomBooleanEditor booleanEditor = new CustomBooleanEditor(true);
        binder.registerCustomEditor(Boolean.class, booleanEditor);
    }
}
