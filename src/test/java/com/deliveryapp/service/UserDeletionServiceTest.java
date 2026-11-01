package com.deliveryapp.service;

import com.deliveryapp.entity.Store;
import com.deliveryapp.entity.User;
import com.deliveryapp.enums.UserType;
import com.deliveryapp.exception.InvalidDataException;
import com.deliveryapp.exception.ResourceNotFoundException;
import com.deliveryapp.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    private static final long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository statusHistoryRepository;
    @Mock private CouponUsageRepository couponUsageRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private FlashSaleRepository flashSaleRepository;
    @Mock private PromotionalOfferRepository offerRepository;
    @Mock private UserAddressRepository addressRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private OtpVerificationRepository otpRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenService tokenService;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks private UserDeletionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(USER_ID);
        user.setName("محمد");
        user.setPhoneNumber("0987654321");
        user.setEmail("m@example.com");
        user.setProfileImage("/uploads/users/a.jpg");
        user.setUserType(UserType.DRIVER);
        user.setVehicleNumber("123456");
        user.setFcmToken("fcm");
        user.setIsActive(true);
        user.setManagedStore(new Store());
    }

    @Test
    void orderInProgress_blocksTheDelete() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.existsByUserUserIdAndStatusIn(eq(USER_ID), anyList())).thenReturn(true);

        assertThrows(InvalidDataException.class, () -> service.deleteUser(USER_ID));
        verify(userRepository, never()).delete(any());
        verify(addressRepository, never()).deleteByUserUserId(any());
    }

    @Test
    void noHistory_removesTheUserCompletely() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertTrue(service.deleteUser(USER_ID));

        verify(addressRepository).deleteByUserUserId(USER_ID);
        verify(favoriteRepository).deleteByUserUserId(USER_ID);
        verify(notificationRepository).deleteByUserUserId(USER_ID);
        verify(otpRepository).deleteByUserId(USER_ID);
        verify(fileStorageService).deleteFile("/uploads/users/a.jpg");
        verify(refreshTokenRepository).deleteByUserId(USER_ID);
        verify(userRepository).delete(user);
    }

    @Test
    void pastOrders_keepAnAnonymousRecord_andFreeThePhoneNumber() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(orderRepository.existsByDriverUserId(USER_ID)).thenReturn(true);

        assertFalse(service.deleteUser(USER_ID));

        verify(userRepository, never()).delete(any());
        verify(userRepository).save(user);
        assertEquals("مستخدم محذوف", user.getName());
        assertEquals("DELETED_7", user.getPhoneNumber());
        assertNull(user.getEmail());
        assertNull(user.getProfileImage());
        assertNull(user.getVehicleNumber());
        assertNull(user.getFcmToken());
        assertNull(user.getManagedStore());
        assertEquals("", user.getPassword());
        assertFalse(user.getIsActive());
        assertNotNull(user.getDeletedAt());
        assertEquals(UserType.DRIVER, user.getUserType()); // kept for the orders it delivered
        verify(tokenService).revokeAllTokensForUser(USER_ID);
    }

    @Test
    void createdOffers_countAsHistory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(offerRepository.existsByCreatedBy(USER_ID)).thenReturn(true);

        assertFalse(service.deleteUser(USER_ID));
    }

    @Test
    void alreadyDeletedUser_isNotFound() {
        user.setDeletedAt(LocalDateTime.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(ResourceNotFoundException.class, () -> service.deleteUser(USER_ID));
    }
}
