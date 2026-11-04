package com.ozorhaar.yggdrasil.launcher

import android.app.WallpaperManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Yggdrasil wallpapers bundled with the launcher (portrait, 1440x3120). */
enum class YggWallpaper(val label: String, val res: Int) {
    FJORD("Fjord", R.drawable.ygg_wall_fjord),
    WORLD_TREE("World Tree", R.drawable.ygg_wall_worldtree),
    IRON("Iron", R.drawable.ygg_wall_iron),
    GOLD_TREE("Gold tree", R.drawable.ygg_wall_goldtree),   // black + thin gold: made for the lock screen / AOD
}

enum class WallTarget(val label: String, val flags: Int) {
    HOME("Home", WallpaperManager.FLAG_SYSTEM),
    LOCK("Lock", WallpaperManager.FLAG_LOCK),
    BOTH("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** Sets a bundled wallpaper as a plain image (no live/layered effects). */
suspend fun Context.applyYggWallpaper(wall: YggWallpaper, target: WallTarget): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            resources.openRawResource(wall.res).use { input ->
                WallpaperManager.getInstance(this@applyYggWallpaper).setStream(input, null, true, target.flags)
            }
        }.isSuccess
    }
