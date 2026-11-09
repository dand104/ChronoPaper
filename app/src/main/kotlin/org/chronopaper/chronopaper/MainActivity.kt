package org.chronopaper.chronopaper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.chronopaper.chronopaper.ui.navigation.ChronoNavHost
import org.chronopaper.chronopaper.ui.theme.ChronoPaperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isFromWallpaperSettings =
            intent.component?.className?.endsWith("WallpaperSettingsActivity") == true ||
                    !intent.hasCategory(Intent.CATEGORY_LAUNCHER)

        setContent {
            ChronoPaperTheme {
                ChronoNavHost(isFromWallpaperSettings = isFromWallpaperSettings)
            }
        }
    }
}