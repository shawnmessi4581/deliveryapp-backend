package com.deliveryapp.service;

import com.deliveryapp.dto.home.HomeSectionAdminResponse;
import com.deliveryapp.dto.home.HomeSectionRequest;
import com.deliveryapp.entity.HomeSection;
import com.deliveryapp.enums.HomeSectionType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.HomeSectionRepository;
import com.deliveryapp.util.UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The admin's settings for the customer home screen: which sections show, in what order, with what
 * title and icon. The home itself is built by {@link HomeService}.
 */
@Service
@RequiredArgsConstructor
public class HomeSectionService {

    /** Items a section with "show all" shows on the home until the admin picks another number. */
    public static final int DEFAULT_ITEM_LIMIT = 10;
    public static final int MAX_ITEM_LIMIT = 50;

    private static final int MAX_TITLE_LENGTH = 150;
    private static final long MAX_ICON_BYTES = 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
    private static final String ICON_FOLDER = "home-sections";

    private final HomeSectionRepository homeSectionRepository;
    private final FileStorageService fileStorageService;
    private final UrlUtil urlUtil;

    // =================================================================================
    // STARTUP
    // =================================================================================

    /**
     * Creates the sections that don't exist yet, after the existing ones, in {@link HomeSectionType} order.
     * They start on, except the under-price section, which needs a price first.
     */
    @Transactional
    public void createMissingSections() {
        List<HomeSection> existing = homeSectionRepository.findAll();
        Set<HomeSectionType> existingTypes = existing.stream()
                .map(HomeSection::getType)
                .collect(Collectors.toSet());
        int nextOrder = existing.stream()
                .mapToInt(section -> section.getDisplayOrder() != null ? section.getDisplayOrder() : 0)
                .max().orElse(0) + 1;

        List<HomeSection> created = new ArrayList<>();
        for (HomeSectionType type : HomeSectionType.values()) {
            if (existingTypes.contains(type)) {
                continue;
            }
            HomeSection section = new HomeSection();
            section.setType(type);
            section.setTitle(type.getDefaultTitle());
            section.setDisplayOrder(nextOrder++);
            section.setIsActive(type != HomeSectionType.UNDER_PRICE_PRODUCTS);
            section.setItemLimit(type.hasShowAll() ? DEFAULT_ITEM_LIMIT : null);
            section.setUpdatedAt(LocalDateTime.now());
            created.add(section);
        }
        if (!created.isEmpty()) {
            homeSectionRepository.saveAll(created);
        }
    }

    // =================================================================================
    // ADMIN
    // =================================================================================

    /** Every section, in home order — including the ones that are off. */
    public List<HomeSectionAdminResponse> getAllSections() {
        return homeSectionRepository.findAllByOrderByDisplayOrderAscHomeSectionIdAsc().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /** Partial edit: fields left out keep their value. A new icon replaces the old one. */
    @Transactional
    public HomeSectionAdminResponse updateSection(Long sectionId, HomeSectionRequest request, MultipartFile icon) {
        HomeSection section = findById(sectionId);
        HomeSectionType type = section.getType();

        if (request.getTitle() != null) {
            section.setTitle(validTitle(request.getTitle()));
        }
        if (request.getDisplayOrder() != null) {
            section.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getItemLimit() != null && type.hasShowAll()) {
            section.setItemLimit(validItemLimit(request.getItemLimit()));
        }
        if (request.getMaxPrice() != null && type == HomeSectionType.UNDER_PRICE_PRODUCTS) {
            section.setMaxPrice(validMaxPrice(request.getMaxPrice()));
        }
        if (request.getIsActive() != null) {
            section.setIsActive(request.getIsActive());
        }
        assertCanBeShown(section);

        // Everything is valid before any file is touched, so a rejected edit leaves no stray upload
        boolean newIcon = icon != null && !icon.isEmpty();
        if (newIcon) {
            assertPng(icon);
        }
        String oldIcon = section.getIcon();
        if (newIcon) {
            section.setIcon(fileStorageService.storeFile(icon, ICON_FOLDER));
        } else if (Boolean.TRUE.equals(request.getRemoveIcon())) {
            section.setIcon(null);
        }

        section.setUpdatedAt(LocalDateTime.now());
        // Saved before the old file goes, so a failed save never leaves the section pointing at a deleted icon
        HomeSection saved = homeSectionRepository.saveAndFlush(section);
        if (oldIcon != null && !oldIcon.equals(saved.getIcon())) {
            fileStorageService.deleteFile(oldIcon);
        }
        return toAdminResponse(saved);
    }

    /** Turns a section on or off. */
    @Transactional
    public HomeSectionAdminResponse toggleStatus(Long sectionId) {
        HomeSection section = findById(sectionId);
        section.setIsActive(!Boolean.TRUE.equals(section.getIsActive()));
        assertCanBeShown(section);
        section.setUpdatedAt(LocalDateTime.now());
        return toAdminResponse(homeSectionRepository.save(section));
    }

    /** Drag-and-drop reorder: every section id once, in the new order; positions become 1, 2, 3, ... */
    @Transactional
    public List<HomeSectionAdminResponse> reorderSections(List<Long> sectionIds) {
        List<HomeSection> sections = homeSectionRepository.findAll();
        Map<Long, HomeSection> byId = sections.stream()
                .collect(Collectors.toMap(HomeSection::getHomeSectionId, Function.identity()));
        // Same size and same ids → every section exactly once
        if (sectionIds == null || sectionIds.size() != byId.size()
                || !new HashSet<>(sectionIds).equals(byId.keySet())) {
            throw new InvalidDataException("يجب إرسال جميع الأقسام مرة واحدة بالترتيب الجديد");
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < sectionIds.size(); i++) {
            HomeSection section = byId.get(sectionIds.get(i));
            section.setDisplayOrder(i + 1);
            section.setUpdatedAt(now);
        }
        homeSectionRepository.saveAll(sections);
        return getAllSections();
    }

    // =================================================================================
    // PRIVATE HELPERS
    // =================================================================================

    private HomeSection findById(Long sectionId) {
        return homeSectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("القسم غير موجود برقم: " + sectionId));
    }

    private String validTitle(String title) {
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidDataException("عنوان القسم مطلوب");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidDataException("عنوان القسم يجب ألا يزيد عن " + MAX_TITLE_LENGTH + " حرفاً");
        }
        return trimmed;
    }

    private int validItemLimit(int itemLimit) {
        if (itemLimit < 1 || itemLimit > MAX_ITEM_LIMIT) {
            throw new InvalidDataException("عدد العناصر يجب أن يكون بين 1 و " + MAX_ITEM_LIMIT);
        }
        return itemLimit;
    }

    private double validMaxPrice(double maxPrice) {
        if (!Double.isFinite(maxPrice) || maxPrice <= 0) {
            throw new InvalidDataException("السعر الأعلى يجب أن يكون أكبر من صفر");
        }
        return maxPrice;
    }

    /** The under-price section can't show before it has a price. */
    private void assertCanBeShown(HomeSection section) {
        if (Boolean.TRUE.equals(section.getIsActive())
                && section.getType() == HomeSectionType.UNDER_PRICE_PRODUCTS
                && section.getMaxPrice() == null) {
            throw new InvalidDataException("حدد السعر الأعلى لهذا القسم قبل تفعيله");
        }
    }

    /** A real PNG: the name says .png and the file starts with the PNG signature. */
    private void assertPng(MultipartFile icon) {
        String name = icon.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".png") || !hasPngSignature(icon)) {
            throw new InvalidDataException("يُسمح برفع أيقونة بصيغة PNG فقط");
        }
        if (icon.getSize() > MAX_ICON_BYTES) {
            throw new InvalidDataException("حجم الأيقونة يجب ألا يزيد عن 1 ميغابايت");
        }
    }

    private boolean hasPngSignature(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return Arrays.equals(in.readNBytes(PNG_SIGNATURE.length), PNG_SIGNATURE);
        } catch (IOException e) {
            return false;
        }
    }

    private HomeSectionAdminResponse toAdminResponse(HomeSection section) {
        HomeSectionAdminResponse dto = new HomeSectionAdminResponse();
        dto.setSectionId(section.getHomeSectionId());
        dto.setType(section.getType());
        dto.setTitle(section.getTitle());
        dto.setIconUrl(urlUtil.getFullUrl(section.getIcon()));
        dto.setDisplayOrder(section.getDisplayOrder());
        dto.setIsActive(section.getIsActive());
        dto.setShowAll(section.getType().hasShowAll());
        dto.setItemLimit(section.getItemLimit());
        dto.setMaxPrice(section.getMaxPrice());
        dto.setUpdatedAt(section.getUpdatedAt());
        return dto;
    }
}
