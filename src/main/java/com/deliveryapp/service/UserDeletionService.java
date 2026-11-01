package com.deliveryapp.service;

import com.deliveryapp.entity.User;
import com.deliveryapp.enums.OrderStatus;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.CouponRepository;
import com.deliveryapp.repository.CouponUsageRepository;
import com.deliveryapp.repository.FavoriteRepository;
import com.deliveryapp.repository.FlashSaleRepository;
import com.deliveryapp.repository.NotificationRepository;
import com.deliveryapp.repository.OrderRepository;
import com.deliveryapp.repository.OrderStatusHistoryRepository;
import com.deliveryapp.repository.OtpVerificationRepository;
import com.deliveryapp.repository.PromotionalOfferRepository;
import com.deliveryapp.repository.RefreshTokenRepository;
import com.deliveryapp.repository.UserAddressRepository;
import com.deliveryapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes user accounts — the one place for both the admin delete and the customer's "delete my account".
 *
 * <ul>
 *   <li>Refused while the user has an order in progress (as customer or as driver).</li>
 *   <li>Personal data and everything only this user needs is always removed: addresses, favorites,
 *       notifications, OTP codes, sessions, profile image.</li>
 *   <li>No history → the row is deleted completely.</li>
 *   <li>History (orders, order status changes, coupon use, offers / coupons / flash sales they created)
 *       → the row stays as an anonymous "deleted user" with every personal field wiped, so past orders,
 *       store payouts, offer ownership and profit reports stay correct. The phone number is freed, so
 *       the person can register again as a brand-new account.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    static final List<OrderStatus> IN_PROGRESS = List.of(
            OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);

    static final String DELETED_USER_NAME = "مستخدم محذوف";

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final CouponRepository couponRepository;
    private final FlashSaleRepository flashSaleRepository;
    private final PromotionalOfferRepository offerRepository;
    private final UserAddressRepository addressRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final OtpVerificationRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final FileStorageService fileStorageService;

    /**
     * @return true when the account was deleted completely, false when an anonymous record was kept
     *         for past orders
     */
    @Transactional
    public boolean deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("المستخدم غير موجود برقم: " + userId));

        if (orderRepository.existsByUserUserIdAndStatusIn(userId, IN_PROGRESS)
                || orderRepository.existsByDriverUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new InvalidDataException("لا يمكن حذف الحساب لوجود طلبات قيد التنفيذ. يجب تسليمها أو إلغاؤها أولاً.");
        }

        removePersonalData(user);

        if (hasHistory(userId)) {
            anonymize(user);
            userRepository.save(user);
            log.info("User {} deleted — anonymous record kept for past orders", userId);
            return false;
        }

        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
        log.info("User {} deleted completely", userId);
        return true;
    }

    private void removePersonalData(User user) {
        Long userId = user.getUserId();
        addressRepository.deleteByUserUserId(userId);
        favoriteRepository.deleteByUserUserId(userId);
        notificationRepository.deleteByUserUserId(userId);
        otpRepository.deleteByUserId(userId);
        if (user.getPhoneNumber() != null) {
            otpRepository.deleteByPhoneNumber(user.getPhoneNumber());
        }
        // No session can be refreshed any more
        tokenService.revokeAllTokensForUser(userId);
        if (user.getProfileImage() != null) {
            fileStorageService.deleteFile(user.getProfileImage());
        }
    }

    /** Anything that still points to the user and must keep working after the account is gone. */
    private boolean hasHistory(Long userId) {
        return orderRepository.existsByUserUserId(userId)
                || orderRepository.existsByDriverUserId(userId)
                || statusHistoryRepository.existsByChangedByUserId(userId)
                || couponUsageRepository.existsByUserId(userId)
                || offerRepository.existsByCreatedBy(userId)
                || flashSaleRepository.existsByCreatedBy(userId)
                || couponRepository.existsByCreatedBy(userId);
    }

    /** Wipes every personal field. The user type stays, so offers they created keep their owner type. */
    private void anonymize(User user) {
        user.setName(DELETED_USER_NAME);
        user.setPhoneNumber("DELETED_" + user.getUserId()); // frees the real number for a new registration
        user.setEmail(null);
        user.setPassword("");                               // matches no password, so no login
        user.setProfileImage(null);
        user.setAddress(null);
        user.setLatitude(null);
        user.setLongitude(null);
        user.setVehicleType(null);
        user.setVehicleNumber(null);
        user.setIsAvailable(false);
        user.setCurrentLocationLat(null);
        user.setCurrentLocationLng(null);
        user.setFcmToken(null);
        user.setManagedStore(null);
        user.setIsActive(false);
        user.setDeletedAt(LocalDateTime.now());
    }
}
