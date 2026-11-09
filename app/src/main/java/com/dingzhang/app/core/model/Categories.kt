package com.dingzhang.app.core.model

/**
 * 分类。id 一旦落库就不能改，名称可以改。
 * color 是 ARGB Int，icon 是 UI 层图标映射的 key。
 */
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val color: Int
)

object Categories {

    val food = Category("food", "餐饮", "food", 0xFFFF9EB5.toInt())
    val drink = Category("drink", "饮品", "drink", 0xFFFFB98A.toInt())
    val transport = Category("transport", "交通", "transport", 0xFF9BD1FF.toInt())
    val shopping = Category("shopping", "购物", "shopping", 0xFFB9A6FF.toInt())
    val home = Category("home", "居住", "home", 0xFFFFD86F.toInt())
    val funMe = Category("fun", "娱乐", "fun", 0xFFFFA6E1.toInt())
    val health = Category("health", "医疗", "health", 0xFF8FE3C8.toInt())
    val study = Category("study", "学习", "study", 0xFFC6E67A.toInt())
    val social = Category("social", "人情", "social", 0xFFFF8F8F.toInt())
    val pet = Category("pet", "宠物", "pet", 0xFFE2B98F.toInt())
    val digital = Category("digital", "数码", "digital", 0xFF93D9E8.toInt())
    val other = Category("other", "其他", "other", 0xFFC9C4D2.toInt())
    val income = Category("income", "收入", "income", 0xFF7DE2C3.toInt())

    /** 支出分类，同时决定记账页的图标网格顺序。 */
    val expenseAll = listOf(
        food, drink, transport, shopping, home, funMe,
        health, study, social, pet, digital, other
    )

    val all = expenseAll + income

    private val byId = all.associateBy { it.id }

    fun of(id: String?): Category = byId[id] ?: other

    fun isExpenseCategory(id: String): Boolean = id != income.id
}
