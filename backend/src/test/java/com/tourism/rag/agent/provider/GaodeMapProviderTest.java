package com.tourism.rag.agent.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.dto.agent.PoiInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GaodeMapProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private GaodeApiClient apiClient;
    private GaodeMapProvider provider;

    @BeforeEach
    void setUp() {
        apiClient = new GaodeApiClient(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(apiClient, "apiKey", "test-api-key");
        provider = new GaodeMapProvider(apiClient);
    }

    @Test
    void searchPOI_encodesCityAndTypesInRequestUri() {
        String gaodeResponse = """
                {
                  "status": "1",
                  "info": "OK",
                  "count": "1",
                  "pois": [{
                    "id": "B001",
                    "name": "栈桥",
                    "type": "风景名胜",
                    "address": "市南区",
                    "location": "120.31,36.06",
                    "biz_ext": { "rating": "4.8" },
                    "opentime_week": "全天"
                  }]
                }
                """;

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        when(restTemplate.getForObject(uriCaptor.capture(), eq(String.class))).thenReturn(gaodeResponse);

        List<PoiInfo> pois = provider.searchPOI("qingdao", "青岛", List.of("景点"), List.of(), 12);

        URI uri = uriCaptor.getAllValues().get(0);
        String raw = uri.toString();

        assertThat(raw).contains("city=%E9%9D%92%E5%B2%9B");
        assertThat(raw).contains("types=110000%7C141200");
        assertThat(raw).doesNotContain("city=青岛");
        assertThat(pois).hasSize(1);
        assertThat(pois.get(0).getDataSource()).isEqualTo("gaode_api");
        assertThat(pois.get(0).getName()).isEqualTo("栈桥");
    }

    @Test
    void searchPOI_throwsWhenApiKeyMissing() {
        ReflectionTestUtils.setField(apiClient, "apiKey", "");
        assertThatThrownBy(() -> provider.searchPOI("qingdao", "青岛", List.of("景点"), List.of(), 12))
                .isInstanceOf(AgentDataUnavailableException.class)
                .hasMessageContaining("MAP_API_KEY");
    }
}
