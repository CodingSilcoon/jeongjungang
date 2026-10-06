package com.jeongjungang.ui.recommend;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.jeongjungang.domain.model.LatLng;
import com.jeongjungang.domain.recommend.Criterion;
import com.jeongjungang.domain.recommend.Participant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** 백그라운드 계산은 LiveData가 필요해서 여기서는 입력 검증만 본다. */
public class RecommendViewModelTest {

    private static List<Participant> people(int n) {
        List<Participant> list = new ArrayList<Participant>();
        for (int i = 0; i < n; i++) {
            list.add(new Participant("p" + i, new LatLng(37.5 + i * 0.01, 127.0)));
        }
        return list;
    }

    @Test
    public void acceptsTwoToMax() {
        assertNull(RecommendViewModel.validate(people(2), Criterion.TOTAL_TIME));
        assertNull(RecommendViewModel.validate(people(RecommendViewModel.MAX_PARTICIPANTS), Criterion.MAX_TIME));
    }

    @Test
    public void rejectsTooFewTooManyOrNoCriterion() {
        assertNotNull(RecommendViewModel.validate(Collections.<Participant>emptyList(), Criterion.TOTAL_TIME));
        assertNotNull(RecommendViewModel.validate(people(1), Criterion.TOTAL_TIME));
        assertNotNull(RecommendViewModel.validate(people(RecommendViewModel.MAX_PARTICIPANTS + 1), Criterion.TOTAL_TIME));
        assertNotNull(RecommendViewModel.validate(people(2), null));
    }
}
