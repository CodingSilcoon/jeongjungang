package com.jeongjungang.ui.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.jeongjungang.data.remote.ReverseAddress;
import org.junit.Test;

/** 디바운스·LiveData는 메인 루퍼가 필요해서 여기서는 입력 정리와 검증, 표시 문구만 본다. */
public class AddressSearchViewModelTest {

    @Test
    public void normalizeTrimsAndCollapsesSpaces() {
        assertEquals("", AddressSearchViewModel.normalize(null));
        assertEquals("서울 마포구 와우산로", AddressSearchViewModel.normalize("  서울   마포구\t와우산로 "));
    }

    @Test
    public void validateLength() {
        assertNotNull(AddressSearchViewModel.validate(""));
        assertNotNull(AddressSearchViewModel.validate("홍"));
        assertNull(AddressSearchViewModel.validate("홍대"));
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            longQuery.append('가');
        }
        assertNotNull(AddressSearchViewModel.validate(longQuery.toString()));
    }

    @Test
    public void pinLabelPrefersRoadAddressAndFallsBack() {
        assertEquals("도로명", new ReverseAddress("지번", "도로명").displayText());
        assertEquals("지번", new ReverseAddress("지번", null).displayText());
        assertEquals("지번", new ReverseAddress("지번", "").displayText());
        assertNull(new ReverseAddress(null, null).displayText());
        assertEquals(PinState.DEFAULT_LABEL, PinState.resolved(null, null).label);
        assertEquals(PinState.Status.FALLBACK, PinState.resolved(null, null).status);
        assertEquals("도로명", PinState.resolved(null, "도로명").label);
    }
}
