package one.yufz.hmspush.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.hmspush.hook.hms.NPatchFallbackHook

class NPatchEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.xiaomi.xmsf") return
        runCatching { NPatchFallbackHook.hook(lpparam) }
    }
}
