package com.example.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.user.Role;
import com.example.back.user.User;
import com.example.back.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired private MockMvc mvc;

    @Autowired private UserRepository userRepository;

    @Autowired private MutableClock clock;

    private record Tokens(String pseudo, String access, String refresh) {}

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    // --- Registration ---

    @Test
    void registerGeneratesPseudoHashesPasswordAndOpensSession() throws Exception {
        MvcResult result =
                register(PASSWORD)
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.pseudo")
                                        .value(
                                                org.hamcrest.Matchers.matchesPattern(
                                                        "^[a-z]+-[0-9]{6}$")))
                        .andExpect(jsonPath("$.role").value("USER"))
                        .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
                        .andExpect(cookie().secure(AuthCookies.ACCESS, true))
                        .andExpect(cookie().sameSite(AuthCookies.ACCESS, "Strict"))
                        .andExpect(cookie().maxAge(AuthCookies.ACCESS, 15 * 60))
                        .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true))
                        .andExpect(cookie().path(AuthCookies.REFRESH, "/api/auth"))
                        .andExpect(cookie().maxAge(AuthCookies.REFRESH, 7 * 24 * 3600))
                        .andReturn();

        String pseudo = JsonPath.read(result.getResponse().getContentAsString(), "$.pseudo");
        User user = userRepository.findByPseudo(pseudo).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getPasswordHash()).startsWith("$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void registerRejectsTooShortPassword() throws Exception {
        register("short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void registerIgnoresAnyPseudoSentByClient() throws Exception {
        mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"password\":\""
                                                + PASSWORD
                                                + "\",\"pseudo\":\"lerat-000001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pseudo").value(org.hamcrest.Matchers.not("lerat-000001")));
    }

    // --- Login ---

    @Test
    void loginIsCaseInsensitiveAndIgnoresSurroundingSpaces() throws Exception {
        String pseudo = registerAndGetTokens().pseudo();

        login("  " + pseudo.toUpperCase() + " ", PASSWORD, "10.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pseudo").value(pseudo))
                .andExpect(cookie().exists(AuthCookies.ACCESS))
                .andExpect(cookie().exists(AuthCookies.REFRESH));
        assertThat(userRepository.findByPseudo(pseudo).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginWithWrongPasswordOrUnknownPseudoGivesSameError() throws Exception {
        String pseudo = registerAndGetTokens().pseudo();

        login(pseudo, "wrong-password", "10.0.0.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("inconnu-999999", PASSWORD, "10.0.0.2")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginIsBlockedFor15MinutesAfterFiveFailures() throws Exception {
        String pseudo = registerAndGetTokens().pseudo();
        String ip = "10.0.0.3";
        for (int i = 0; i < 5; i++) {
            login(pseudo, "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        login(pseudo, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        login(pseudo, PASSWORD, "10.0.0.4").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(15));
        login(pseudo, PASSWORD, ip).andExpect(status().isOk());
    }

    // --- Access token ---

    @Test
    void meRequiresValidAccessCookie() throws Exception {
        Tokens tokens = registerAndGetTokens();

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, "not-a-jwt")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.ACCESS, tokens.access())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pseudo").value(tokens.pseudo()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void publicAuthEndpointsIgnoreInvalidAccessCookie() throws Exception {
        String pseudo = registerAndGetTokens().pseudo();

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .cookie(new Cookie(AuthCookies.ACCESS, "expired-or-garbage"))
                                .content(loginJson(pseudo, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void adminRouteIsForbiddenToUsers() throws Exception {
        Tokens tokens = registerAndGetTokens();

        mvc.perform(
                        get("/api/admin/anything")
                                .cookie(new Cookie(AuthCookies.ACCESS, tokens.access())))
                .andExpect(status().isForbidden());
    }

    // --- Refresh rotation ---

    @Test
    void refreshRotatesToken() throws Exception {
        Tokens tokens = registerAndGetTokens();

        MvcResult result =
                refresh(tokens.refresh())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.pseudo").value(tokens.pseudo()))
                        .andExpect(cookie().exists(AuthCookies.ACCESS))
                        .andReturn();
        String newRefresh = result.getResponse().getCookie(AuthCookies.REFRESH).getValue();

        assertThat(newRefresh).isNotEqualTo(tokens.refresh());
        refresh(newRefresh).andExpect(status().isOk());
    }

    @Test
    void reusingRotatedTokenAfterGraceRevokesWholeSession() throws Exception {
        Tokens tokens = registerAndGetTokens();
        String rotated = refreshAndGetNewToken(tokens.refresh());

        clock.advance(Duration.ofSeconds(11));
        refresh(tokens.refresh()).andExpect(status().isUnauthorized());

        refresh(rotated).andExpect(status().isUnauthorized());
    }

    @Test
    void reusingRotatedTokenWithinGraceDoesNotRevokeSession() throws Exception {
        Tokens tokens = registerAndGetTokens();
        String rotated = refreshAndGetNewToken(tokens.refresh());

        refresh(tokens.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(AuthCookies.REFRESH));

        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void refreshTokenExpiresAfterSevenDays() throws Exception {
        Tokens tokens = registerAndGetTokens();

        clock.advance(Duration.ofDays(7));

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutOrWithUnknownTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        refresh("unknown-token").andExpect(status().isUnauthorized());
    }

    // --- Logout ---

    @Test
    void logoutInvalidatesRefreshTokenAndClearsCookies() throws Exception {
        Tokens tokens = registerAndGetTokens();

        mvc.perform(
                        post("/api/auth/logout")
                                .cookie(new Cookie(AuthCookies.REFRESH, tokens.refresh())))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));

        refresh(tokens.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutOnlyClosesItsOwnSession() throws Exception {
        Tokens first = registerAndGetTokens();
        MvcResult second = login(first.pseudo(), PASSWORD, "10.0.0.5").andReturn();
        String secondRefresh = second.getResponse().getCookie(AuthCookies.REFRESH).getValue();

        mvc.perform(
                        post("/api/auth/logout")
                                .cookie(new Cookie(AuthCookies.REFRESH, first.refresh())))
                .andExpect(status().isNoContent());

        refresh(secondRefresh).andExpect(status().isOk());
    }

    // --- Admin bootstrap ---

    @Test
    void adminIsCreatedAtStartupFromConfiguration() throws Exception {
        User admin = userRepository.findByPseudo("admin-000000").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);

        login("ADMIN-000000", "admin-test-password", "10.0.0.6")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // --- Helpers ---

    private ResultActions register(String password) throws Exception {
        return mvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String pseudo, String password, String ip) throws Exception {
        return mvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(pseudo, password))
                        .with(
                                request -> {
                                    request.setRemoteAddr(ip);
                                    return request;
                                }));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(
                post("/api/auth/refresh").cookie(new Cookie(AuthCookies.REFRESH, refreshToken)));
    }

    private Tokens registerAndGetTokens() throws Exception {
        MvcResult result = register(PASSWORD).andExpect(status().isCreated()).andReturn();
        return new Tokens(
                JsonPath.read(result.getResponse().getContentAsString(), "$.pseudo"),
                result.getResponse().getCookie(AuthCookies.ACCESS).getValue(),
                result.getResponse().getCookie(AuthCookies.REFRESH).getValue());
    }

    private String refreshAndGetNewToken(String refreshToken) throws Exception {
        MvcResult result = refresh(refreshToken).andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(AuthCookies.REFRESH).getValue();
    }

    private static String loginJson(String pseudo, String password) {
        return "{\"pseudo\":\"" + pseudo + "\",\"password\":\"" + password + "\"}";
    }
}
