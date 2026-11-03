package com.ozorhaar.yggdrasil.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ozorhaar.yggdrasil.launcher.ui.LauncherRoot
import com.ozorhaar.yggdrasil.launcher.ui.YggTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { YggTheme { LauncherRoot() } }
    }

    override fun onResume() {
        super.onResume()
        ygg.repo.reload()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) ygg.homePressed.tryEmit(Unit)
    }
}
