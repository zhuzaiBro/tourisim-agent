package com.tourism.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourism.rag.agent.AgentDataUnavailableException;
import com.tourism.rag.agent.util.GeoUtils;
import com.tourism.rag.agent.util.PoiIndoorClassifier;
import com.tourism.rag.dto.ItinerarySummaryDto;
import com.tourism.rag.dto.agent.*;
import com.tourism.rag.entity.ItineraryRecord;
import com.tourism.rag.repository.CityRepository;
import com.tourism.rag.util.CityGeoResolver;
import com.tourism.rag.util.CityNameResolver;
import com.tourism.rag.repository.ItineraryRecordRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 智能行程 Agent 核心编排服务。
 * <p>
 * 执行流程：
 * 1. 输入校验
 * 2. 并行调用 天气 / POI / 美食 工具（高德 + RAG，失败即报错）
 * 3. 路线最近邻优化
 * 4. 用 LLM 生成叙述文本
 * 5. 组装 ItineraryResponse
 * 6. 异步持久化到 MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryAgentService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── 配置 ──────────────────────────────────────────────────────────────
    @Value("${agent.timeout-ms:8000}")
    private long toolTimeoutMs;

    @Value("${agent.max-days:7}")
    private int maxDays;

    // ── 依赖注入 ──────────────────────────────────────────────────────────
    private final AgentToolFacade     toolFacade;

    private final ChatLanguageModel       chatLanguageModel;
    private final ObjectMapper            objectMapper;
    private final ItineraryRecordRepository itineraryRepo;
    private final CityRepository          cityRepository;
    private final CityGeoResolver         cityGeoResolver;

    // ======================== 主入口 ========================

    public ItineraryResponse generate(ItineraryRequest req, Long userId) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put("requestId", requestId);
        log.info("[Agent] 开始生成行程 requestId={}, city={}, {}->{}", requestId,
                req.getCityCode(), req.getStartDate(), req.getEndDate());

        try {
            validate(req);

            String cityName = CityNameResolver.resolve(req.getCityCode(), req.getCityName(), cityRepository);
            List<LocalDate> dates = buildDateRange(req.getStartDate(), req.getEndDate());
            List<ToolCallLog> toolLogs = new ArrayList<>();

            // ── Step1: 并行获取天气 + POI + 美食 ──────────────────────────
            ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

            // 天气（全日期范围一次性取）
            Future<List<WeatherInfo>> weatherFuture = exec.submit(
                    () -> toolFacade.callWeather(req.getCityCode(), cityName, req.getStartDate(), req.getEndDate(), toolLogs));

            int totalDays = dates.size();
            Future<List<PoiInfo>> poiFuture = exec.submit(
                    () -> toolFacade.callPOI(
                            req.getCityCode(), cityName, req.getPreferences(), totalDays, toolLogs));

            List<WeatherInfo> allWeather;
            List<PoiInfo>     allPois;
            try {
                allWeather = weatherFuture.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
                allPois    = poiFuture.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
                PoiIndoorClassifier.applyIndoorFlags(allPois);
            } catch (TimeoutException e) {
                throw new AgentDataUnavailableException(
                        "天气/POI 工具调用超时（" + toolTimeoutMs + "ms），请稍后重试");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AgentDataUnavailableException ade) {
                    throw ade;
                }
                throw new AgentDataUnavailableException(
                        "天气/POI 工具调用失败: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
            } finally {
                exec.shutdown();
            }

            Map<String, WeatherInfo> weatherByDate = allWeather.stream()
                    .collect(Collectors.toMap(WeatherInfo::getDate, w -> w));

            // ── Step2: 逐日编排 ──────────────────────────────────────────
            List<DayPlan> days = new ArrayList<>();
            int totalPoiCount = allPois.size();
            int poisPerDay = Math.max(2, Math.min(4, totalPoiCount / Math.max(1, dates.size())));

            double[] cityCenter = cityGeoResolver.resolve(req.getCityCode(), cityName);

            for (int i = 0; i < dates.size(); i++) {
                LocalDate date = dates.get(i);
                String dateStr = date.format(DATE_FMT);

                WeatherInfo weather = weatherByDate.get(dateStr);
                if (weather == null) {
                    throw new AgentDataUnavailableException(
                            "高德天气缺少日期数据: " + dateStr + "，请检查日期范围或 API 返回");
                }

                // 按天分配景点（循环利用，避免重复）
                int startIdx = (i * poisPerDay) % Math.max(1, totalPoiCount);
                List<PoiInfo> dayPois = new ArrayList<>();
                for (int j = 0; j < poisPerDay; j++) {
                    dayPois.add(allPois.get((startIdx + j) % totalPoiCount));
                }

                // 户外 vs 室内分组
                List<PoiInfo> outdoorPois = dayPois.stream().filter(p -> !PoiIndoorClassifier.isIndoor(p)).collect(Collectors.toList());
                List<PoiInfo> indoorPois  = dayPois.stream().filter(PoiIndoorClassifier::isIndoor).collect(Collectors.toList());
                if (indoorPois.size() < 2) {
                    indoorPois = allPois.stream().filter(PoiIndoorClassifier::isIndoor).limit(4).collect(Collectors.toList());
                }

                // 路线优化：晴天主方案户外优先，雨天备选室内优先
                List<PoiInfo> mainPois  = weather.isOutdoorFriendly() ? dayPois : indoorPois;
                List<PoiInfo> altPois   = !indoorPois.isEmpty()
                        ? indoorPois
                        : (weather.isOutdoorFriendly() ? indoorPois : outdoorPois);
                if (altPois.isEmpty()) {
                    altPois = !mainPois.isEmpty() ? mainPois : dayPois;
                }
                RouteInfo mainRoute = toolFacade.callRoute(mainPois, cityCenter[0], cityCenter[1],
                        req.getTransportMode(), toolLogs);
                RouteInfo altRoute  = toolFacade.callRoute(altPois,  cityCenter[0], cityCenter[1],
                        req.getTransportMode(), toolLogs);

                // 参考坐标取第一个景点坐标，用于附近美食搜索
                double refLat = mainRoute.getOptimizedPois().isEmpty()
                        ? cityCenter[0] : mainRoute.getOptimizedPois().get(0).getLat();
                double refLng = mainRoute.getOptimizedPois().isEmpty()
                        ? cityCenter[1] : mainRoute.getOptimizedPois().get(0).getLng();

                List<FoodRecommendation> foods = toolFacade.callFood(
                        req.getCityCode(), cityName, refLat, refLng, req.getPreferences(),
                        req.getDietaryRestrictions(), req.getTastePreferences(), toolLogs);

                // 时间槽编排
                List<TimeSlotActivity> mainActivities = buildActivities(
                        mainRoute.getOptimizedPois(), mainRoute, req.getTransportMode(), weather, true);
                List<TimeSlotActivity> altActivities  = buildActivities(
                        altRoute.getOptimizedPois(),  altRoute,  req.getTransportMode(), weather, false);

                // LLM 叙述（失败时降级为固定文本）
                String narrative = generateNarrative(date, weather, mainRoute.getOptimizedPois(),
                        foods, req.getPreferences(), cityName);

                List<String> tips = buildTips(weather, req.getPreferences(), req.getTransportMode());
                Map<String, String> dayBudget = estimateBudget(mainActivities, foods, req.getBudget());

                days.add(DayPlan.builder()
                        .date(dateStr)
                        .dayNumber(i + 1)
                        .dayOfWeek(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINESE))
                        .weather(weather)
                        .mainPlanTitle(weather.isOutdoorFriendly()
                                ? "☀️ 晴天方案：" + cityName + "经典户外游"
                                : "🌧️ 晴天方案：户外优先游览")
                        .mainActivities(mainActivities)
                        .alternatePlanTitle(weather.isOutdoorFriendly()
                                ? "🌂 雨天备选：博物馆与室内文化体验"
                                : "☀️ 好天气方案：户外精华打卡")
                        .alternateActivities(altActivities)
                        .route(mainRoute)
                        .alternateRoute(altRoute)
                        .foods(foods)
                        .tips(tips)
                        .narrative(narrative)
                        .budget(dayBudget)
                        .build());
            }

            // ── Step3: 住宿安排 ──────────────────────────────────────────
            double accLat = allPois.stream().mapToDouble(PoiInfo::getLat).average().orElse(cityCenter[0]);
            double accLng = allPois.stream().mapToDouble(PoiInfo::getLng).average().orElse(cityCenter[1]);
            String accType = req.getAccommodationType() != null ? req.getAccommodationType() : "hotel";
            AccommodationSearchResult accommodation = toolFacade.callAccommodation(
                    req.getCityCode(), cityName, accLat, accLng,
                    accType, req.getBudget(), req.getPreferences(), toolLogs);

            // ── Step4: 全程预算汇总 ──────────────────────────────────────
            Map<String, String> totalBudget = aggregateBudget(days, req.getBudget());

            // ── Step5: 生成全程总结 ──────────────────────────────────────
            String tripSummary = generateTripSummary(req, cityName, days);

            // ── Step6: 组装响应 ───────────────────────────────────────────
            String itineraryId = UUID.randomUUID().toString();
            ItineraryResponse response = ItineraryResponse.builder()
                    .itineraryId(itineraryId)
                    .requestId(requestId)
                    .cityCode(req.getCityCode())
                    .cityName(cityName)
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .totalDays(dates.size())
                    .preferences(req.getPreferences())
                    .budget(req.getBudget())
                    .transportMode(req.getTransportMode())
                    .tripSummary(tripSummary)
                    .days(days)
                    .totalBudget(totalBudget)
                    .toolCallLogs(toolLogs)
                    .generatedAt(LocalDateTime.now().format(ISO_FMT))
                    .hasRealWeatherData(toolLogs.stream().anyMatch(
                            l -> "getWeather".equals(l.getToolName()) && !l.isUsedFallback()))
                    .hasRealPoiData(toolLogs.stream().anyMatch(
                            l -> "searchPOI".equals(l.getToolName()) && !l.isUsedFallback()
                                    && l.getProvider() != null && !"mock".equals(l.getProvider())))
                    .hasRealFoodData(toolLogs.stream().anyMatch(
                            l -> "recommendFood".equals(l.getToolName()) && !l.isUsedFallback()))
                    .accommodations(accommodation.getAccommodations())
                    .primaryAccommodation(accommodation.getPrimary())
                    .accommodationTips(accommodation.getTips())
                    .hasRealAccommodationData(toolLogs.stream().anyMatch(
                            l -> "recommendAccommodation".equals(l.getToolName()) && !l.isUsedFallback()))
                    .build();

            // 异步持久化
            saveAsync(itineraryId, req, response, userId);

            log.info("[Agent] 行程生成成功 itineraryId={}, days={}", itineraryId, dates.size());
            return response;

        } catch (IllegalArgumentException | AgentDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Agent] 行程生成异常 requestId={}", requestId, e);
            throw new RuntimeException("行程生成失败，请稍后重试：" + e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }

    public Optional<ItineraryResponse> getById(String id) {
        return itineraryRepo.findById(id).map(record -> {
            try {
                return objectMapper.readValue(record.getResponseJson(), ItineraryResponse.class);
            } catch (Exception e) {
                log.warn("[Agent] 解析历史行程失败 id={}", id, e);
                return null;
            }
        });
    }

    public Map<String, Object> listByUser(Long userId, int page, int size) {
        Page<ItineraryRecord> records = itineraryRepo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        List<ItinerarySummaryDto> items = records.getContent().stream()
                .map(r -> {
                    String summary = "";
                    try {
                        ItineraryResponse resp = objectMapper.readValue(r.getResponseJson(), ItineraryResponse.class);
                        summary = resp.getTripSummary() != null ? resp.getTripSummary() : "";
                    } catch (Exception ignored) {}
                    return ItinerarySummaryDto.builder()
                            .id(r.getId())
                            .cityCode(r.getCityCode())
                            .cityName(r.getCityName())
                            .startDate(r.getStartDate())
                            .endDate(r.getEndDate())
                            .totalDays(r.getTotalDays())
                            .tripSummary(summary)
                            .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                            .build();
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        result.put("total", records.getTotalElements());
        result.put("totalPages", records.getTotalPages());
        return result;
    }

    // ======================== 时间槽编排 ========================

    /**
     * 生成全天行程时间槽：上午景点 → 午餐 → 下午景点 → 傍晚自由 → 晚餐 → 夜间自由
     */
    private List<TimeSlotActivity> buildActivities(List<PoiInfo> pois, RouteInfo route,
                                                    String transport, WeatherInfo weather,
                                                    boolean isMain) {
        List<TimeSlotActivity> activities = new ArrayList<>();
        if (pois.isEmpty()) return activities;

        // 将景点平均分为上午场与下午场
        int half = (pois.size() + 1) / 2;
        List<PoiInfo> morningPois   = new ArrayList<>(pois.subList(0, half));
        List<PoiInfo> afternoonPois = new ArrayList<>(
                pois.size() > half ? pois.subList(half, pois.size()) : List.of());

        int h = 9, m = 0;

        // ── 上午场（09:00 起）────────────────────────────────────────
        for (int i = 0; i < morningPois.size(); i++) {
            PoiInfo poi   = morningPois.get(i);
            int travelMin = (i > 0 && route.getLegs().size() > i)
                    ? route.getLegs().get(i).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from  = hm(h, m);
            int visitMin = Math.min(poi.getVisitDurationMinutes(), 150); // 单景点上午最多 2.5 h
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 12) { h = 12; m = 0; }                             // 上午不超过 12:00
            String to    = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName()).type("attraction").poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "从酒店/住所出发" : suggestTransport(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildPoiNote(poi, weather))
                    .build());
        }

        // ── 午餐（12:00-13:00）───────────────────────────────────────
        activities.add(TimeSlotActivity.builder()
                .timeSlot("12:00-13:00").activity("午餐时间").type("food")
                .durationMinutes(60).estimatedCost(0)
                .notes("推荐就近用餐，品尝当地特色美食").build());

        // ── 下午场（13:00 起）────────────────────────────────────────
        h = 13; m = 0;
        for (int i = 0; i < afternoonPois.size(); i++) {
            PoiInfo poi   = afternoonPois.get(i);
            int absLeg    = half + i;
            int travelMin = (i > 0 && route.getLegs().size() > absLeg)
                    ? route.getLegs().get(absLeg).getDurationMinutes() : 0;
            if (i > 0) { m += travelMin; h += m / 60; m %= 60; }

            String from  = hm(h, m);
            int visitMin = poi.getVisitDurationMinutes();
            m += visitMin; h += m / 60; m %= 60;
            if (h >= 18) { h = 17; m = 30; }                            // 下午不超过 17:30
            String to    = hm(h, m);

            activities.add(TimeSlotActivity.builder()
                    .timeSlot(from + "-" + to)
                    .activity(poi.getName()).type("attraction").poi(poi)
                    .durationMinutes(visitMin)
                    .transportFromPrev(i == 0 ? "午餐后出发" : suggestTransport(travelMin, transport))
                    .transportMinutes(travelMin)
                    .estimatedCost(parseTicketCost(poi.getTicketPrice()))
                    .notes(buildPoiNote(poi, weather))
                    .build());
        }

        // ── 傍晚自由（若下午活动结束早于 17:00，补充自由时间）──────────
        if (h < 17) {
            activities.add(TimeSlotActivity.builder()
                    .timeSlot(hm(h, m) + "-17:30")
                    .activity("自由探索 / 休闲购物").type("rest")
                    .durationMinutes(0).estimatedCost(0)
                    .notes("自由漫步周边街区，感受当地市井风情，或前往商业街购物").build());
        }

        // ── 晚餐（18:00-19:30）───────────────────────────────────────
        activities.add(TimeSlotActivity.builder()
                .timeSlot("18:00-19:30").activity("晚餐时间").type("food")
                .durationMinutes(90).estimatedCost(0)
                .notes("结束今日游览，享用晚餐，推荐当地特色餐厅").build());

        // ── 夜间自由（19:30-21:00）───────────────────────────────────
        activities.add(TimeSlotActivity.builder()
                .timeSlot("19:30-21:00").activity("夜间自由时光").type("rest")
                .durationMinutes(90).estimatedCost(0)
                .notes("漫步夜市、欣赏夜景，或提前返回住所休息").build());

        return activities;
    }

    // ======================== LLM 叙述生成 ========================

    private String generateNarrative(LocalDate date, WeatherInfo weather, List<PoiInfo> pois,
                                      List<FoodRecommendation> foods, List<String> preferences,
                                      String cityName) {
        try {
            String poiNames = pois.stream().map(PoiInfo::getName).collect(Collectors.joining("、"));
            String foodNames = foods.stream().limit(2).map(FoodRecommendation::getName)
                    .collect(Collectors.joining("、"));
            String prefStr = preferences == null ? "休闲游" : String.join("/", preferences);

            String prompt = String.format("""
                    用一段100字以内的中文，描述这一天的旅游行程亮点。风格轻松活泼，突出当地特色。
                    城市：%s
                    日期：%s，%s
                    天气：%s，%d~%d℃
                    游玩景点：%s
                    推荐美食：%s
                    出行偏好：%s
                    """, cityName, date.format(DATE_FMT), weather.getConditionText(),
                    weather.getConditionText(), weather.getTempLow(), weather.getTempHigh(),
                    poiNames.isEmpty() ? "待定" : poiNames,
                    foodNames.isEmpty() ? "当地特色" : foodNames, prefStr);

            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.debug("[Agent] LLM 叙述生成失败，使用默认文本：{}", e.getMessage());
            return String.format("第%d天行程：%s天气%s（%d~%d℃），探访%s等地，品尝地道美食，度过充实的一天。",
                    date.getDayOfMonth(), cityName,
                    weather.getConditionText(), weather.getTempLow(), weather.getTempHigh(),
                    pois.isEmpty() ? "当地景点" : pois.get(0).getName());
        }
    }

    private String generateTripSummary(ItineraryRequest req, String cityName, List<DayPlan> days) {
        try {
            String highlights = days.stream()
                    .flatMap(d -> d.getMainActivities().stream().limit(1))
                    .map(TimeSlotActivity::getActivity)
                    .collect(Collectors.joining("、"));
            String prompt = String.format("""
                    用80字以内的中文，写一段%s%d天旅行的总体介绍，突出亮点。
                    主要景点：%s，偏好：%s，预算档位：%s
                    """, cityName, req.getEndDate().equals(req.getStartDate()) ? 1 : days.size(),
                    highlights.isEmpty() ? cityName + "各大景点" : highlights,
                    req.getPreferences() != null ? String.join("/", req.getPreferences()) : "综合",
                    req.getBudget());
            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            return String.format("精心规划的%s%d日行程，融合自然风光、历史文化与地道美食，为您带来难忘的旅游体验。",
                    cityName, days.size());
        }
    }

    // ======================== 辅助方法 ========================

    private List<String> buildTips(WeatherInfo weather, List<String> preferences, String transport) {
        List<String> tips = new ArrayList<>();
        if (!weather.isOutdoorFriendly()) {
            tips.add("今天有 " + weather.getConditionText() + "，建议携带雨具");
            tips.add("雨天路面湿滑，穿防滑鞋出行更安全");
        }
        if (weather.getTempHigh() >= 30) tips.add("气温较高，注意防晒补水");
        if (weather.getTempLow() <= 5)   tips.add("早晚温差大，注意添加衣物");
        try {
            String ws = weather.getWindScale().replaceAll("[^\\d]", "").trim();
            int windScale = ws.isEmpty() ? 0 : Integer.parseInt(ws.substring(0, 1));
            if (windScale >= 4) tips.add("风力较强（" + weather.getWindScale() + "级），海边景点注意防风");
        } catch (Exception ignored) { }
        if (preferences != null && preferences.contains("family")) {
            tips.add("携带儿童出行，景区提前确认儿童票政策");
        }
        if (preferences != null && preferences.contains("photography")) {
            tips.add("摄影爱好者建议携带三脚架，清晨光线最佳");
        }
        if ("driving".equals(transport)) {
            tips.add("自驾出行注意景区停车位，假期建议提前占位");
        }
        if (tips.isEmpty()) tips.add("提前查看景区最新开放公告，祝旅途愉快！");
        return tips;
    }

    private Map<String, String> estimateBudget(List<TimeSlotActivity> activities,
                                                List<FoodRecommendation> foods, String budget) {
        double attractionCost = activities.stream()
                .mapToDouble(TimeSlotActivity::getEstimatedCost).sum();
        double foodCost = switch (budget) {
            case "low"  -> 60;
            case "high" -> 200;
            default     -> 120;
        };
        double transportCost = switch (budget) {
            case "low"  -> 20;
            case "high" -> 80;
            default     -> 40;
        };
        double total = attractionCost + foodCost * 3 + transportCost;
        double var   = total * 0.2;
        Map<String, String> b = new LinkedHashMap<>();
        b.put("attraction", rangeStr(attractionCost, var));
        b.put("food",       rangeStr(foodCost * 3, var));
        b.put("transport",  rangeStr(transportCost, var * 0.5));
        b.put("total",      rangeStr(total, var));
        return b;
    }

    private Map<String, String> aggregateBudget(List<DayPlan> days, String budget) {
        Map<String, String> total = new LinkedHashMap<>();
        total.put("attraction", "见每日行程");
        total.put("food",       "见每日行程");
        total.put("transport",  "见每日行程");
        int perDay = switch (budget) {
            case "low"  -> 200;
            case "high" -> 800;
            default     -> 450;
        };
        int min = perDay * days.size();
        int max = (int)(min * 1.3);
        total.put("total", min + "–" + max + " 元/人");
        return total;
    }

    private void validate(ItineraryRequest req) {
        LocalDate start = LocalDate.parse(req.getStartDate(), DATE_FMT);
        LocalDate end   = LocalDate.parse(req.getEndDate(),   DATE_FMT);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long days = start.until(end).getDays() + 1;
        if (days > maxDays) {
            throw new IllegalArgumentException("行程最长支持 " + maxDays + " 天");
        }
    }

    private List<LocalDate> buildDateRange(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate, DATE_FMT);
        LocalDate end   = LocalDate.parse(endDate,   DATE_FMT);
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            dates.add(cur);
            cur = cur.plusDays(1);
        }
        return dates;
    }

    @Async
    public void saveAsync(String id, ItineraryRequest req, ItineraryResponse resp, Long userId) {
        try {
            itineraryRepo.save(ItineraryRecord.builder()
                    .id(id)
                    .cityCode(req.getCityCode())
                    .cityName(resp.getCityName())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .totalDays(resp.getTotalDays())
                    .responseJson(objectMapper.writeValueAsString(resp))
                    .requestJson(objectMapper.writeValueAsString(req))
                    .userId(userId)
                    .build());
        } catch (Exception e) {
            log.warn("[Agent] 行程持久化失败 id={}", id, e);
        }
    }

    // ======================== 格式化工具 ========================

    private static String hm(int hour, int min) {
        return String.format("%02d:%02d", Math.min(hour, 22), min % 60);
    }

    private static String rangeStr(double base, double variance) {
        int low  = (int) Math.max(0, Math.round(base - variance));
        int high = (int) Math.round(base + variance);
        if (low == 0 && high == 0) return "免费";
        if (low == high) return high + " 元";
        return low + "–" + high + " 元";
    }

    private static double parseTicketCost(String ticketPrice) {
        if (ticketPrice == null || ticketPrice.contains("免费")) return 0;
        try {
            return Double.parseDouble(ticketPrice.replaceAll("[^\\d.]", "").split("\\.")[0]);
        } catch (Exception e) { return 50; }
    }

    private static String suggestTransport(int minutes, String mode) {
        if (minutes <= 10) return "步行";
        if ("driving".equals(mode)) return "打车/自驾";
        if (minutes <= 20) return "公交";
        return "公交/地铁";
    }

    private static String buildPoiNote(PoiInfo poi, WeatherInfo weather) {
        List<String> notes = new ArrayList<>();
        if (poi.getDescription() != null && !poi.getDescription().isBlank()
                && poi.getDataSource() != null && poi.getDataSource().contains("xhs")) {
            notes.add("攻略口碑：" + poi.getDescription());
        }
        if (poi.getTicketPrice() != null && !poi.getTicketPrice().contains("免费")) {
            notes.add("需购票：" + poi.getTicketPrice());
        }
        if (!weather.isOutdoorFriendly() && !poi.isIndoorVenue()) {
            notes.add("天气原因建议携带雨具");
        }
        if (poi.getOpeningHours() != null && !poi.getOpeningHours().contains("全天")) {
            notes.add("开放时间：" + poi.getOpeningHours());
        }
        return String.join("；", notes);
    }
}
