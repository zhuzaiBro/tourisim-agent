package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.PoiInfo;
import com.tourism.rag.dto.agent.RouteLeg;
import com.tourism.rag.dto.agent.RouteInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock 地图/POI 提供者——内置多城市景点数据，路线采用最近邻启发式算法。
 */
@Slf4j
@Component
public class MockMapProvider implements MapProvider {

    // ======================== 城市 POI 数据 ========================

    private static final List<PoiInfo> QINGDAO_POIS = List.of(
        poi("qd001", "崂山风景区", "nature_park",    "崂山区崂山路", 36.1578, 120.5932,
            "08:00-17:30", "65元", 180, false, List.of("自然","爬山","宗教"), "道教圣地，海上名山"),
        poi("qd002", "栈桥公园",   "landmark",       "市南区太平路22号", 36.0574, 120.3117,
            "全天开放", "免费", 60, false, List.of("海滨","网红","情侣"), "青岛标志性地标，百年历史"),
        poi("qd003", "八大关景区", "historic_area",  "市南区韶关路", 36.0638, 120.3405,
            "全天开放", "免费", 90, false, List.of("建筑","摄影","情侣"), "万国建筑博览区，网红打卡地"),
        poi("qd004", "青岛海底世界","aquarium",      "市南区莱阳路2号", 36.0594, 120.3311,
            "08:30-17:30","120元", 120, true,  List.of("亲子","室内","海洋"), "亲子必游，大型海洋馆"),
        poi("qd005", "即墨古城",   "historic_area",  "即墨区墨城路1号", 36.3893, 120.4435,
            "09:00-21:00","免费", 150, false, List.of("历史","古镇","文化"), "明清风格古城，夜景绝美"),
        poi("qd006", "青岛啤酒博物馆","museum",      "市北区登州路56号", 36.0871, 120.3680,
            "09:00-18:00","60元", 90, true,  List.of("历史","品酒","文化"), "百年啤酒文化，可现场品尝"),
        poi("qd007", "第一海水浴场","beach",         "市南区汇泉路1号",  36.0629, 120.3483,
            "07:00-19:00","免费", 120, false, List.of("海滨","游泳","亲子"), "青岛最著名的海水浴场"),
        poi("qd008", "五四广场",   "landmark",       "市南区东海西路8号",36.0533, 120.3688,
            "全天开放", "免费", 45, false, List.of("广场","摄影","地标"), "五月的风雕塑，城市地标")
    );

    private static final List<PoiInfo> BEIJING_POIS = List.of(
        poi("bj001", "故宫博物院",  "museum",         "东城区景山前街4号", 39.9163, 116.3972,
            "08:30-17:00","80元", 240, true,  List.of("历史","文化","亲子"), "世界最大宫殿建筑群，必游之地"),
        poi("bj002", "天安门广场", "landmark",        "东城区天安门广场",  39.9087, 116.3975,
            "全天开放","免费", 60, false, List.of("地标","爱国","历史"), "中国最重要的广场"),
        poi("bj003", "颐和园",     "imperial_garden", "海淀区新建宫门路19号",40.0006,116.2755,
            "06:30-18:00","30元", 180, false, List.of("园林","历史","皇家"), "中国最大皇家园林"),
        poi("bj004", "天坛公园",   "historic_park",   "东城区天坛东里",    39.8828, 116.4105,
            "06:00-22:00","15元", 120, false, List.of("历史","建筑","文化"), "明清皇帝祭天场所"),
        poi("bj005", "南锣鼓巷",   "historic_area",   "东城区南锣鼓巷",    39.9330, 116.4070,
            "全天开放", "免费", 90, false, List.of("美食","购物","胡同"), "北京最有特色的胡同文化街区"),
        poi("bj006", "798艺术区",  "art_district",    "朝阳区酒仙桥路4号", 39.9842, 116.4965,
            "10:00-22:00","免费", 120, true,  List.of("艺术","摄影","文化"), "北京当代艺术中心"),
        poi("bj007", "国家博物馆", "museum",          "东城区天安门广场东", 39.9041, 116.4022,
            "09:00-17:00","免费", 180, true,  List.of("历史","文化","亲子"), "中华文明最全面的展示"),
        poi("bj008", "什刹海景区", "lake_scenic",     "西城区什刹海",       39.9384, 116.3809,
            "全天开放","免费", 90, false, List.of("水景","胡同","情侣"), "老北京风情，四合院环绕")
    );

    private static final List<PoiInfo> SHANGHAI_POIS = List.of(
        poi("sh001", "外滩",        "landmark",       "黄浦区中山东一路",   31.2397, 121.4901,
            "全天开放","免费", 90, false, List.of("夜景","地标","摄影"), "上海最著名的夜景胜地"),
        poi("sh002", "豫园",        "historic_garden","黄浦区安仁街218号",  31.2268, 121.4914,
            "08:30-17:00","40元", 120, false, List.of("园林","历史","文化"), "明代园林，精致古典"),
        poi("sh003", "上海迪士尼",  "theme_park",     "浦东新区川沙新镇",   31.1470, 121.6660,
            "09:00-21:00","595元", 480, false, List.of("亲子","娱乐","主题"), "亚洲最大迪士尼主题乐园"),
        poi("sh004", "新天地",      "shopping_area",  "黄浦区马当路181号",   31.2211, 121.4731,
            "全天开放","免费", 120, false, List.of("购物","美食","时尚"), "上海时尚地标，石库门建筑"),
        poi("sh005", "上海博物馆",  "museum",         "黄浦区人民大道201号",31.2292, 121.4742,
            "09:00-17:00","免费", 150, true,  List.of("历史","艺术","文化"), "中国青铜器收藏最完整之地"),
        poi("sh006", "朱家角古镇",  "ancient_town",   "青浦区朱家角镇",      31.1122, 121.0635,
            "全天开放","免费", 180, false, List.of("古镇","水乡","文化"), "江南水乡古镇，1700年历史"),
        poi("sh007", "上海科技馆",  "science_museum", "浦东新区世纪大道2000号",31.2177,121.5371,
            "09:00-17:15","60元", 180, true,  List.of("亲子","科技","教育"), "亚洲一流科技博物馆")
    );

    private static final List<PoiInfo> XIAN_POIS = List.of(
        poi("xa001", "兵马俑",      "historic_site",  "临潼区秦陵北路",     34.3836, 109.2784,
            "08:30-18:30","150元", 240, false, List.of("历史","文化","世界遗产"), "世界第八大奇迹"),
        poi("xa002", "古城墙",      "historic_wall",  "碑林区文昌门",       34.2583, 108.9486,
            "08:00-22:00","54元", 120, false, List.of("历史","自行车","地标"), "中国现存最完整的古城墙"),
        poi("xa003", "大雁塔",      "temple",         "雁塔区大慈恩寺内",   34.2228, 108.9640,
            "08:00-18:00","50元", 90, false, List.of("宗教","历史","文化"), "唐代佛塔，玄奘取经归来所建"),
        poi("xa004", "回民街",      "food_street",    "莲湖区大皮院",       34.2667, 108.9410,
            "全天开放","免费", 90, false, List.of("美食","夜市","文化"), "西安最著名的清真美食街"),
        poi("xa005", "西安博物院",  "museum",         "碑林区小寨西路91号", 34.2368, 108.9540,
            "09:00-17:00","免费", 150, true,  List.of("历史","文化","免费"), "海量文物展示关中文化"),
        poi("xa006", "华清池",      "imperial_resort","临潼区华清路038号",   34.3598, 109.2147,
            "07:00-20:00","120元",120, false, List.of("历史","皇家","温泉"), "唐明皇与杨贵妃的爱情圣地")
    );

    private static final List<PoiInfo> CHENGDU_POIS = List.of(
        poi("cd001", "成都大熊猫基地","wildlife_park","成华区熊猫大道1375号",30.7313,104.1410,
            "07:30-18:00","90元", 180, false, List.of("亲子","动物","必游"), "全球最大大熊猫保护基地"),
        poi("cd002", "宽窄巷子",    "historic_area",  "青羊区长顺上街",     30.6694, 104.0563,
            "全天开放","免费", 120, false, List.of("文化","美食","历史"), "清代古街道，成都慢生活代表"),
        poi("cd003", "武侯祠",      "temple",         "武侯区武侯祠大街231号",30.6417,104.0486,
            "08:00-18:00","50元", 90, false, List.of("历史","文化","三国"), "中国唯一君臣合祀祠庙"),
        poi("cd004", "锦里古街",    "historic_street","武侯区武侯祠大街231号",30.6412,104.0479,
            "全天开放","免费", 90, false, List.of("美食","购物","文化"), "西蜀第一街，紧邻武侯祠"),
        poi("cd005", "都江堰",      "historic_site",  "都江堰市灌县古城",   30.9971, 103.6175,
            "08:00-18:00","90元", 180, false, List.of("历史","自然","世界遗产"), "2000年历史的水利工程"),
        poi("cd006", "成都博物馆",  "museum",         "青羊区小河街15号",   30.6623, 104.0646,
            "09:00-17:00","免费", 150, true,  List.of("历史","文化","免费"), "中国最大的市级博物馆之一"),
        poi("cd007", "天府广场",    "landmark",       "青羊区东御街",       30.6573, 104.0640,
            "全天开放","免费", 45, false, List.of("地标","广场","夜景"), "成都中心，夜晚极为壮观")
    );

    /** 城市代码 → POI 列表 */
    private static final Map<String, List<PoiInfo>> CITY_POIS = Map.of(
            "qingdao", QINGDAO_POIS,
            "beijing",  BEIJING_POIS,
            "shanghai", SHANGHAI_POIS,
            "xian",     XIAN_POIS,
            "chengdu",  CHENGDU_POIS
    );

    /** 城市中心坐标（用于路线优化起点） */
    private static final Map<String, double[]> CITY_CENTER = Map.of(
            "qingdao", new double[]{36.0671, 120.3826},
            "beijing",  new double[]{39.9042, 116.4074},
            "shanghai", new double[]{31.2304, 121.4737},
            "xian",     new double[]{34.2659, 108.9541},
            "chengdu",  new double[]{30.5728, 104.0668}
    );

    // ======================== 接口实现 ========================

    @Override
    public List<PoiInfo> searchPOI(String cityCode, String cityName, List<String> keywords,
                                    List<String> preferences, int maxResults) {
        log.info("[MockMap] searchPOI city={}, keywords={}, prefs={}", cityCode, keywords, preferences);
        List<PoiInfo> cityPois = CITY_POIS.getOrDefault(cityCode.toLowerCase(), QINGDAO_POIS);

        // 如偏好包含 family → 亲子友好优先；photography → 摄影类优先；food → 含美食地标
        List<PoiInfo> filtered = cityPois.stream()
                .filter(p -> matchesPreferences(p, preferences))
                .limit(maxResults)
                .collect(Collectors.toList());

        // 如过滤后不够，补充剩余
        if (filtered.size() < maxResults) {
            cityPois.stream()
                    .filter(p -> !filtered.contains(p))
                    .limit(maxResults - filtered.size())
                    .forEach(filtered::add);
        }
        return filtered;
    }

    @Override
    public RouteInfo planRoute(List<PoiInfo> pois, double startLat, double startLng, String transportMode) {
        log.info("[MockMap] planRoute pois={}, transport={}", pois.size(), transportMode);
        if (pois.isEmpty()) {
            return RouteInfo.builder().optimizedPois(pois).legs(List.of())
                    .totalDistanceKm(0).totalDurationMinutes(0)
                    .optimizationMethod("nearest_neighbor").dataSource("mock").build();
        }

        // 最近邻启发式算法
        List<PoiInfo> ordered = nearestNeighbor(pois, startLat, startLng);

        // 构建路段
        List<RouteLeg> legs = new ArrayList<>();
        double curLat = startLat, curLng = startLng;
        String fromName = "出发地/酒店";
        double totalDist = 0;
        int totalDuration = 0;

        for (PoiInfo poi : ordered) {
            double distKm = haversineKm(curLat, curLng, poi.getLat(), poi.getLng());
            int durMin = travelMinutes(distKm, transportMode);
            totalDist += distKm;
            totalDuration += durMin;
            legs.add(RouteLeg.builder()
                    .fromName(fromName)
                    .toName(poi.getName())
                    .distanceKm(Math.round(distKm * 10.0) / 10.0)
                    .durationMinutes(durMin)
                    .transportSuggestion(suggestTransport(distKm, transportMode))
                    .instruction(buildInstruction(fromName, poi.getName(), distKm, durMin))
                    .build());
            curLat = poi.getLat();
            curLng = poi.getLng();
            fromName = poi.getName();
        }

        return RouteInfo.builder()
                .optimizedPois(ordered)
                .legs(legs)
                .totalDistanceKm(Math.round(totalDist * 10.0) / 10.0)
                .totalDurationMinutes(totalDuration)
                .optimizationMethod("nearest_neighbor")
                .dataSource("mock")
                .build();
    }

    @Override
    public String providerName() {
        return "mock";
    }

    // ======================== 工具方法 ========================

    private List<PoiInfo> nearestNeighbor(List<PoiInfo> pois, double startLat, double startLng) {
        List<PoiInfo> unvisited = new ArrayList<>(pois);
        List<PoiInfo> ordered   = new ArrayList<>();
        double curLat = startLat, curLng = startLng;

        while (!unvisited.isEmpty()) {
            PoiInfo nearest = null;
            double minDist = Double.MAX_VALUE;
            for (PoiInfo p : unvisited) {
                double d = haversineKm(curLat, curLng, p.getLat(), p.getLng());
                if (d < minDist) {
                    minDist = d;
                    nearest = p;
                }
            }
            ordered.add(nearest);
            unvisited.remove(nearest);
            curLat = nearest.getLat();
            curLng = nearest.getLng();
        }
        return ordered;
    }

    /** Haversine 公式计算两点距离（km） */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private int travelMinutes(double distKm, String transportMode) {
        double speedKmH = switch (transportMode) {
            case "walking"  -> 4.0;
            case "driving"  -> 30.0;   // 城市行驶含堵车
            default         -> 20.0;   // transit（公交/地铁平均）
        };
        return Math.max(5, (int) Math.ceil(distKm / speedKmH * 60));
    }

    private String suggestTransport(double distKm, String transportMode) {
        if ("driving".equals(transportMode)) return distKm < 2 ? "步行" : "打车/自驾";
        if ("walking".equals(transportMode)) return "步行";
        if (distKm < 1.5) return "步行";
        if (distKm < 5)   return "公交/步行";
        return "地铁/公交";
    }

    private String buildInstruction(String from, String to, double distKm, int durMin) {
        return String.format("从%s前往%s，约%.1f公里，预计%d分钟", from, to, distKm, durMin);
    }

    private boolean matchesPreferences(PoiInfo poi, List<String> preferences) {
        if (preferences == null || preferences.isEmpty()) return true;
        List<String> tags = poi.getTags() == null ? List.of() : poi.getTags();
        for (String pref : preferences) {
            if ("family".equals(pref)      && tags.stream().anyMatch(t -> t.contains("亲子"))) return true;
            if ("photography".equals(pref) && tags.stream().anyMatch(t -> t.contains("摄影") || t.contains("网红"))) return true;
            if ("food".equals(pref)        && tags.stream().anyMatch(t -> t.contains("美食"))) return true;
            if ("culture".equals(pref)     && tags.stream().anyMatch(t -> t.contains("历史") || t.contains("文化"))) return true;
            if ("couple".equals(pref)      && tags.stream().anyMatch(t -> t.contains("情侣"))) return true;
        }
        return false;
    }

    // ======================== 静态 POI 构造辅助 ========================

    private static PoiInfo poi(String id, String name, String category, String address,
                                double lat, double lng, String hours, String price,
                                int durationMin, boolean indoor, List<String> tags, String desc) {
        return PoiInfo.builder()
                .id(id).name(name).category(category).address(address)
                .lat(lat).lng(lng).rating(4.5).openingHours(hours)
                .ticketPrice(price).visitDurationMinutes(durationMin)
                .indoorVenue(indoor).tags(tags).description(desc)
                .dataSource("mock").build();
    }

    /** 供 ItineraryAgentService 获取城市中心坐标 */
    public double[] getCityCenter(String cityCode) {
        return CITY_CENTER.getOrDefault(cityCode.toLowerCase(), new double[]{36.0671, 120.3826});
    }
}
