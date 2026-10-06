package com.jeongjungang.meeting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InviteCodesTest {

    @Test
    void random_usesOnlyUnambiguousCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(InviteCodes.random()).hasSize(8).doesNotContainPattern("[ILOU01]");
        }
    }

    @Test
    void normalize_acceptsLowercaseSpacesAndHyphens() {
        assertThat(InviteCodes.normalize("7k3q-h9mx")).contains("7K3QH9MX");
        assertThat(InviteCodes.normalize(" 7K3Q H9MX ")).contains("7K3QH9MX");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "7K3QH9M", "7K3QH9MXX", "7K3QH9M0", "OOOOOOOO", "7K3Q/H9M"})
    void normalize_rejectsWrongLengthOrAmbiguousCharacters(String raw) {
        assertThat(InviteCodes.normalize(raw)).isEmpty();
    }
}
