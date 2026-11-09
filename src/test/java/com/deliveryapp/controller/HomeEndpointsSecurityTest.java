package com.deliveryapp.controller;

import com.deliveryapp.config.SecurityConfig;
import com.deliveryapp.controller.admin.AdminHomeSectionController;
import com.deliveryapp.dto.home.HomeResponse;
import com.deliveryapp.dto.home.HomeSectionAdminResponse;
import com.deliveryapp.service.HomeSectionService;
import com.deliveryapp.service.HomeService;
import com.deliveryapp.util.KeyUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ HomeController.class, AdminHomeSectionController.class })
@Import({ SecurityConfig.class, HomeEndpointsSecurityTest.TestKeys.class })
class HomeEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtEncoder jwtEncoder;

    @MockitoBean private HomeService homeService;
    @MockitoBean private HomeSectionService homeSectionService;
    @MockitoBean private UserDetailsService userDetailsService;

    @TestConfiguration
    static class TestKeys {
        @Bean
        KeyUtils keyUtils() throws NoSuchAlgorithmException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new KeyUtils() {
                @Override
                public KeyPair getRsaKeyPair() {
                    return keyPair;
                }
            };
        }
    }

    // ── public home ──────────────────────────────────────────────────────────

    @Test
    void home_isOpenToGuests() throws Exception {
        when(homeService.getHome(null, null)).thenReturn(new HomeResponse(List.of()));

        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections").isArray());
    }

    @Test
    void home_passesTheCustomersLocation() throws Exception {
        when(homeService.getHome(33.5, 36.3)).thenReturn(new HomeResponse(List.of()));

        mockMvc.perform(get("/api/home").param("userLat", "33.5").param("userLng", "36.3")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_CUSTOMER")))
                .andExpect(status().isOk());

        verify(homeService).getHome(33.5, 36.3);
    }

    // ── admin layout ─────────────────────────────────────────────────────────

    @Test
    void adminLayout_withoutLogin_is401() throws Exception {
        mockMvc.perform(get("/api/admin/home-sections"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(homeSectionService);
    }

    @Test
    void adminLayout_isAdminOnly() throws Exception {
        String customer = "Bearer " + token("ROLE_CUSTOMER");

        mockMvc.perform(get("/api/admin/home-sections").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/home-sections/1/status").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
        verifyNoInteractions(homeSectionService);
    }

    @Test
    void adminLayout_listsTheSectionsForAdmins() throws Exception {
        when(homeSectionService.getAllSections()).thenReturn(List.of(new HomeSectionAdminResponse()));

        mockMvc.perform(get("/api/admin/home-sections").header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void editSection_bindsTheMultipartForm() throws Exception {
        MockMultipartFile icon = new MockMultipartFile("icon", "cart.png", "image/png", new byte[] { 1 });
        when(homeSectionService.updateSection(eq(4L), any(), any(MultipartFile.class)))
                .thenReturn(new HomeSectionAdminResponse());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/admin/home-sections/4")
                        .file(icon)
                        .param("title", "الأكثر مبيعاً")
                        .param("displayOrder", "3")
                        .param("isActive", "false")
                        .param("itemLimit", "12")
                        .param("maxPrice", "15000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isOk());

        verify(homeSectionService).updateSection(eq(4L), argThat(request ->
                "الأكثر مبيعاً".equals(request.getTitle())
                        && request.getDisplayOrder() == 3
                        && Boolean.FALSE.equals(request.getIsActive())
                        && request.getItemLimit() == 12
                        && request.getMaxPrice() == 15000.0
                        && request.getRemoveIcon() == null),
                argThat(file -> "cart.png".equals(file.getOriginalFilename())));
    }

    @Test
    void reorder_takesTheIdsInTheirNewOrder() throws Exception {
        when(homeSectionService.reorderSections(List.of(3L, 1L, 2L))).thenReturn(List.of());

        mockMvc.perform(put("/api/admin/home-sections/display-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionIds\":[3,1,2]}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ROLE_ADMIN")))
                .andExpect(status().isOk());

        verify(homeSectionService).reorderSections(List.of(3L, 1L, 2L));
    }

    /** A valid access token with the given role, as TokenService puts it in the "scope" claim. */
    private String token(String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("allin-shops")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject("0999000111")
                .claim("scope", role)
                .claim("userId", 7L)
                .claim("sid", "session-1")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
