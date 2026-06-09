package com.tourism.rag.agent.provider.xhs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.dto.agent.XiaohongshuNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XiaohongshuApiClientTest {

    private XiaohongshuApiClient client;

    @BeforeEach
    void setUp() {
        client = new XiaohongshuApiClient(
                new RestTemplate(), new ObjectMapper(), new XiaohongshuRequestSigner(new RestTemplate(), new ObjectMapper()));
    }

    @Test
    void parseNotesFromJson_webNoteCardShape() {
        String json = """
                {
                  "code": 0,
                  "success": true,
                  "data": {
                    "items": [
                      {
                        "model_type": "note",
                        "note_card": {
                          "note_id": "note1",
                          "display_title": "新加坡必打卡",
                          "desc": "滨海湾 鱼尾狮",
                          "interact_info": { "liked_count": "999", "collected_count": "500" },
                          "user": { "nickname": "背包客" }
                        }
                      }
                    ]
                  }
                }
                """;

        List<XiaohongshuNote> notes = client.parseNotesFromJson(json);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getTitle()).isEqualTo("新加坡必打卡");
        assertThat(notes.get(0).getLikes()).isEqualTo(999);
    }

    @Test
    void parseNotesFromJson_gatewayShape() {
        String json = """
                {
                  "code": "0",
                  "message": "ok",
                  "data": {
                    "items": [
                      {
                        "note_id": "abc123",
                        "title": "新加坡3天2夜攻略",
                        "desc": "鱼尾狮、滨海湾、圣淘沙",
                        "liked_count": 1200,
                        "collected_count": 800,
                        "user": { "nickname": "旅行达人" }
                      }
                    ]
                  }
                }
                """;

        List<XiaohongshuNote> notes = client.parseNotesFromJson(json);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getTitle()).isEqualTo("新加坡3天2夜攻略");
        assertThat(notes.get(0).getLikes()).isEqualTo(1200);
        assertThat(notes.get(0).getAuthor()).isEqualTo("旅行达人");
    }
}
