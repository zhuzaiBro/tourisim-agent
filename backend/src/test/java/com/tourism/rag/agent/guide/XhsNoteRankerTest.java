package com.tourism.rag.agent.guide;

import com.tourism.rag.dto.agent.XiaohongshuNote;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XhsNoteRankerTest {

    private final XhsNoteRanker ranker = new XhsNoteRanker();

    @Test
    void rank_prefersMatchingDayCount() {
        List<XiaohongshuNote> notes = List.of(
                note("新加坡7天深度游", "一周攻略", 100, 50),
                note("新加坡3天2夜攻略", "经典路线", 200, 100),
                note("新加坡美食推荐", "好吃", 500, 300)
        );

        List<XiaohongshuNote> ranked = ranker.rankAndFilter(notes, 3, 3);

        assertThat(ranked.get(0).getTitle()).contains("3天");
    }

    private static XiaohongshuNote note(String title, String desc, int likes, int collects) {
        return XiaohongshuNote.builder()
                .title(title).description(desc).likes(likes).collects(collects).build();
    }
}
