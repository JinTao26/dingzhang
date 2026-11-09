package com.dingzhang.app.classify

/**
 * 内置商户 / 场景词典。
 *
 * 设计要点：
 * 1. 匹配时按词长降序，长词优先。这样「美团买药」会赢过「美团」，「京东健康」会赢过「京东」，
 *    不需要维护严格的顺序。
 * 2. 词典只覆盖「有明确归属」的商户；剩下的交给用户教。词典再全也覆盖不了一个人的真实生活。
 * 3. 全部条目本地匹配，不发网络请求。
 */
object MerchantDict {

    data class Entry(val keyword: String, val categoryId: String, val kind: String = "expense")

    private fun e(cat: String, vararg words: String) = words.map { Entry(it.lowercase(), cat) }

    private val expense: List<Entry> = buildList {
        addAll(e("food",
            "美团外卖", "饿了么", "肯德基", "KFC", "麦当劳", "必胜客", "汉堡王", "德克士", "华莱士",
            "沙县", "兰州拉面", "黄焖鸡", "麻辣烫", "冒菜", "火锅", "海底捞", "呷哺", "烧烤", "烤肉",
            "自助餐", "快餐", "便当", "盒饭", "盖饭", "米线", "螺蛳粉", "酸辣粉", "饺子", "馄饨",
            "包子", "煎饼", "面馆", "小炒", "川菜", "湘菜", "粤菜", "食堂", "餐厅", "饭店", "小吃",
            "夜宵", "早餐", "早点", "午餐", "晚餐", "零食", "水果", "生鲜", "菜市场", "蔬菜",
            "面包", "蛋糕", "卤味", "熟食", "烧腊", "麻辣香锅", "寿司", "披萨", "日料", "西餐",
            // 平台裸词放最后：字长短，会被「美团外卖」「美团买菜」「美团单车」这类长词盖过，
            // 只有真拿不到细分类目时才落到这里
            "美团", "饿了么外卖", "美团买菜", "叮咚买菜", "朴朴", "多多买菜", "淘菜菜",
            "生鲜到家", "买菜", "菜篮子", "菜市场摊位"
        ))
        addAll(e("drink",
            "瑞幸", "luckin", "星巴克", "库迪", "蜜雪冰城", "喜茶", "奈雪的茶", "茶颜悦色",
            "霸王茶姬", "古茗", "沪上阿姨", "一点点", "益禾堂", "书亦烧仙草", "咖啡", "奶茶",
            "果汁", "饮料", "可乐", "矿泉水", "纯净水", "气泡水", "精酿"
        ))
        addAll(e("transport",
            "滴滴", "高德打车", "T3出行", "曹操出行", "首汽约车", "花小猪", "哈啰", "青桔",
            "美团单车", "共享单车", "地铁", "公交", "轻轨", "高铁", "12306", "火车票", "机票",
            "航空", "加油", "中石化", "中石油", "充电桩", "停车", "停车费", "洗车", "高速通行",
            "过路费", "ETC", "打车", "出租车", "网约车", "车费", "顺风车", "轮渡", "交通卡", "一卡通"
        ))
        addAll(e("shopping",
            "淘宝", "天猫", "京东", "拼多多", "唯品会", "抖音商城", "快手小店", "闲鱼", "得物",
            "网易严选", "山姆", "盒马", "永辉", "沃尔玛", "大润发", "华润万家", "物美", "超市",
            "便利店", "全家", "7-11", "711", "罗森", "美宜佳", "便利蜂", "优衣库", "zara",
            "无印良品", "名创优品", "安踏", "李宁", "耐克", "阿迪达斯", "服饰", "衣服", "鞋",
            "包包", "快递", "顺丰", "菜鸟", "日用品", "纸巾", "洗衣液", "收纳", "锅具", "床品",
            // 电商子品牌：这些词比平台裸词长，会先命中，避免「京东到家」被「京东」抢走
            "京东到家", "京东超市", "京东自营", "天猫超市", "淘宝闪购", "淘宝特价", "聚划算",
            "唯品会特卖", "得物app", "识货", "小红书商城", "1688", "阿里巴巴批发", "抖音超市"
        ))
        addAll(e("home",
            "房租", "租金", "押金", "物业", "物业费", "水费", "电费", "燃气", "天然气", "宽带",
            "网费", "话费", "中国移动", "中国联通", "中国电信", "电话费", "流量充值", "家政",
            "保洁", "维修", "装修", "宜家", "家具", "床垫", "人体工学", "除湿机", "加湿器",
            "空调", "洗衣机", "冰箱", "中介费", "搬家"
        ))
        addAll(e("fun",
            "电影", "影城", "万达影城", "CGV", "猫眼", "淘票票", "演唱会", "大麦", "门票",
            "景区", "旅游", "酒店", "民宿", "携程", "飞猪", "同程", "去哪儿", "KTV", "剧本杀",
            "密室", "桌游", "网吧", "游戏", "手游", "原神", "鸣潮", "米哈游", "库洛",
            "腾讯游戏", "网易游戏", "steam", "switch", "psn", "爱奇艺", "腾讯视频", "优酷",
            "芒果TV", "哔哩哔哩", "b站大会员", "网易云音乐", "QQ音乐", "手办", "盲盒", "潮玩",
            "演出", "展览", "博物馆", "酒吧", "livehouse", "美团酒店", "美团门票", "携程旅行",
            "抖音", "快手", "冲浪", "剧本杀店"
        ))
        addAll(e("health",
            "医院", "门诊", "挂号", "药房", "药店", "大药房", "药品", "处方", "体检", "口腔",
            "牙科", "洗牙", "正畸", "眼镜", "隐形眼镜", "疫苗", "理疗", "康复", "推拿", "按摩",
            "中医", "针灸", "心理", "光子嫩肤", "医美", "皮肤科", "美团买药", "京东健康",
            "阿里健康", "医保", "润肤", "药膏"
        ))
        addAll(e("study",
            "学费", "培训", "课程", "网课", "得到", "知识星球", "微信读书", "书店", "图书",
            "教材", "文具", "打印", "复印", "论文", "查重", "考试", "报名费", "证书", "雅思",
            "托福", "考研", "学术会议", "jetbrains", "极客时间", "电子书"
        ))
        addAll(e("social",
            "红包", "随礼", "份子钱", "礼物", "送礼", "生日", "婚礼", "孝敬", "给爸妈", "捐款",
            "慰问"
        ))
        addAll(e("pet",
            "宠物", "猫粮", "狗粮", "猫砂", "宠物医院", "驱虫", "猫条", "罐头"
        ))
        addAll(e("digital",
            "手机", "电脑", "显卡", "内存条", "固态硬盘", "硬盘", "显示器", "键盘", "鼠标",
            "耳机", "相机", "镜头", "充电器", "数据线", "移动电源", "apple", "小米", "华为",
            "联想", "戴尔", "罗技", "大疆", "装机", "路由器", "nas", "树莓派", "开发板",
            "fpga", "示波器", "万用表"
        ))
    }

    // 收入也分类了：工资 / 奖金 / 理财 / 报销 / 红包 / 其他收入。
    // 「退款」故意不在这里 —— 退款是 REFUND，要从支出里减，不能算成收入。
    private val income: List<Entry> = buildList {
        addAll(e("salary", "工资", "薪水", "月薪", "发薪", "代发工资", "劳务派遣").map { it.copy(kind = "income") })
        addAll(e("bonus", "年终奖", "奖金", "绩效", "提成", "佣金", "项目奖").map { it.copy(kind = "income") })
        addAll(e("invest", "理财收益", "基金收益", "余额宝收益", "利息", "分红", "股息", "国债").map { it.copy(kind = "income") })
        addAll(e("reimburse", "报销", "差旅补贴", "退税款", "发票报销").map { it.copy(kind = "income") })
        addAll(e("gift", "收红包", "红包", "礼金", "压岁钱", "返现", "中奖").map { it.copy(kind = "income") })
        addAll(e("income", "收款成功", "收款", "收到", "转入", "兼职", "外快", "稿费", "结款", "劳务费").map { it.copy(kind = "income") })
    }

    /** 不计收支的划转类。 */
    private val transfer: List<Entry> = e("other",
        "转账", "提现", "零钱通", "余额宝", "信用卡还款", "花呗还款", "白条还款", "还钱",
        "内部划转", "充值到零钱", "银行卡转入"
    ).map { it.copy(kind = "transfer") }


    /**
     * 通用后缀：店名里带这些词，基本能定分类。
     * 词典再全也覆盖不了街边小店（「加贝购物超市」「老李面馆」「康泰药房」），
     * 但这类名字的后缀很稳定，用它兜底能明显减少「其他」。
     * 长词优先，避免「宠物医院」被「医院」抢走。
     */
    private val suffixHints: List<Pair<String, String>> = listOf(
        "超市" to "shopping", "生活超市" to "shopping", "购物广场" to "shopping",
        "便利店" to "shopping", "百货" to "shopping", "商行" to "shopping",
        "商贸" to "shopping", "批发" to "shopping", "专卖店" to "shopping",
        "旗舰店" to "shopping", "购物中心" to "shopping", "商城" to "shopping",
        "市场" to "shopping", "菜市场" to "food", "农贸" to "food",
        "食品" to "food", "零食" to "food", "生鲜" to "food", "水果" to "food",
        "餐厅" to "food", "餐饮" to "food", "饭店" to "food", "小吃" to "food",
        "面馆" to "food", "米粉" to "food", "米线" to "food", "快餐" to "food",
        "食堂" to "food", "烧烤" to "food", "火锅" to "food", "烘焙" to "food",
        "面包" to "food", "蛋糕" to "food", "甜品" to "food", "饼屋" to "food",
        "卤味" to "food", "熟食" to "food", "烧腊" to "food", "饺子" to "food",
        "奶茶" to "drink", "咖啡" to "drink", "茶饮" to "drink", "饮品" to "drink",
        "茶社" to "drink", "酒馆" to "drink", "酒吧" to "fun",
        "药房" to "health", "药店" to "health", "大药房" to "health",
        "诊所" to "health", "医院" to "health", "口腔" to "health",
        "眼科" to "health", "中医" to "health", "体检" to "health",
        "美容" to "health", "药妆" to "health",
        "宠物" to "pet", "宠物医院" to "pet", "宠物店" to "pet",
        "书店" to "study", "文具" to "study", "图文" to "study",
        "印刷" to "study", "培训" to "study", "教育" to "study",
        "学校" to "study", "自习室" to "study", "考研" to "study",
        "影城" to "fun", "影院" to "fun", "网吧" to "fun", "网咖" to "fun",
        "电竞" to "fun", "剧本" to "fun", "密室" to "fun", "桌游" to "fun",
        "健身" to "fun", "游泳" to "fun", "球馆" to "fun",
        "加油站" to "transport", "油站" to "transport", "充电站" to "transport",
        "停车场" to "transport", "汽修" to "transport", "洗车" to "transport",
        "汽车服务" to "transport", "轮胎" to "transport", "驾校" to "transport",
        "五金" to "home", "建材" to "home", "家居" to "home", "家具" to "home",
        "装饰" to "home", "装修" to "home", "电器" to "digital", "数码" to "digital",
        "通讯" to "digital", "手机店" to "digital", "电脑城" to "digital",
        "眼镜" to "health", "珠宝" to "shopping", "钟表" to "shopping",
        "服饰" to "shopping", "鞋业" to "shopping", "箱包" to "shopping",
        "洗衣" to "home", "干洗" to "home", "快递" to "shopping", "驿站" to "shopping",
        "物流" to "shopping", "理发" to "health", "美发" to "health",
        "足浴" to "fun", "洗浴" to "fun", "按摩" to "health",
        "烟酒" to "shopping", "酒业" to "shopping", "茶叶" to "shopping",
        "粮油" to "food", "副食" to "food", "肉店" to "food", "海鲜" to "food",
        "水产" to "food", "冷饮" to "drink", "冰淇淋" to "drink"
    ).sortedByDescending { it.first.length }

    /** 长度降序，长的先赢。 */
    private val ordered: List<Entry> = (transfer + income + expense).sortedByDescending { it.keyword.length }

    fun lookup(text: String): Entry? {
        val hay = text.lowercase()
        for (entry in ordered) {
            if (hay.contains(entry.keyword)) return entry
        }
        // 词典里没有的小店，看名字后缀
        for ((w, cat) in suffixHints) {
            if (hay.contains(w)) return Entry(w, cat)
        }
        return null
    }

    /** 规则页要展示的「词典概览」 */
    fun previewByCategory(categoryId: String, limit: Int = 8): List<String> =
        (expense + income + transfer).filter { it.categoryId == categoryId }.take(limit).map { it.keyword }

    fun totalSize(): Int = ordered.size
}
