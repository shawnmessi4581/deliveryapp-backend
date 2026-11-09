package com.deliveryapp.service;

import com.deliveryapp.dto.home.HomeSectionAdminResponse;
import com.deliveryapp.dto.home.HomeSectionRequest;
import com.deliveryapp.entity.HomeSection;
import com.deliveryapp.enums.HomeSectionType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.repository.HomeSectionRepository;
import com.deliveryapp.util.UrlUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.deliveryapp.enums.HomeSectionType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeSectionServiceTest {

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 };
    private static final byte[] JPEG_BYTES = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F' };

    @Mock private HomeSectionRepository homeSectionRepository;
    @Mock private FileStorageService fileStorageService;
    @Captor private ArgumentCaptor<List<HomeSection>> savedSections;

    private HomeSectionService service;

    @BeforeEach
    void setUp() {
        service = new HomeSectionService(homeSectionRepository, fileStorageService, new UrlUtil());
    }

    // ── startup ──────────────────────────────────────────────────────────────

    @Test
    void createMissingSections_onAnEmptyTable_createsEveryTypeInOrder() {
        when(homeSectionRepository.findAll()).thenReturn(List.of());

        service.createMissingSections();

        verify(homeSectionRepository).saveAll(savedSections.capture());
        List<HomeSection> created = savedSections.getValue();
        assertEquals(Arrays.asList(HomeSectionType.values()), created.stream().map(HomeSection::getType).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), created.stream().map(HomeSection::getDisplayOrder).toList());
        for (HomeSection section : created) {
            assertEquals(section.getType().getDefaultTitle(), section.getTitle());
            // The under-price section needs a price before it can show
            assertEquals(section.getType() != UNDER_PRICE_PRODUCTS, section.getIsActive());
            assertEquals(section.getType().hasShowAll() ? 10 : null, section.getItemLimit());
        }
    }

    @Test
    void createMissingSections_addsOnlyTheMissingTypes_afterTheLastPosition() {
        HomeSection banners = section(1, BANNERS, 3);
        HomeSection stores = section(2, STORES, 7);
        when(homeSectionRepository.findAll()).thenReturn(List.of(banners, stores));

        service.createMissingSections();

        verify(homeSectionRepository).saveAll(savedSections.capture());
        List<HomeSection> created = savedSections.getValue();
        List<HomeSectionType> missing = new ArrayList<>(Arrays.asList(HomeSectionType.values()));
        missing.removeAll(List.of(BANNERS, STORES));
        assertEquals(missing, created.stream().map(HomeSection::getType).toList());
        assertEquals(8, created.get(0).getDisplayOrder());
        assertEquals(15, created.get(created.size() - 1).getDisplayOrder());
    }

    @Test
    void createMissingSections_whenNothingIsMissing_savesNothing() {
        List<HomeSection> all = new ArrayList<>();
        for (HomeSectionType type : HomeSectionType.values()) {
            all.add(section(type.ordinal() + 1, type, type.ordinal() + 1));
        }
        when(homeSectionRepository.findAll()).thenReturn(all);

        service.createMissingSections();

        verify(homeSectionRepository, never()).saveAll(any());
    }

    // ── edit ─────────────────────────────────────────────────────────────────

    @Test
    void update_changesOnlyTheFieldsSent() {
        HomeSection stores = givenSection(section(1, STORES, 2));
        HomeSectionRequest request = new HomeSectionRequest();
        request.setTitle("  متاجر مميزة  ");

        HomeSectionAdminResponse response = service.updateSection(1L, request, null);

        assertEquals("متاجر مميزة", response.getTitle());
        assertEquals(2, response.getDisplayOrder());
        assertTrue(response.getIsActive());
        assertEquals(10, response.getItemLimit());
        assertTrue(response.getShowAll());
        assertNotNull(stores.getUpdatedAt());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void update_blankTitle_isRejected() {
        givenSectionForRejectedEdit(section(1, STORES, 1));
        HomeSectionRequest request = new HomeSectionRequest();
        request.setTitle("   ");

        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, request, null));
        verify(homeSectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_itemLimit_isCheckedForShowAllSections() {
        givenSectionForRejectedEdit(section(1, STORES, 1));
        HomeSectionRequest request = new HomeSectionRequest();
        request.setItemLimit(HomeSectionService.MAX_ITEM_LIMIT + 1);

        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, request, null));
        request.setItemLimit(0);
        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, request, null));
    }

    @Test
    void update_itemLimitAndMaxPrice_onlyApplyToTheirSections() {
        givenSection(section(1, CATEGORIES, 1));
        HomeSectionRequest request = new HomeSectionRequest();
        request.setItemLimit(20);
        request.setMaxPrice(5000.0);

        HomeSectionAdminResponse response = service.updateSection(1L, request, null);

        // Categories have no "show all" (they show everything) and no price
        assertNull(response.getItemLimit());
        assertNull(response.getMaxPrice());
        assertFalse(response.getShowAll());
    }

    @Test
    void update_underPrice_cantBeTurnedOnWithoutAPrice() {
        HomeSection underPrice = section(1, UNDER_PRICE_PRODUCTS, 9);
        underPrice.setIsActive(false);
        givenSectionForRejectedEdit(underPrice);
        HomeSectionRequest request = new HomeSectionRequest();
        request.setIsActive(true);

        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, request, null));
        verify(homeSectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_underPrice_turnsOnTogetherWithItsPrice() {
        HomeSection underPrice = section(1, UNDER_PRICE_PRODUCTS, 9);
        underPrice.setIsActive(false);
        givenSection(underPrice);
        HomeSectionRequest request = new HomeSectionRequest();
        request.setIsActive(true);
        request.setMaxPrice(10000.0);

        HomeSectionAdminResponse response = service.updateSection(1L, request, null);

        assertTrue(response.getIsActive());
        assertEquals(10000.0, response.getMaxPrice());
    }

    @Test
    void update_underPrice_priceMustBePositive() {
        givenSectionForRejectedEdit(section(1, UNDER_PRICE_PRODUCTS, 9));
        HomeSectionRequest request = new HomeSectionRequest();
        request.setMaxPrice(0.0);

        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, request, null));
    }

    // ── icon ─────────────────────────────────────────────────────────────────

    @Test
    void update_pngIcon_isStoredFirst_thenTheOldOneDeleted() {
        HomeSection stores = section(1, STORES, 1);
        stores.setIcon("/uploads/home-sections/old.png");
        givenSection(stores);
        MockMultipartFile icon = new MockMultipartFile("icon", "cart.PNG", "image/png", PNG_BYTES);
        when(fileStorageService.storeFile(icon, "home-sections")).thenReturn("/uploads/home-sections/new.png");

        HomeSectionAdminResponse response = service.updateSection(1L, new HomeSectionRequest(), icon);

        assertEquals("/uploads/home-sections/new.png", response.getIconUrl());
        InOrder order = inOrder(fileStorageService, homeSectionRepository);
        order.verify(fileStorageService).storeFile(icon, "home-sections");
        order.verify(homeSectionRepository).saveAndFlush(stores);
        order.verify(fileStorageService).deleteFile("/uploads/home-sections/old.png");
    }

    @Test
    void update_iconThatIsNotAPng_isRejected_andNothingIsStored() {
        givenSectionForRejectedEdit(section(1, STORES, 1));
        MockMultipartFile jpegNamedPng = new MockMultipartFile("icon", "photo.png", "image/png", JPEG_BYTES);
        MockMultipartFile pngNamedJpg = new MockMultipartFile("icon", "icon.jpg", "image/jpeg", PNG_BYTES);

        assertThrows(InvalidDataException.class,
                () -> service.updateSection(1L, new HomeSectionRequest(), jpegNamedPng));
        assertThrows(InvalidDataException.class,
                () -> service.updateSection(1L, new HomeSectionRequest(), pngNamedJpg));
        verifyNoInteractions(fileStorageService);
        verify(homeSectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_iconOver1Mb_isRejected() {
        givenSectionForRejectedEdit(section(1, STORES, 1));
        byte[] big = Arrays.copyOf(PNG_BYTES, 1024 * 1024 + 1);
        MockMultipartFile icon = new MockMultipartFile("icon", "big.png", "image/png", big);

        assertThrows(InvalidDataException.class, () -> service.updateSection(1L, new HomeSectionRequest(), icon));
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void update_removeIcon_clearsItAndDeletesTheFile() {
        HomeSection stores = section(1, STORES, 1);
        stores.setIcon("/uploads/home-sections/old.png");
        givenSection(stores);
        HomeSectionRequest request = new HomeSectionRequest();
        request.setRemoveIcon(true);

        HomeSectionAdminResponse response = service.updateSection(1L, request, null);

        assertNull(response.getIconUrl());
        verify(fileStorageService).deleteFile("/uploads/home-sections/old.png");
    }

    // ── on / off ─────────────────────────────────────────────────────────────

    @Test
    void toggle_turnsTheSectionOffAndOnAgain() {
        HomeSection stores = section(1, STORES, 1);
        when(homeSectionRepository.findById(1L)).thenReturn(Optional.of(stores));
        when(homeSectionRepository.save(stores)).thenReturn(stores);

        assertFalse(service.toggleStatus(1L).getIsActive());
        assertTrue(service.toggleStatus(1L).getIsActive());
    }

    @Test
    void toggle_underPriceWithoutAPrice_isRejected() {
        HomeSection underPrice = section(1, UNDER_PRICE_PRODUCTS, 9);
        underPrice.setIsActive(false);
        when(homeSectionRepository.findById(1L)).thenReturn(Optional.of(underPrice));

        assertThrows(InvalidDataException.class, () -> service.toggleStatus(1L));
        verify(homeSectionRepository, never()).save(any());
    }

    // ── reorder ──────────────────────────────────────────────────────────────

    @Test
    void reorder_givesEachSectionItsNewPosition() {
        HomeSection banners = section(1, BANNERS, 1);
        HomeSection stores = section(2, STORES, 2);
        HomeSection categories = section(3, CATEGORIES, 3);
        when(homeSectionRepository.findAll()).thenReturn(List.of(banners, stores, categories));
        when(homeSectionRepository.findAllByOrderByDisplayOrderAscHomeSectionIdAsc())
                .thenReturn(List.of(categories, banners, stores));

        List<HomeSectionAdminResponse> result = service.reorderSections(List.of(3L, 1L, 2L));

        assertEquals(1, categories.getDisplayOrder());
        assertEquals(2, banners.getDisplayOrder());
        assertEquals(3, stores.getDisplayOrder());
        assertEquals(List.of(3L, 1L, 2L), result.stream().map(HomeSectionAdminResponse::getSectionId).toList());
    }

    @Test
    void reorder_mustListEverySectionExactlyOnce() {
        when(homeSectionRepository.findAll())
                .thenReturn(List.of(section(1, BANNERS, 1), section(2, STORES, 2), section(3, CATEGORIES, 3)));

        for (List<Long> ids : Arrays.asList(List.of(1L, 2L), List.of(1L, 2L, 2L), List.of(1L, 2L, 3L, 4L), null)) {
            assertThrows(InvalidDataException.class, () -> service.reorderSections(ids), "ids " + ids);
        }
        verify(homeSectionRepository, never()).saveAll(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A section the edit will save (saveAndFlush hands it back). */
    private HomeSection givenSection(HomeSection section) {
        when(homeSectionRepository.findById(section.getHomeSectionId())).thenReturn(Optional.of(section));
        when(homeSectionRepository.saveAndFlush(section)).thenReturn(section);
        return section;
    }

    /** A section the edit is expected to reject, so nothing gets saved. */
    private void givenSectionForRejectedEdit(HomeSection section) {
        when(homeSectionRepository.findById(section.getHomeSectionId())).thenReturn(Optional.of(section));
    }

    private static HomeSection section(long id, HomeSectionType type, int displayOrder) {
        HomeSection section = new HomeSection();
        section.setHomeSectionId(id);
        section.setType(type);
        section.setTitle(type.getDefaultTitle());
        section.setDisplayOrder(displayOrder);
        section.setIsActive(true);
        section.setItemLimit(type.hasShowAll() ? 10 : null);
        return section;
    }
}
