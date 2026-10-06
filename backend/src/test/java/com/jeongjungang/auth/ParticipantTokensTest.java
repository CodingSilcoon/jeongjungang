package com.jeongjungang.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParticipantTokensTest {

    @Test
    void newToken_is43UrlSafeCharsAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = ParticipantTokens.newToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            seen.add(token);
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void hash_isStableSha256Hex() {
        assertThat(ParticipantTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ParticipantTokens.hash("abc")).isEqualTo(ParticipantTokens.hash("abc"));
    }
}
