package com.tourism.rag.agent.provider;

import com.tourism.rag.dto.agent.FoodRecommendation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock 美食推荐提供者——内置多城市精选餐厅数据。
 */
@Slf4j
@Component
public class MockFoodProvider implements FoodProvider {

    // ======================== 城市美食数据 ========================

    private static final List<FoodRecommendation> QINGDAO_FOODS = List.of(
        food("船歌鱼水饺",     "海鲜水饺", 4.8, "60-100元/人",  "市南区闽江路6号",   "09:00-21:00", "lunch",   36.0620, 120.3720, "青岛特色海鲜水饺，馅料新鲜，皮薄汁多，必吃"),
        food("春和楼",         "鲁菜",     4.7, "80-150元/人",  "市南区中山路146号","10:00-21:30","lunch",   36.0636, 120.3253, "百年老字号，正宗鲁菜，糖醋鱼是招牌"),
        food("青岛海鲜排档",   "海鲜",     4.6, "100-200元/人", "市北区台东三路",   "17:00-01:00","dinner",  36.0871, 120.3580, "台东夜市，超新鲜海货，最地道的青岛海鲜体验"),
        food("劈柴院老字号",   "小吃",     4.5, "30-60元/人",   "市南区江宁路劈柴院","10:00-22:00","lunch",  36.0614, 120.3201, "百年小吃街，锅贴、烤肉、海蛎煎，应有尽有"),
        food("青岛啤酒屋",     "烧烤",     4.7, "80-120元/人",  "市北区登州路56号", "16:00-23:00","dinner",  36.0871, 120.3680, "在啤酒博物馆旁喝原浆啤酒配烧烤，超爽"),
        food("沧口早点铺",     "早餐",     4.6, "15-30元/人",   "李沧区沧口街道",   "06:00-10:00","breakfast",36.1735,120.4295,"青岛老城区早点，油条豆浆配咸菜，地道"),
        food("亚细亚海鲜",     "海鲜",     4.8, "150-300元/人", "崂山区松岭路217号","11:00-22:00","dinner",  36.1083, 120.4540, "崂山脚下，吃最新鲜的崂山海鲜")
    );

    private static final List<FoodRecommendation> BEIJING_FOODS = List.of(
        food("全聚德烤鸭(前门)","北京烤鸭", 4.5, "150-250元/人","前门大街30号",   "11:00-21:30","lunch",   39.8988, 116.3927, "百年烤鸭老字号，外脆里嫩，必须体验"),
        food("簋街麻辣小龙虾", "川湘菜",    4.6, "80-150元/人", "东城区东直门内大街","17:00-03:00","dinner", 39.9341, 116.4274, "北京深夜食堂，小龙虾+啤酒，夜生活首选"),
        food("老北京炸酱面馆", "北京菜",    4.7, "30-50元/人",  "东城区国子监街",  "10:00-21:00","lunch",  39.9468, 116.4086, "手擀面，酱香浓郁，配六种菜码，最正宗"),
        food("爆肚冯(护国寺)","北京小吃",   4.6, "40-80元/人",  "西城区护国寺街",  "11:00-21:00","lunch",  39.9435, 116.3808, "正宗爆肚，羊肚入口鲜嫩，蘸麻酱绝了"),
        food("南锣鼓巷小食",  "街头小吃",   4.5, "20-50元/人",  "东城区南锣鼓巷",  "10:00-22:00","snack",  39.9330, 116.4070, "驴打滚、豆汁、焦圈，北京传统小吃聚集地"),
        food("东来顺涮羊肉",  "火锅",       4.7, "100-180元/人","东城区王府井大街",  "11:00-22:00","dinner", 39.9144, 116.4082, "铜锅涮羊肉，百年老字号，内蒙古精选羊肉")
    );

    private static final List<FoodRecommendation> SHANGHAI_FOODS = List.of(
        food("南翔馒头店(城隍庙)", "上海小笼", 4.7, "40-80元/人",  "黄浦区豫园路85号","07:00-20:30","breakfast",31.2269,121.4918, "百年小笼包，皮薄汁多，城隍庙排队必吃"),
        food("老吉士酒家",        "本帮菜",   4.8, "80-150元/人", "徐汇区天平路41号","11:00-21:30","lunch",    31.2167,121.4598, "红烧肉、腌笃鲜，最正宗的上海家常菜"),
        food("外白渡桥旁大排档",  "海鲜",     4.5, "60-120元/人", "黄浦区外滩附近", "17:00-23:00","dinner",   31.2416,121.4886, "看外滩夜景，吃大闸蟹，上海特色体验"),
        food("沈大成糕团",        "点心",     4.6, "15-30元/人",  "黄浦区南京东路636号","06:30-19:00","breakfast",31.2368,121.4801,"青团、赤豆糕、糯米团，老上海传统早点"),
        food("Din Tai Fung鼎泰丰","台式小笼", 4.8, "100-180元/人","静安区南京西路1038号","10:00-22:00","lunch",31.2253,121.4555, "世界级小笼包，口感细腻，服务一流")
    );

    private static final List<FoodRecommendation> XIAN_FOODS = List.of(
        food("老孙家泡馍",  "羊肉泡馍", 4.7, "30-60元/人",  "莲湖区西羊市街",  "07:00-21:00","lunch",   34.2685, 108.9452, "西安必吃，羊肉汤浓郁，掰馍是种乐趣"),
        food("賈三灌汤包",  "灌汤包",   4.8, "25-50元/人",  "莲湖区北院门街",  "08:00-21:00","breakfast",34.2701,108.9380, "回民街内，皮薄汤鲜，一口一个烫嘴的幸福"),
        food("魏家凉皮",    "凉皮",     4.6, "15-25元/人",  "碑林区东大街附近","全天",        "snack",   34.2594, 108.9484, "正宗秦镇凉皮，酸辣爽口，西安最佳街头小吃"),
        food("老字号肉夹馍","肉夹馍",   4.7, "10-20元/人",  "莲湖区回民街",    "09:00-22:00","snack",   34.2667, 108.9410, "正宗腊汁肉，馍酥脆，肉香浓，西安灵魂美食"),
        food("德发长饺子宴","陕西饺子", 4.6, "80-150元/人", "碑林区解放路路口", "11:00-21:00","dinner",  34.2564, 108.9511, "108种饺子，花样繁多，陕西美食文化的缩影")
    );

    private static final List<FoodRecommendation> CHENGDU_FOODS = List.of(
        food("陈麻婆豆腐",    "川菜",     4.7, "40-80元/人",  "青羊区西玉龙街197号","10:30-21:30","lunch",  30.6684,104.0597, "麻婆豆腐发源地，嫩滑鲜香麻辣，真正的成都味"),
        food("龙抄手(春熙路)","抄手/小吃",4.6, "30-60元/人",  "锦江区春熙路步行街","08:00-22:00","lunch",  30.6541,104.0816, "成都名小吃，鲜肉抄手皮薄馅足，汤底香浓"),
        food("皇城坝串串香",  "串串",     4.8, "60-100元/人", "青羊区宽窄巷子附近","17:00-23:00","dinner", 30.6694,104.0563, "成都特色串串，麻辣鲜香，配冰粉是绝配"),
        food("钟水饺",        "水饺",     4.5, "20-40元/人",  "锦江区荔枝巷19号", "08:00-21:00","breakfast",30.6617,104.0757,"百年老字号，红油抄手和甜水面最绝"),
        food("成都火锅(蜀九香)","火锅",   4.8, "80-150元/人", "锦江区建设路71号", "11:00-24:00","dinner", 30.6734,104.1021, "成都正宗火锅，牛油锅底麻辣鲜香，配毛肚绝了")
    );

    private static final Map<String, List<FoodRecommendation>> CITY_FOODS = Map.of(
            "qingdao", QINGDAO_FOODS,
            "beijing",  BEIJING_FOODS,
            "shanghai", SHANGHAI_FOODS,
            "xian",     XIAN_FOODS,
            "chengdu",  CHENGDU_FOODS
    );

    // ======================== 接口实现 ========================

    @Override
    public List<FoodRecommendation> recommendFood(String cityCode, String cityName,
                                                   double lat, double lng,
                                                   String mealType, List<String> preferences,
                                                   double minRating, int maxResults) {
        log.info("[MockFood] city={}, mealType={}, minRating={}", cityCode, mealType, minRating);

        List<FoodRecommendation> cityFoods = CITY_FOODS.getOrDefault(
                cityCode.toLowerCase(), QINGDAO_FOODS);

        // 计算与参考坐标的距离，填入 distanceKm
        List<FoodRecommendation> enriched = cityFoods.stream()
                .map(f -> {
                    double dist = MockMapProvider.haversineKm(lat, lng, f.getLat(), f.getLng());
                    return FoodRecommendation.builder()
                            .name(f.getName()).category(f.getCategory())
                            .rating(f.getRating()).priceRange(f.getPriceRange())
                            .distanceKm(Math.round(dist * 10.0) / 10.0)
                            .address(f.getAddress()).businessStatus(f.getBusinessStatus())
                            .openingHours(f.getOpeningHours()).phone(f.getPhone())
                            .recommendReason(f.getRecommendReason())
                            .mealType(f.getMealType())
                            .lat(f.getLat()).lng(f.getLng()).dataSource("mock").build();
                })
                .filter(f -> f.getRating() >= minRating)
                .sorted(Comparator.comparingDouble(FoodRecommendation::getDistanceKm))
                .limit(maxResults)
                .collect(Collectors.toList());

        return enriched;
    }

    @Override
    public String providerName() {
        return "mock";
    }

    // ======================== 构造辅助 ========================

    private static FoodRecommendation food(String name, String category, double rating,
                                            String price, String address, String hours,
                                            String mealType, double lat, double lng, String reason) {
        return FoodRecommendation.builder()
                .name(name).category(category).rating(rating)
                .priceRange(price).address(address)
                .businessStatus("营业中").openingHours(hours)
                .mealType(mealType).lat(lat).lng(lng)
                .recommendReason(reason).dataSource("mock").build();
    }
}
