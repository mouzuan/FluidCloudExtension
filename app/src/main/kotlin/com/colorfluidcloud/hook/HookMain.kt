package com.colorfluidcloud.hook

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.graphics.RectF
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.lang.reflect.Method
import java.util.Properties


/**
 * Xposed 模块主入口类
 * 当目标应用（系统UI或本模块）加载时自动调用，注入自定义钩子
 */
class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {

        // ==================== 1. 模块自身激活状态检测 ====================
        // 如果当前加载的包是本模块自身，则钩住 MainActivity 的 isModuleActivated 方法，
        // 使其始终返回 true，用于在界面中显示“模块已激活”。
        if (lpparam.packageName == "com.colorfluidcloud.hook") {
            XposedHelpers.findAndHookMethod(
                "com.colorfluidcloud.hook.MainActivity",
                lpparam.classLoader,
                "isModuleActivated",
                XC_MethodReplacement.returnConstant(true)
            )
        }

        // ==================== 2. 流体云位置修改（针对系统UI） ====================
        // 目标包为系统UI，修改流体云（孔洞）的显示位置（左侧和右侧边距）
        if (lpparam.packageName == "com.android.systemui") {
            // 目标类：SeedlingPluginManager 的内部类 holeRectListener$1
            val targetClass = "com.oplus.systemui.statusbar.seeding.SeedlingPluginManager\$holeRectListener\$1"
            try {
                XposedHelpers.findAndHookMethod(
                    targetClass,
                    lpparam.classLoader,
                    "onRectChanged",
                    RectF::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            super.beforeHookedMethod(param)
                            val rect = param.args[0] as? RectF ?: return

                            // 默认边距值（若配置文件读取失败则使用）
                            var left = 540.0f
                            var right = 540.0f

                            // 获取 Application 实例，用于访问文件系统
                            var application: Application? = null
                            try {
                                val activityThreadClass = Class.forName("android.app.ActivityThread")
                                val currentApplicationMethod: Method =
                                    activityThreadClass.getMethod("currentApplication")
                                application = currentApplicationMethod.invoke(null) as? Application
                            } catch (e: Exception) {
                                // 获取失败时忽略，后续直接使用默认值
                            }

                            // 从外部配置文件读取用户自定义的 left/right 值
                            application?.let {
                                try {
                                    val configFile = File(HOLE_CONFIG_PATH)
                                    if (configFile.exists() && configFile.canRead()) {
                                        var br: BufferedReader? = null
                                        try {
                                            br = BufferedReader(FileReader(configFile))
                                            var line: String?
                                            while (br.readLine().also { line = it } != null) {
                                                val trimLine = line?.trim() ?: continue
                                                try {
                                                    if (trimLine.startsWith("left=")) {
                                                        left = trimLine.substring(5).toFloat()
                                                    } else if (trimLine.startsWith("right=")) {
                                                        right = trimLine.substring(6).toFloat()
                                                    }
                                                } catch (e: NumberFormatException) {
                                                    // 忽略格式错误的行
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // 忽略读取异常
                                        } finally {
                                            try {
                                                br?.close()
                                            } catch (e: Exception) {
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                }
                            }

                            // 将计算后的边距值写回矩形参数，实现位置调整
                            rect.left = left
                            rect.right = right
                        }
                    }
                )
            } catch (e: Exception) {
                // 目标类可能不存在于当前系统版本，静默失败
            }
        }

        // ==================== 3. 音乐封面形状修改（针对系统UI） ====================
        // 钩住 Resources.getString 方法，根据配置文件中的 ltyfmxz 属性，
        // 动态修改特定字符串资源的返回值（控制流体云音乐封面的形状：圆形/圆角矩形）
        if (lpparam.packageName == "com.android.systemui") {
            XposedHelpers.findAndHookMethod(
                "android.content.res.Resources",
                lpparam.classLoader,
                "getString",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        super.afterHookedMethod(param)
                        try {
                            val res = param.thisObject as? Resources ?: return
                            val resId = param.args[0] as? Int ?: return
                            val name = res.getResourceEntryName(resId)

                            // 读取配置文件中的 ltyfmxz 属性
                            val configFile = File(HOLE_CONFIG_PATH)
                            val props = Properties()
                            if (configFile.exists()) {
                                FileInputStream(configFile).use { fis ->
                                    props.load(fis)
                                }
                                val value = props.getProperty("ltyfmxz")

                                when (value) {
                                    "1" -> {
                                        // 若资源名为形状定义字符串，将结果改为 "round-rectangle"（圆角矩形）
                                        if (name == "shapeArtworkCircle") {
                                            param.result = "round-rectangle"
                                        }
                                    }
                                    "0" -> {
                                        // 若资源名为形状定义字符串，将结果改为 "circle"（圆形）
                                        if (name == "shapeArtworkCircle") {
                                            param.result = "circle"
                                        }
                                    }
                                    // 其他值或无配置时保持原样
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略所有异常，保证原方法正常执行
                        }
                    }
                }
            )
        }

        // ==================== 4. 静音磁贴可用性（原三段式按键磁贴） ====================
        // 控制快速设置面板中静音模式磁贴（RingerModeTile）的显示与隐藏。
        // 根据配置文件中的 jyct 属性（1=强制可用，0=强制不可用）覆盖原始 isAvailable 返回值。
        if (lpparam.packageName == "com.android.systemui") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.oplus.systemui.qs.tiles.RingerModeTile",
                    lpparam.classLoader,
                    "isAvailable",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 读取配置文件中的可用性设置
                            val configFile = File(HOLE_CONFIG_PATH)
                            if (configFile.exists()) {
                                try {
                                    val props = Properties()
                                    FileInputStream(configFile).use { fis ->
                                        props.load(fis)
                                    }
                                    val value = props.getProperty("jyct")
                                    when (value) {
                                        "1" -> param.result = true
                                        "0" -> param.result = false
                                    }
                                } catch (e: Exception) {
                                    // 读取失败，保留原返回值
                                }
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                // 类或方法不存在时静默忽略，避免影响 SystemUI 启动
            }
        }

        // ==================== 5. OTG 磁贴可用性 ====================
        // 控制快速设置面板中 OTG 磁贴（FlavorOneOtgTile）的显示与隐藏。
        // 根据配置文件中的 otgct 属性（1=强制可用，0=强制不可用）覆盖原始 isAvailable 返回值。
        if (lpparam.packageName == "com.android.systemui") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.oplus.systemui.qs.tiles.FlavorOneOtgTile",
                    lpparam.classLoader,
                    "isAvailable",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 读取配置文件中的可用性设置
                            val configFile = File(HOLE_CONFIG_PATH)
                            if (configFile.exists()) {
                                try {
                                    val props = Properties()
                                    FileInputStream(configFile).use { fis ->
                                        props.load(fis)
                                    }
                                    val value = props.getProperty("otgct")
                                    when (value) {
                                        "1" -> param.result = true
                                        "0" -> param.result = false
                                    }
                                } catch (e: Exception) {
                                    // 读取失败，保留原返回值
                                }
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                // 类或方法不存在时静默忽略，避免影响 SystemUI 启动
            }
        }

        // ==================== 6. 2x1 磁贴背景填满控制 ====================
        // 控制快速设置面板中 2x1 磁贴的背景填满样式。
        // 根据配置文件中的 ct2x1 属性（1=强制填满，0=强制不填满）覆盖原始返回值。
        if (lpparam.packageName == "com.android.systemui") {
            try {
                XposedHelpers.findAndHookMethod(
                    "com.oplus.systemui.qs.base.util.QsColorUtil",
                    lpparam.classLoader,
                    "isNeedUseSeparateDarkThemeColor",
                    Context::class.java,
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 读取配置文件中的可用性设置
                            val configFile = File(HOLE_CONFIG_PATH)
                            if (configFile.exists()) {
                                try {
                                    val props = Properties()
                                    FileInputStream(configFile).use { fis ->
                                        props.load(fis)
                                    }
                                    val value = props.getProperty("ct2x1")
                                    when (value) {
                                        "1" -> param.result = false
                                        "0" -> param.result = true
                                    }
                                } catch (e: Exception) {
                                    // 读取失败，保留原返回值
                                }
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                // 类或方法不存在时静默忽略，避免影响 SystemUI 启动
            }
        }
             
        // ==================== 快速设置面板颜色修改 ====================
        // 修改控制中心/快速设置面板中特定元素的颜色值。
        // 针对不同资源名称返回自定义颜色，覆盖系统默认颜色。
        if (lpparam.packageName == "com.android.systemui") {
            XposedHelpers.findAndHookMethod(
                Resources::class.java,
                "getColor",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val resId = param.args[0] as Int
                            val res = param.thisObject as Resources
                            val entryName = res.getResourceEntryName(resId) ?: return

                            // 读取配置文件中的可用性设置
                            val configFile = File(HOLE_CONFIG_PATH)
                            if (configFile.exists()) {
                                try {
                                    val props = Properties()
                                    FileInputStream(configFile).use { fis ->
                                        props.load(fis)
                                    }
                                    val value = props.getProperty("ct2x1")
                                    when (value) {
                                        "1" -> {
                                            when (entryName) {
                                                // ① 2x1图标背景（关、小圆圈）
                                                "status_bar_qs_smalltile_bg_color_inactive" -> {
                                                    param.result = 0x00FFFFFF.toInt() // 白色透明
                                                    return
                                                }

                                                // ② 2x1图标背景（开、小圆圈）
                                                "status_bar_qs_smalltile_bg_color_active" -> {
                                                    param.result = 0x00FFFFFF.toInt() // 白色透明
                                                    return
                                                }
                                                else -> {}
                                            }
                                        }

                                        "0" -> {
                                            /**
                                            * 看到了这行注释那么就证明你打开了我的代码
                                            * 真的崩溃了，写这阴间玩意
                                            * 找 AI 重置去吧
                                            * 不要动我的破b玩意，小心跑不了哦
                                            */
                                        }
                                        else -> {}
                                    }
                                } catch (e: Exception) {
                                    // 读取失败，保留原返回值
                                }
                            }

                        } catch (e: Exception) {
                            // 忽略异常，避免影响系统
                        }
                    }
                }
            )
        }


    }
}
