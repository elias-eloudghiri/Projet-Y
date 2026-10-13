package com.example.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.user.PseudoGenerator;
import com.example.back.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GuestIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;

    @Autowired private UserRepository userRepository;

    @Autowired private JwtDecoder jwtDecoder;

    @Autowired private MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    // --- Token generation ---

    @Test
    void guestGetsSingleTwentyFourHourTokenWithoutAnyDatabaseRow() throws Exception {
        long usersBefore = userRepository.count();

        MvcResult result =
                guest().andExpect(status().isOk())
                        .andExpect(jsonPath("$.role").value("GUEST"))
                        .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
                        .andExpect(cookie().secure(AuthCookies.ACCESS, true))
                        .andExpect(cookie().sameSite(AuthCookies.ACCESS, "Strict"))
                        .andExpect(cookie().path(AuthCookies.ACCESS, "/"))
                        .andExpect(cookie().maxAge(AuthCookies.ACCESS, 24 * 3600))
                        // No refresh token for guests: the cookie is only cleared.
                        .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0))
                        .andReturn();

        String pseudo = JsonPath.read(result.getResponse().getContentAsString(), "$.pseudo");
        assertThat(pseudo).matches(PseudoGenerator.FORMAT);

        Jwt jwt = jwtDecoder.decode(accessCookie(result).getValue());
        assertThat(UUID.fromString(jwt.getSubject())).isNotNull();
        assertThat(jwt.getClaimAsString(AccessTokenService.CLAIM_PSEUDO)).isEqualTo(pseudo);
        assertThat(jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE)).isEqualTo("GUEST");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofHours(24));

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.existsByPseudo(pseudo)).isFalse();
    }

    @Test
    void eachNewGuestGetsItsOwnIdentity() throws Exception {
        Jwt first = jwtDecoder.decode(accessCookie(guest().andReturn()).getValue());
        Jwt second = jwtDecoder.decode(accessCookie(guest().andReturn()).getValue());

        assertThat(first.getSubject()).isNotEqualTo(second.getSubject());
    }

    @Test
    void meExposesGuestRole() throws Exception {
        MvcResult result = guest().andReturn();
        String pseudo = JsonPath.read(result.getResponse().getContentAsString(), "$.pseudo");

        mvc.perform(get("/api/auth/me").cookie(accessCookie(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pseudo").value(pseudo))
                .andExpect(jsonPath("$.role").value("GUEST"));
    }

    // --- Coming back ---

    @Test
    void returningGuestWithValidCookieKeepsSameIdentityAndToken() throws Exception {
        MvcResult first = guest().andReturn();
        String pseudo = JsonPath.read(first.getResponse().getContentAsString(), "$.pseudo");
        Cookie access = accessCookie(first);

        clock.advance(Duration.ofHours(23));

        guest(access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pseudo").value(pseudo))
                .andExpect(jsonPath("$.role").value("GUEST"))
                // Same token kept: its lifetime is not extended.
                .andExpect(cookie().doesNotExist(AuthCookies.ACCESS));
    }

    @Test
    void guestTokenExpiresAfterTwentyFourHours() throws Exception {
        MvcResult first = guest().andReturn();
        Cookie access = accessCookie(first);
        String firstSubject = jwtDecoder.decode(access.getValue()).getSubject();

        // Beyond the 60 s clock skew tolerated by the JWT validator.
        clock.advance(Duration.ofHours(24).plusMinutes(2));

        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
        MvcResult second = guest(access).andExpect(status().isOk()).andReturn();
        assertThat(subjectOf(second)).isNotEqualTo(firstSubject);
    }

    @Test
    void invalidOrAccountCookieGivesNewGuest() throws Exception {
        guest(new Cookie(AuthCookies.ACCESS, "garbage"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthCookies.ACCESS));

        MvcResult account = register();
        guest(accessCookie(account))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("GUEST"))
                .andExpect(cookie().exists(AuthCookies.ACCESS));
    }

    @Test
    void becomingGuestClosesPreviousAccountSession() throws Exception {
        MvcResult account = register();
        Cookie refresh = account.getResponse().getCookie(AuthCookies.REFRESH);

        guest(refresh).andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
    }

    // --- Role checks ---

    @Test
    void guestCannotCreateRoom() throws Exception {
        Cookie access = accessCookie(guest().andReturn());

        mvc.perform(
                        post("/api/rooms")
                                .cookie(access)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountIsNotDeniedRoomCreation() throws Exception {
        Cookie access = accessCookie(register());

        int status =
                mvc.perform(
                                post("/api/rooms")
                                        .cookie(access)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void guestCannotAccessAdminRoutes() throws Exception {
        Cookie access = accessCookie(guest().andReturn());

        mvc.perform(get("/api/admin/anything").cookie(access)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/stories").cookie(access)).andExpect(status().isForbidden());
    }

    @Test
    void guestCannotUseRefreshFlow() throws Exception {
        MvcResult result = guest().andReturn();

        mvc.perform(post("/api/auth/refresh").cookie(accessCookie(result)))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private ResultActions guest(Cookie... cookies) throws Exception {
        var request = post("/api/auth/guest");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc.perform(request);
    }

    private MvcResult register() throws Exception {
        return mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static Cookie accessCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(AuthCookies.ACCESS);
        assertThat(cookie).isNotNull();
        return new Cookie(AuthCookies.ACCESS, cookie.getValue());
    }

    private String subjectOf(MvcResult result) {
        return jwtDecoder.decode(accessCookie(result).getValue()).getSubject();
    }
}
