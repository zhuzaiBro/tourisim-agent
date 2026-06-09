package com.tourism.rag.agent.rag;

import com.tourism.rag.dto.agent.PoiInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPoiParserTest {

    private final RagPoiParser parser = new RagPoiParser();

    @Test
    void parseSegment_extractsTicketAndHours() {
        String text = """
                ### 栈桥
                青岛标志性景点。
                - **门票**：免费（回澜阁内部约10元）
                - **开放时间**：全天（回澜阁 8:30-17:30）
                - **地址**：青岛市市南区太平路22号
                """;

        PoiInfo poi = parser.parseSegment(text, "qingdao");

        assertThat(poi.getName()).isEqualTo("栈桥");
        assertThat(poi.getTicketPrice()).contains("免费");
        assertThat(poi.getOpeningHours()).contains("全天");
        assertThat(poi.getAddress()).contains("太平路");
        assertThat(poi.getDataSource()).isEqualTo("rag");
    }

    @Test
    void enrich_fillsGaodePlaceholdersFromRag() {
        PoiInfo gaode = PoiInfo.builder()
                .name("崂山风景区")
                .openingHours("请提前确认")
                .ticketPrice("请咨询景区")
                .dataSource("gaode_api")
                .build();
        PoiInfo rag = PoiInfo.builder()
                .name("崂山")
                .openingHours("7:00-17:00")
                .ticketPrice("165元")
                .description("海上名山第一")
                .dataSource("rag")
                .build();

        parser.enrich(gaode, rag);

        assertThat(gaode.getOpeningHours()).isEqualTo("7:00-17:00");
        assertThat(gaode.getTicketPrice()).isEqualTo("165元");
        assertThat(gaode.getDescription()).contains("海上名山");
    }
}
