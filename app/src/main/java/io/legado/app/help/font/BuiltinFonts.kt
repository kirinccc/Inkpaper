package io.legado.app.help.font

import android.content.Context
import android.graphics.Typeface

/**
 * 随 APK 打包的内置字体, 字体文件位于 assets/font/ 目录
 *
 * 存储值以 [PREFIX] 开头, 用于与磁盘路径 / content uri 区分,
 * 由 [io.legado.app.ui.book.read.page.provider.ChapterProvider] 解析为 Typeface
 */
object BuiltinFonts {

    const val PREFIX = "assets:"

    data class Item(val name: String, val value: String)

    val items: List<Item> = listOf(
        Item("霞鹜文楷", "${PREFIX}font/LXGWWenKai-Regular.ttf"),
    )

    fun isBuiltin(value: String): Boolean = value.startsWith(PREFIX)

    fun displayName(value: String): String? = items.firstOrNull { it.value == value }?.name

    fun createTypeface(context: Context, value: String): Typeface? {
        val assetPath = value.removePrefix(PREFIX)
        return runCatching { Typeface.createFromAsset(context.assets, assetPath) }.getOrNull()
    }

}
