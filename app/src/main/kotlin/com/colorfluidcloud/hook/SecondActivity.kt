package com.colorfluidcloud.hook

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SecondActivity : AppCompatActivity() {
    private lateinit var bottomBlurView: BlurView
    private lateinit var viewPager: ViewPager2
    private lateinit var optionHome: View
    private lateinit var optionAbout: View
    private lateinit var iconHome: ImageView
    private lateinit var iconAbout: ImageView
    private lateinit var textHome: TextView
    private lateinit var textAbout: TextView
    private lateinit var restartButtonBackground: Button

    private lateinit var viewModel: ConfigViewModel
    private val configFile = File(HOLE_CONFIG_PATH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // 获取共享 ViewModel
        viewModel = ViewModelProvider(this)[ConfigViewModel::class.java]

        // 初始化透明状态栏
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        // 绑定控件
        initView()

        // 初始化底部模糊
        setupBlur(bottomBlurView)

        // ViewPager2 适配器
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int) = when (position) {
                0 -> HomeFragment()
                else -> AboutFragment()
            }
        }

        // ViewPager页面切换监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBottomNav(position)
            }
        })

        // 底部导航点击事件（添加动画）
        optionHome.setOnClickListener {
            animateView(optionHome)
            viewPager.currentItem = 0
        }
        optionAbout.setOnClickListener {
            animateView(optionAbout)
            viewPager.currentItem = 1
        }

        // 重启SystemUI按钮点击：先保存配置，再重启
        restartButtonBackground.setOnClickListener {
            saveConfigFromViewModel()
            restartSystemUI()
            restartSystemUIplugins()
        }

        // 初始选中主页
        updateBottomNav(0)
    }

    // 绑定所有控件
    private fun initView() {
        bottomBlurView = findViewById(R.id.bottom_blur_view)
        viewPager = findViewById(R.id.view_pager)
        optionHome = findViewById(R.id.option_home)
        optionAbout = findViewById(R.id.option_about)
        iconHome = findViewById(R.id.icon_home)
        iconAbout = findViewById(R.id.icon_about)
        textHome = findViewById(R.id.text_home)
        textAbout = findViewById(R.id.text_about)
        restartButtonBackground = findViewById(R.id.restartButtonBackground)
    }

    /** 更新底部导航样式 */
    private fun updateBottomNav(selected: Int) {
        val (activeIcon, activeText) = if (selected == 0) {
            Pair(iconHome, textHome)
        } else {
            Pair(iconAbout, textAbout)
        }
        val (inactiveIcon, inactiveText) = if (selected == 0) {
            Pair(iconAbout, textAbout)
        } else {
            Pair(iconHome, textHome)
        }
        activeIcon.setColorFilter(Color.parseColor("#4386EF"))
        activeText.setTextColor(Color.parseColor("#4386EF"))
        inactiveIcon.setColorFilter(Color.parseColor("#999999"))
        inactiveText.setTextColor(Color.parseColor("#999999"))
    }

    /** 设置模糊 */
    private fun setupBlur(blurView: BlurView) {
        val rootView = window.decorView as? ViewGroup ?: return
        blurView.setupWith(rootView, RenderScriptBlur(this))
            .setFrameClearDrawable(window.decorView.background)
            .setBlurRadius(15f)
    }

    /** 点击缩放动画 */
    private fun animateView(view: View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    /**
     * 将 ViewModel 中的当前配置写入文件
     */
    private fun saveConfigFromViewModel() {
        try {
            val left = viewModel.leftMargin
            val right = viewModel.rightMargin
            val shape = if (viewModel.shapeEnabled) "1" else "0"
            val mute = if (viewModel.muteEnabled) "1" else "0"
            val otg = if (viewModel.otgEnabled) "1" else "0"
            val bg2x1 = if (viewModel.bg2x1Enabled) "1" else "0"

            if (left.isEmpty() || right.isEmpty()) {
                Toast.makeText(this, R.string.toast_invalid_values, Toast.LENGTH_SHORT).show()
                return
            }

            val content = """
                left=$left
                right=$right
                ltyfmxz=$shape
                jyct=$mute
                otgct=$otg
                ct2x1=$bg2x1
            """.trimIndent()

            // 确保配置目录存在
            val configDir = File(CONFIG_DIR_PATH)
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            // 写入文件
            configFile.writeText(content)
            Toast.makeText(this, R.string.toast_save_success, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 重启 SystemUI（需要 root 权限） */
    private fun restartSystemUI() = lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching { Runtime.getRuntime().exec("su -c pkill -f com.android.systemui").waitFor() }
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { exitCode ->
                    val msgRes = if (exitCode == 0) R.string.systemui_restart_success else R.string.systemui_restart_failed
                    Toast.makeText(this@SecondActivity, msgRes, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this@SecondActivity, getString(R.string.systemui_restart_error, e.message), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /** 重启 systemui.plugins（需要 root 权限） */
    private fun restartSystemUIplugins() = lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching { Runtime.getRuntime().exec("su -c pkill -f com.oplus.systemui.plugins").waitFor() }
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { exitCode ->
                    val msgRes = if (exitCode == 0) R.string.systemui_restart_success else R.string.systemui_restart_failed
                    Toast.makeText(this@SecondActivity, msgRes, Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this@SecondActivity, getString(R.string.systemui_restart_error, e.message), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}