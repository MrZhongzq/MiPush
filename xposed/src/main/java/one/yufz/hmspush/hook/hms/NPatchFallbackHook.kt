package one.yufz.hmspush.hook.hms

import android.content.ComponentName
import android.content.Intent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object NPatchFallbackHook {
    private const val NPATCH_PACKAGE = "org.lsposed.npatch"
    private const val RECEIVER_CLASS = "org.lsposed.npatch.manager.MiPushFallbackReceiver"
    private const val ACTION_POST_FALLBACK = "org.lsposed.npatch.action.POST_MIPUSH_FALLBACK"
    @Volatile
    private var npatchPresent = true

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val helperClass = XposedHelpers.findClassIfExists(
            "com.xiaomi.push.service.MyMIPushNotificationHelper",
            lpparam.classLoader
        ) ?: return

        hookCandidate(helperClass, "notifyPushMessage")
        hookCandidate(helperClass, "notifyPushMessageOld")
        hookCandidate(helperClass, "notifyMiPushMessage")
    }

    private fun hookCandidate(clazz: Class<*>, methodName: String) {
        try {
            XposedBridge.hookAllMethods(clazz, methodName, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context = extractContext(param.thisObject, param.args) ?: return
                    if (!ensureNPatchPresent(context)) return
                    val targetPackage = guessString(param.thisObject, param.args, "packageName", "targetPkg", "pkg")
                        ?: return
                    val title = guessString(param.thisObject, param.args, "title", "ticker", "notifyTitle")
                        ?: "New message"
                    val text = guessString(param.thisObject, param.args, "description", "content", "notifySummary")
                        ?: "Push delivery fallback"
                    val appLabel = runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(targetPackage, 0)
                        ).toString()
                    }.getOrNull() ?: targetPackage

                    val intent = Intent(ACTION_POST_FALLBACK).apply {
                        component = ComponentName(NPATCH_PACKAGE, RECEIVER_CLASS)
                        addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        putExtra("target_package", targetPackage)
                        putExtra("app_label", appLabel)
                        putExtra("title", title)
                        putExtra("text", text)
                    }
                    runCatching { context.sendBroadcast(intent) }
                }
            })
        } catch (_: Throwable) {
        }
    }

    private fun ensureNPatchPresent(context: android.content.Context): Boolean {
        if (!npatchPresent) return false
        npatchPresent = runCatching {
            context.packageManager.getPackageInfo(NPATCH_PACKAGE, 0)
            true
        }.getOrElse { false }
        return npatchPresent
    }

    private fun extractContext(thisObject: Any?, args: Array<Any?>): android.content.Context? {
        args.forEach { arg ->
            if (arg is android.content.Context) return arg
        }
        if (thisObject != null) {
            listOf("mContext", "context").forEach { field ->
                runCatching {
                    val value = XposedHelpers.getObjectField(thisObject, field)
                    if (value is android.content.Context) return value
                }
            }
        }
        return null
    }

    private fun guessString(thisObject: Any?, args: Array<Any?>, vararg candidates: String): String? {
        args.forEach { arg ->
            if (arg == null) return@forEach
            candidates.forEach { field ->
                runCatching {
                    val value = XposedHelpers.getObjectField(arg, field) as? String
                    if (!value.isNullOrBlank()) return value
                }
            }
            if (arg is String && arg.contains('.')) {
                return arg
            }
        }
        if (thisObject != null) {
            candidates.forEach { field ->
                runCatching {
                    val value = XposedHelpers.getObjectField(thisObject, field) as? String
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return null
    }
}
