package com.example.back.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.back.user.PseudoGenerator;
import com.example.back.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class GuestServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PseudoGenerator pseudoGenerator = mock(PseudoGenerator.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final GuestService service =
            new GuestService(
                    userRepository, pseudoGenerator, accessTokenService, mock(JwtDecoder.class));

    @Test
    void regeneratesPseudoWhenItBelongsToAnAccount() {
        when(pseudoGenerator.generate()).thenReturn("lerat-000001", "lerat-000002");
        when(userRepository.existsByPseudo("lerat-000001")).thenReturn(true);
        when(userRepository.existsByPseudo("lerat-000002")).thenReturn(false);
        when(accessTokenService.issueGuest(any(), anyString())).thenReturn("token");

        GuestService.GuestSession guest = service.enter(null);

        assertThat(guest.pseudo()).isEqualTo("lerat-000002");
        assertThat(guest.isNew()).isTrue();
        verify(accessTokenService).issueGuest(eq(guest.guestId()), eq("lerat-000002"));
        verify(accessTokenService, never()).issueGuest(any(), eq("lerat-000001"));
    }

    @Test
    void failsWhenNoFreePseudoIsFound() {
        when(pseudoGenerator.generate()).thenReturn("lerat-000001");
        when(userRepository.existsByPseudo("lerat-000001")).thenReturn(true);

        assertThatThrownBy(() -> service.enter(null)).isInstanceOf(IllegalStateException.class);
        verify(accessTokenService, never()).issueGuest(any(), anyString());
    }
}
