package one.yufz.hmspush.hook.hms

import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.*

class HookHMS {
    companion object {
        private const val TAG = "HookHMS"
    }

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        runHook("PushSignWatcher") { PushSignWatcher.watch() }
        runHook("HookLegacyTokenRequest") { HookLegacyTokenRequest.hook(lpparam.classLoader) }
        runHook("RuntimeKitHook") { RuntimeKitHook.hook(lpparam.classLoader) }

        if (HookPushNC.canHook(lpparam.classLoader)) {
            runHook("HookPushNC") { HookPushNC.hook(lpparam.classLoader) }
        }
    }

    private fun runHook(name: String, hook: () -> Unit) {
        try {
            hook()
        } catch (t: Throwable) {
            XLog.e(TAG, "$name failed: ${t.message}", t)
        }
    }
}
