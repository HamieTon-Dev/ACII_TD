package com.cyopstd.game

import android.app.Application
import com.cyopstd.game.i18n.Tr

/**
 * Exists for one job: pick the game's language before anything reads text.
 * Enums and objects read their lines once, when first touched, so this has to
 * run before the first screen does.
 */
class CyOpsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Tr.init(this)
    }
}
