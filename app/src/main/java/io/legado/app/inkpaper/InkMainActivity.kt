package io.legado.app.inkpaper

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import io.legado.app.R
import io.legado.app.inkpaper.design.InkPaperTheme
import io.legado.app.inkpaper.design.InkTheme
import io.legado.app.inkpaper.navigation.InkAppRoot
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.toastOnUi

class InkMainActivity : ComponentActivity() {

    private var exitTime: Long = 0L
    private var currentRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this) {
            if (currentRoute == "bookshelf") {
                val now = System.currentTimeMillis()
                if (now - exitTime > 2000L) {
                    toastOnUi(R.string.double_click_exit)
                    exitTime = now
                } else {
                    if (BaseReadAloudService.pause) {
                        finish()
                    } else {
                        moveTaskToBack(true)
                    }
                }
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }

        setContent {
            InkPaperTheme {
                val isDark = InkTheme.colors.isDark
                LaunchedEffect(isDark) {
                    val style = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(
                        statusBarStyle = style,
                        navigationBarStyle = style,
                    )
                }
                InkAppRoot(
                    onTabRouteChanged = { currentRoute = it },
                )
            }
        }
    }
}
