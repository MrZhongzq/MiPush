package one.yufz.hmspush.hook.fakedevice

import android.app.AndroidAppHelper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import one.yufz.hmspush.common.HMS_PACKAGE_NAME
import one.yufz.hmspush.hook.XLog
import one.yufz.xposed.findClass

object FakeXmsfPackage {
    private const val TAG = "FakeXmsfPackage"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classApplicationPackageManager =
            lpparam.classLoader.findClass("android.app.ApplicationPackageManager")

        hookGetApplicationInfo(classApplicationPackageManager)
        hookGetPackageInfo(classApplicationPackageManager)
        hookCheckSignatures(classApplicationPackageManager)
        hookEnabledState(classApplicationPackageManager)
    }

    private fun hookGetApplicationInfo(classApplicationPackageManager: Class<*>) {
        XposedBridge.hookAllMethods(classApplicationPackageManager, "getApplicationInfo", afterHook {
            if (matchesFrameworkPackage(args.firstOrNull())) {
                (result as? ApplicationInfo)?.let(::patchApplicationInfo)
            }
        })
    }

    private fun hookGetPackageInfo(classApplicationPackageManager: Class<*>) {
        XposedBridge.hookAllMethods(classApplicationPackageManager, "getPackageInfo", afterHook {
            if (matchesFrameworkPackage(args.firstOrNull())) {
                (result as? PackageInfo)?.let(::patchPackageInfo)
            }
        })
    }

    private fun hookCheckSignatures(classApplicationPackageManager: Class<*>) {
        XposedBridge.hookAllMethods(classApplicationPackageManager, "checkSignatures", beforeHook {
            when {
                args.size >= 2 && matchesFrameworkPackage(args[0]) -> result = PackageManager.SIGNATURE_MATCH
                args.size >= 2 && matchesFrameworkPackage(args[1]) -> result = PackageManager.SIGNATURE_MATCH
                args.size >= 2 && matchesFrameworkUid(args[0]) -> result = PackageManager.SIGNATURE_MATCH
                args.size >= 2 && matchesFrameworkUid(args[1]) -> result = PackageManager.SIGNATURE_MATCH
            }
        })
    }

    private fun hookEnabledState(classApplicationPackageManager: Class<*>) {
        XposedBridge.hookAllMethods(classApplicationPackageManager, "getApplicationEnabledSetting", beforeHook {
            if (matchesFrameworkPackage(args.firstOrNull())) {
                result = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            }
        })
    }

    private fun patchPackageInfo(info: PackageInfo) {
        info.applicationInfo?.let(::patchApplicationInfo)
    }

    private fun patchApplicationInfo(info: ApplicationInfo) {
        info.flags = info.flags or ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        info.enabled = true
        patchPrivateFlags(info)
    }

    private fun patchPrivateFlags(info: ApplicationInfo) {
        setPrivateFlag(info, "PRIVATE_FLAG_PRIVILEGED")
        setPrivateFlag(info, "PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY")
    }

    private fun setPrivateFlag(info: ApplicationInfo, constantName: String) {
        try {
            val constant = ApplicationInfo::class.java.getDeclaredField(constantName).getInt(null)
            val privateFlagsField = ApplicationInfo::class.java.getDeclaredField("privateFlags")
            privateFlagsField.isAccessible = true
            val current = privateFlagsField.getInt(info)
            privateFlagsField.setInt(info, current or constant)
        } catch (_: Throwable) {
            // Hidden/private flags vary by ROM and API level. FLAG_SYSTEM is the main signal.
        }
    }

    private fun matchesFrameworkPackage(arg: Any?): Boolean {
        return arg is String && arg == HMS_PACKAGE_NAME
    }

    private fun matchesFrameworkUid(arg: Any?): Boolean {
        if (arg !is Int) {
            return false
        }
        val app = AndroidAppHelper.currentApplication() ?: return false
        val frameworkUid = try {
            app.packageManager.getApplicationInfo(HMS_PACKAGE_NAME, 0).uid
        } catch (t: Throwable) {
            XLog.d(TAG, "matchesFrameworkUid: failed to resolve uid: ${t.message}")
            return false
        }
        return arg == frameworkUid
    }

    private fun beforeHook(block: XC_MethodHook.MethodHookParam.() -> Unit): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.block()
            }
        }
    }

    private fun afterHook(block: XC_MethodHook.MethodHookParam.() -> Unit): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                param.block()
            }
        }
    }
}
