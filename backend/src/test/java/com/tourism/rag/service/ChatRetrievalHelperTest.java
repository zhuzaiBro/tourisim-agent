package com.tourism.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRetrievalHelperTest {

    @Test
    void detectsCrossCityComparisonKeywords() {
        assertThat(ChatRetrievalHelper.isCrossCityComparison("青岛和北京哪个更适合亲子游")).isTrue();
        assertThat(ChatRetrievalHelper.isCrossCityComparison("对比两地美食")).isTrue();
        assertThat(ChatRetrievalHelper.isCrossCityComparison("青岛有什么好吃的")).isFalse();
    }

    @Test
    void normalizesCategory() {
        assertThat(ChatRetrievalHelper.normalizeCategory(" Food ")).isEqualTo("food");
        assertThat(ChatRetrievalHelper.normalizeCategory(null)).isNull();
        assertThat(ChatRetrievalHelper.normalizeCategory("")).isNull();
    }
}
