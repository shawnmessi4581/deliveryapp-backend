package com.deliveryapp.service;

import com.deliveryapp.dto.catalog.ProductRequest;
import com.deliveryapp.entity.*;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final ColorRepository colorRepository;
    private final StoreCategoryRepository storeCategoryRepository;
    private final FileStorageService fileStorageService;

    // ── Strip sort from pageable (ID queries are scalar — can't sort by entity
    // fields) ──
    private Pageable withoutSort(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    // ── Resolve IDs → full entities, preserving ID-query order ──
    private Page<Product> fetchPage(Page<Long> idPage) {
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), idPage.getPageable(), idPage.getTotalElements());
        }
        List<Product> products = productRepository.findByProductIdIn(idPage.getContent());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        List<Product> ordered = idPage.getContent().stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageImpl<>(ordered, idPage.getPageable(), idPage.getTotalElements());
    }

    // ── Resolve IDs → full entities, then sort in-memory by displayOrder ──
    private Page<Product> fetchPageSortedByDisplayOrder(Page<Long> idPage) {
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), idPage.getPageable(), idPage.getTotalElements());
        }
        List<Product> products = productRepository.findByProductIdIn(idPage.getContent());
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));
        List<Product> ordered = idPage.getContent().stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(p -> (p.getDisplayOrder() == null ? 0 : p.getDisplayOrder())))
                .collect(Collectors.toList());
        return new PageImpl<>(ordered, idPage.getPageable(), idPage.getTotalElements());
    }

    // ================= PUBLIC / CATALOG (Paginated) =================

    public Page<Product> getAllProductsRandomly(Pageable pageable) {
        return fetchPageSortedByDisplayOrder(productRepository.findAllActiveProductIds(withoutSort(pageable)));
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("المنتج غير موجود برقم: " + id));
    }

    public Page<Product> getProductsByStore(Long storeId, Pageable pageable) {
        if (!storeRepository.existsById(storeId))
            throw new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId);
        return fetchPageSortedByDisplayOrder(
                productRepository.findIdsByStoreStoreIdAndIsAvailableTrue(storeId, withoutSort(pageable)));
    }

    public Page<Product> getProductsByStoreAndStoreCategory(Long storeId, Long storeCategoryId, Pageable pageable) {
        if (!storeRepository.existsById(storeId))
            throw new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId);
        if (!storeCategoryRepository.existsById(storeCategoryId))
            throw new ResourceNotFoundException("الفئة غير موجودة برقم: " + storeCategoryId);
        return fetchPageSortedByDisplayOrder(productRepository
                .findIdsByStoreStoreIdAndStoreCategoryStoreCategoryIdAndIsAvailableTrue(
                        storeId, storeCategoryId, withoutSort(pageable)));
    }

    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        if (!categoryRepository.existsById(categoryId))
            throw new ResourceNotFoundException("الفئة غير موجودة برقم: " + categoryId);
        return fetchPageSortedByDisplayOrder(
                productRepository.findIdsByCategoryCategoryIdAndIsAvailableTrue(categoryId, withoutSort(pageable)));
    }

    public Page<Product> getProductsBySubCategory(Long subCategoryId, Pageable pageable) {
        if (!subCategoryRepository.existsById(subCategoryId))
            throw new ResourceNotFoundException("الفئة الفرعية غير موجودة برقم: " + subCategoryId);
        return fetchPageSortedByDisplayOrder(
                productRepository.findIdsBySubCategorySubcategoryIdAndIsAvailableTrue(subCategoryId,
                        withoutSort(pageable)));
    }

    public Page<Product> searchProducts(String keyword, Long categoryId, Pageable pageable) {
        if (keyword == null)
            keyword = "";
        if (categoryId == null || categoryId == 0) {
            return fetchPage(productRepository
                    .findIdsByNameContainingIgnoreCaseAndIsAvailableTrue(keyword, pageable));
        } else {
            return fetchPage(productRepository
                    .findIdsByCategoryCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(
                            categoryId, keyword, pageable));
        }
    }

    public Page<Product> searchProductsInStore(String keyword, Long storeId, Pageable pageable) {
        if (!storeRepository.existsById(storeId))
            throw new ResourceNotFoundException("المتجر غير موجود برقم: " + storeId);
        if (keyword == null)
            keyword = "";
        return fetchPage(productRepository
                .findIdsByStoreStoreIdAndNameContainingIgnoreCaseAndIsAvailableTrue(storeId, keyword, pageable));
    }

    public Page<Product> getProductsByStoreAndCategory(Long storeId, Long categoryId, Pageable pageable) {
        return fetchPageSortedByDisplayOrder(productRepository
                .findIdsByStoreStoreIdAndCategoryCategoryIdAndIsAvailableTrue(storeId, categoryId,
                        withoutSort(pageable)));
    }

    public Page<Product> getProductsByStoreAndSubCategory(Long storeId, Long subCategoryId, Pageable pageable) {
        return fetchPageSortedByDisplayOrder(productRepository
                .findIdsByStoreStoreIdAndSubCategorySubcategoryIdAndIsAvailableTrue(
                        storeId, subCategoryId, withoutSort(pageable)));
    }

    public Page<Product> getProductsUnderPrice(Double price, Pageable pageable) {
        return fetchPageSortedByDisplayOrder(
                productRepository.findIdsByBasePriceLessThanEqualAndIsAvailableTrue(price, withoutSort(pageable)));
    }

    public Page<Product> getNewestProducts(Pageable pageable) {
        return fetchPage(productRepository.findIdsByIsAvailableTrueOrderByProductIdDesc(pageable));
    }

    public Page<Product> getTrendingProducts(Pageable pageable) {
        return fetchPage(productRepository.findIdsByIsTrendingTrueAndIsAvailableTrue(pageable));
    }

    public Page<Product> getOffers(Pageable pageable) {
        return fetchPageSortedByDisplayOrder(
                productRepository.findIdsByHasOfferTrueAndIsAvailableTrue(withoutSort(pageable)));
    }

    // ================= ADMIN CRUD =================

    public Page<Product> getAllProductsAdmin(Long storeId, Long categoryId, Long subCategoryId, Pageable pageable) {
        return fetchPage(productRepository.findAdminFilteredProductIds(storeId, categoryId, subCategoryId, pageable));
    }

    @Transactional
    public Product createProduct(ProductRequest request, MultipartFile mainImage, List<MultipartFile> galleryImages) {
        Store store = storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("المتجر غير موجود"));

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());

        if (request.getIsUsd() != null) {
            product.setIsUsd(request.getIsUsd());
            if (request.getIsUsd()) {
                product.setUsdPrice(request.getUsdPrice());
                product.setBasePrice(0.0);
            } else {
                product.setBasePrice(request.getBasePrice());
                product.setUsdPrice(0.0);
            }
        } else {
            product.setIsUsd(false);
            product.setBasePrice(request.getBasePrice() != null ? request.getBasePrice() : 0.0);
            product.setUsdPrice(0.0);
        }

        product.setIsAvailable(true);
        product.setStore(store);
        product.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId()).orElseThrow();
            product.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId()).orElseThrow();
            product.setSubCategory(sub);
        }

        if (mainImage != null && !mainImage.isEmpty()) {
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }

        if (request.getColorIds() != null && !request.getColorIds().isEmpty()) {
            product.setColors(colorRepository.findAllById(request.getColorIds()));
        }

        if (galleryImages != null && !galleryImages.isEmpty()) {
            List<String> imagePaths = new ArrayList<>();
            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    imagePaths.add(fileStorageService.storeFile(file, "products"));
                }
            }
            product.setImages(imagePaths);
        }

        product.setIsTrending(request.getIsTrending() != null ? request.getIsTrending() : false);

        if (request.getStoreCategoryId() != null) {
            StoreCategory storeCategory = storeCategoryRepository.findById(request.getStoreCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("تصنيف المتجر غير موجود"));
            if (!storeCategory.getStore().getStoreId().equals(product.getStore().getStoreId())) {
                throw new InvalidDataException("هذا التصنيف ينتمي لمتجر آخر!");
            }
            product.setStoreCategory(storeCategory);
        } else {
            product.setStoreCategory(null);
        }

        if (request.getHasOffer() != null) {
            product.setHasOffer(request.getHasOffer());
            if (request.getHasOffer()) {
                if (request.getIsUsd() != null && request.getIsUsd()) {
                    product.setOfferUsdPrice(request.getOfferUsdPrice());
                    product.setOfferBasePrice(0.0);
                } else {
                    product.setOfferBasePrice(request.getOfferBasePrice());
                    product.setOfferUsdPrice(0.0);
                }
            }
        } else {
            product.setHasOffer(false);
        }

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest request, MultipartFile mainImage,
            List<MultipartFile> galleryImages) {
        Product product = getProductById(id);

        if (request.getName() != null)
            product.setName(request.getName());
        if (request.getDescription() != null)
            product.setDescription(request.getDescription());

        if (request.getIsUsd() != null) {
            product.setIsUsd(request.getIsUsd());
            if (request.getIsUsd()) {
                if (request.getUsdPrice() != null)
                    product.setUsdPrice(request.getUsdPrice());
                product.setBasePrice(0.0);
            } else {
                if (request.getBasePrice() != null)
                    product.setBasePrice(request.getBasePrice());
                product.setUsdPrice(0.0);
            }
        }

        if (request.getIsAvailable() != null)
            product.setIsAvailable(request.getIsAvailable());
        if (request.getDisplayOrder() != null)
            product.setDisplayOrder(request.getDisplayOrder());
        if (request.getIsTrending() != null)
            product.setIsTrending(request.getIsTrending());

        if (request.getStoreId() != null) {
            Store store = storeRepository.findById(request.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
            product.setStore(store);
        }
        if (request.getCategoryId() != null) {
            Category cat = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("الفئة غير موجودة"));
            product.setCategory(cat);
        }
        if (request.getSubCategoryId() != null) {
            SubCategory sub = subCategoryRepository.findById(request.getSubCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("الفئة الفرعية غير موجودة"));
            product.setSubCategory(sub);
        }

        if (mainImage != null && !mainImage.isEmpty()) {
            if (product.getImage() != null) {
                fileStorageService.deleteFile(product.getImage());
            }
            product.setImage(fileStorageService.storeFile(mainImage, "products"));
        }

        if (request.getColorIds() != null) {
            product.setColors(colorRepository.findAllById(request.getColorIds()));
        }

        if (galleryImages != null && !galleryImages.isEmpty()) {
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                product.getImages().forEach(fileStorageService::deleteFile);
                product.getImages().clear();
            }
            for (MultipartFile file : galleryImages) {
                if (!file.isEmpty()) {
                    product.getImages().add(fileStorageService.storeFile(file, "products"));
                }
            }
        }

        if (request.getStoreCategoryId() != null) {
            StoreCategory storeCategory = storeCategoryRepository.findById(request.getStoreCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store Category not found"));
            if (!storeCategory.getStore().getStoreId().equals(product.getStore().getStoreId())) {
                throw new InvalidDataException("This category belongs to a different store!");
            }
            product.setStoreCategory(storeCategory);
        }

        if (request.getHasOffer() != null) {
            product.setHasOffer(request.getHasOffer());
            if (request.getHasOffer()) {
                if (request.getIsUsd() != null && request.getIsUsd()) {
                    product.setOfferUsdPrice(request.getOfferUsdPrice());
                    product.setOfferBasePrice(0.0);
                } else {
                    product.setOfferBasePrice(request.getOfferBasePrice());
                    product.setOfferUsdPrice(0.0);
                }
            }
        } else {
            product.setHasOffer(false);
        }

        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        if (product.getImage() != null) {
            fileStorageService.deleteFile(product.getImage());
        }
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            product.getImages().forEach(fileStorageService::deleteFile);
        }
        productRepository.deleteById(id);
    }

    public ProductVariant addProductVariant(Long productId, String name, Double priceAdjustment) {
        Product product = getProductById(productId);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setVariantValue(name);
        variant.setPriceAdjustment(priceAdjustment);
        variant.setIsAvailable(true);
        return variantRepository.save(variant);
    }

    public void deleteProductVariant(Long variantId) {
        if (!variantRepository.existsById(variantId))
            throw new ResourceNotFoundException("النوع غير موجود برقم: " + variantId);
        variantRepository.deleteById(variantId);
    }
}