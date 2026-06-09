package com.tourism.rag.service;

import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.dto.AttractionDto;
import com.tourism.rag.entity.Attraction;
import com.tourism.rag.repository.AttractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AttractionQueryService {

    private final AttractionRepository attractionRepository;

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "attraction", "景点",
            "food", "美食",
            "transport", "交通",
            "accommodation", "住宿",
            "festival", "节庆"
    );

    public List<AttractionDto> listByCity(String cityCode) {
        String code = cityCode.toLowerCase().trim();
        double[] center = GeoUtils.getCityCenter(code);
        List<Attraction> rows = attractionRepository.findByCityCode(code);
        List<AttractionDto> result = new ArrayList<>();

        int i = 0;
        for (Attraction a : rows) {
            double[] coords = offsetFromCenter(center, i++);
            result.add(AttractionDto.builder()
                    .id(a.getId())
                    .cityCode(a.getCityCode())
                    .name(a.getName())
                    .category(a.getCategory())
                    .categoryLabel(CATEGORY_LABELS.getOrDefault(a.getCategory(), "景点"))
                    .description(a.getDescription())
                    .address(a.getAddress())
                    .ticketPrice(a.getTicketPrice() != null ? a.getTicketPrice().toPlainString() + "元" : "请查询")
                    .openingHours(a.getOpeningHours())
                    .rating(a.getRating() != null ? a.getRating().doubleValue() : 4.5)
                    .lat(coords[0])
                    .lng(coords[1])
                    .dataSource("mysql")
                    .build());
        }
        return result;
    }

    /** DB 无坐标时围绕城市中心微偏移，避免 marker 重叠 */
    private double[] offsetFromCenter(double[] center, int index) {
        double angle = index * 0.7;
        double r = 0.008 + (index % 5) * 0.003;
        return new double[]{
                center[0] + r * Math.cos(angle),
                center[1] + r * Math.sin(angle)
        };
    }
}
