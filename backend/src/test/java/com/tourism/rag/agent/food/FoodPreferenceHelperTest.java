package com.tourism.rag.agent.food;

import com.tourism.rag.dto.agent.FoodRecommendation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoodPreferenceHelperTest {

    @Test
    void buildXhsQueries_includesTasteAndDietary() {
        List<String> queries = FoodPreferenceHelper.buildXhsQueries(
                "吉隆坡", List.of("夜市"), List.of("清真"));
        assertTrue(queries.stream().anyMatch(q -> q.contains("夜市")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("清真")));
    }

    @Test
    void applyDietaryFilter_removesSeafoodWhenAllergic() {
        List<FoodRecommendation> foods = List.of(
                FoodRecommendation.builder().name("海鲜大排档").category("海鲜").build(),
                FoodRecommendation.builder().name("椰浆饭").category("马来菜").build()
        );
        List<FoodRecommendation> filtered = FoodPreferenceHelper.applyDietaryFilter(
                foods, List.of("海鲜过敏"));
        assertEquals(1, filtered.size());
        assertEquals("椰浆饭", filtered.get(0).getName());
    }
}
