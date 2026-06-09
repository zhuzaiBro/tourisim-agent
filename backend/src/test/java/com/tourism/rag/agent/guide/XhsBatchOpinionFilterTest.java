package com.tourism.rag.agent.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XhsBatchOpinionFilterTest {

    @Test
    void formatOpinionReason_prefersExplicitReason() {
        XhsOpinionVerdict v = XhsOpinionVerdict.builder()
                .reason("夜景出片，但周末人多需错峰")
                .positives(List.of("免费"))
                .negatives(List.of("人多"))
                .build();
        assertEquals("夜景出片，但周末人多需错峰", XhsBatchOpinionFilter.formatOpinionReason(v));
    }

    @Test
    void formatOpinionReason_buildsFromPositivesAndNegatives() {
        XhsOpinionVerdict v = XhsOpinionVerdict.builder()
                .positives(List.of("地道肉骨茶", "性价比高"))
                .negatives(List.of("排队久"))
                .build();
        String reason = XhsBatchOpinionFilter.formatOpinionReason(v);
        assertTrue(reason.contains("亮点"));
        assertTrue(reason.contains("注意"));
        assertTrue(reason.contains("排队久"));
    }
}
