package com.colorfluidcloud.hook

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import java.io.File

/**
 * 主页 Fragment（配置界面）
 * 提供编辑框、开关等控件，用于修改流体云的边距、形状、磁贴可用性等配置
 * 配置暂存在 ViewModel 中，点击“重启作用域”时统一写入文件
 */
class HomeFragment : Fragment() {

    // UI 控件
    private lateinit var editLeft: EditText          // 左侧边距输入框
    private lateinit var editRight: EditText         // 右侧边距输入框
    private lateinit var shapeSwitch: ToggleButton   // 形状开关（配置项 ltyfmxz）
    private lateinit var muteSwitch: ToggleButton    // 静音磁贴开关（配置项 jyct）
    private lateinit var otgSwitch: ToggleButton     // OTG 磁贴开关（配置项 otgct）
    private lateinit var bg2x1Switch: ToggleButton   // 2x1磁贴背景填满开关（配置项 ct2x1）
    private lateinit var btnSubmit: Button           // 保存按钮（现仅用于提示）

    // 模糊视图
    private lateinit var homeBlurView: BlurView
    private lateinit var shapeBlurView: BlurView

    // 配置文件对象（使用全局常量定义路径）
    private val configFile = File(HOLE_CONFIG_PATH)

    // 共享 ViewModel
    private lateinit var viewModel: ConfigViewModel

    // 标志位：避免加载配置时触发监听器
    private var isUpdating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 获取共享 ViewModel
        viewModel = ViewModelProvider(requireActivity())[ConfigViewModel::class.java]

        // 绑定控件
        editLeft = view.findViewById(R.id.edit_text1)
        editRight = view.findViewById(R.id.edit_text2)
        shapeSwitch = view.findViewById(R.id.shape_switch)
        muteSwitch = view.findViewById(R.id.mute_switch)
        otgSwitch = view.findViewById(R.id.otg_switch)
        bg2x1Switch = view.findViewById(R.id.bg2x1_switch)
        btnSubmit = view.findViewById(R.id.action_button)

        // 绑定模糊视图
        homeBlurView = view.findViewById(R.id.home_blur_view)
        shapeBlurView = view.findViewById(R.id.shape_blur_view)

        // 设置模糊效果
        setupBlur(homeBlurView)
        setupBlur(shapeBlurView)

        // 加载现有配置到界面和 ViewModel
        loadConfig()

        // 监听文本框变化，更新 ViewModel
        editLeft.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdating) {
                    viewModel.leftMargin = s?.toString() ?: ""
                }
            }
        })
        editRight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isUpdating) {
                    viewModel.rightMargin = s?.toString() ?: ""
                }
            }
        })

        // 监听开关变化，更新 ViewModel（不保存文件）
        shapeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) viewModel.shapeEnabled = isChecked
        }
        muteSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) viewModel.muteEnabled = isChecked
        }
        otgSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) viewModel.otgEnabled = isChecked
        }
        bg2x1Switch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdating) viewModel.bg2x1Enabled = isChecked
        }

        // 提交按钮仅用于提示（实际保存由重启按钮触发）
        btnSubmit.setOnClickListener {
            Toast.makeText(requireContext(), R.string.toast_save_success, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 为指定的 BlurView 添加模糊效果
     * @param blurView 需要模糊的视图
     */
    private fun setupBlur(blurView: BlurView) {
        val activity = requireActivity()
        val rootView = activity.window.decorView as? ViewGroup ?: return
        blurView.setupWith(rootView, RenderScriptBlur(activity))
            .setFrameClearDrawable(activity.window.decorView.background)
            .setBlurRadius(15f)
    }

    /**
     * 从配置文件读取现有值，填充到界面控件和 ViewModel 中
     * 如果文件不存在，则使用默认值：
     * left=540, right=540, 形状开关关闭, 静音磁贴关闭, OTG 磁贴关闭, 2x1背景开关关闭
     */
    private fun loadConfig() {
        isUpdating = true
        try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val lines = content.split("\n")
                for (line in lines) {
                    when {
                        line.startsWith("left=") -> {
                            val value = line.substringAfter("left=")
                            editLeft.setText(value)
                            viewModel.leftMargin = value
                        }
                        line.startsWith("right=") -> {
                            val value = line.substringAfter("right=")
                            editRight.setText(value)
                            viewModel.rightMargin = value
                        }
                        line.startsWith("ltyfmxz=") -> {
                            val checked = line.substringAfter("ltyfmxz=") == "1"
                            shapeSwitch.isChecked = checked
                            viewModel.shapeEnabled = checked
                        }
                        line.startsWith("jyct=") -> {
                            val checked = line.substringAfter("jyct=") == "1"
                            muteSwitch.isChecked = checked
                            viewModel.muteEnabled = checked
                        }
                        line.startsWith("otgct=") -> {
                            val checked = line.substringAfter("otgct=") == "1"
                            otgSwitch.isChecked = checked
                            viewModel.otgEnabled = checked
                        }
                        line.startsWith("ct2x1=") -> {
                            val checked = line.substringAfter("ct2x1=") == "1"
                            bg2x1Switch.isChecked = checked
                            viewModel.bg2x1Enabled = checked
                        }
                    }
                }
            } else {
                // 默认值
                editLeft.setText("540")
                editRight.setText("540")
                shapeSwitch.isChecked = false
                muteSwitch.isChecked = false
                otgSwitch.isChecked = false
                bg2x1Switch.isChecked = false

                viewModel.leftMargin = "540"
                viewModel.rightMargin = "540"
                viewModel.shapeEnabled = false
                viewModel.muteEnabled = false
                viewModel.otgEnabled = false
                viewModel.bg2x1Enabled = false
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.toast_load_config_failed, Toast.LENGTH_SHORT).show()
        } finally {
            isUpdating = false
        }
    }
}