package com.colorfluidcloud.hook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面 Activity
 * 显示模块激活状态、存储权限检查、Root 检测，并提供进入配置界面的按钮
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var configButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 设置透明状态栏，并让图标显示为深色（适配 Android 15/16）
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        statusTextView = findViewById(R.id.statusTextView)
        configButton = findViewById(R.id.restartButtonBackground) // 注意：按钮 ID 可能已改名，此处保留原命名

        // 检查存储权限和 Root 权限
        checkStoragePermission()
        checkRootAccess()

        // 点击按钮跳转到配置界面（SecondActivity）
        configButton.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 更新模块激活状态文本
        statusTextView.text = if (isModuleActivated()) {
            getText(R.string.module_status1)   // 已激活
        } else {
            getText(R.string.module_status)    // 未激活
        }
    }

    /**
     * 检查设备是否已获取 Root 权限
     * 通过执行 "su -c id" 命令并判断输出是否包含 uid=0
     */
    private fun checkRootAccess() {
        lifecycleScope.launch(Dispatchers.IO) {
            val isRoot = try {
                val process = Runtime.getRuntime().exec("su -c id")
                val output = process.inputStream.bufferedReader().readLine()
                process.waitFor()
                output?.contains("uid=0") == true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity,
                    if (isRoot) R.string.root_granted else R.string.root_failed,
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 检查是否有所有文件访问权限（MANAGE_EXTERNAL_STORAGE）
     * 若无权限则弹出引导对话框，并禁用配置按钮
     */
    private fun checkStoragePermission() {
        if (Environment.isExternalStorageManager()) {
            enableConfigButton()
        } else {
            disableConfigButton()
            showManageStorageDialog()
        }
    }

    /**
     * 显示引导用户授予存储权限的对话框
     * 点击“去设置”将跳转到系统应用权限页
     */
    private fun showManageStorageDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_manage_storage_title)
            .setMessage(R.string.permission_manage_storage_message)
            .setCancelable(false)
            .setPositiveButton(R.string.permission_go_to_settings) { _, _ ->
                try {
                    // 跳转到所有文件访问权限的设置页面
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    // 备用方案：跳转到应用详情页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    Toast.makeText(this@MainActivity, R.string.permission_storage_required, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.permission_exit) { _, _ ->
                finishAffinity() // 退出应用
            }
            .show()
    }

    /** 启用配置按钮（视觉和点击） */
    private fun enableConfigButton() {
        configButton.isEnabled = true
        configButton.alpha = 1.0f
    }

    /** 禁用配置按钮（半透明且不可点击） */
    private fun disableConfigButton() {
        configButton.isEnabled = false
        configButton.alpha = 0.5f
    }

    /**
     * 模块激活状态检测方法（原始返回 false）
     * 会被 Xposed 钩子替换为始终返回 true，用于界面显示
     */
    private fun isModuleActivated(): Boolean = false
}