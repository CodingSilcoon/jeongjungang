package com.jeongjungang.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StationPairKeyTest {

    @Test
    void buildsRedisKeyFromStationPair() {
        assertThat(new StationPairKey("222", "333").toRedisKey()).isEqualTo("travel:222:333");
    }

    @Test
    void reversedPairProducesDifferentKey() {
        assertThat(new StationPairKey("222", "333").toRedisKey())
                .isNotEqualTo(new StationPairKey("333", "222").toRedisKey());
    }

    @Test
    void rejectsBlankStationId() {
        assertThatThrownBy(() -> new StationPairKey(" ", "333"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
