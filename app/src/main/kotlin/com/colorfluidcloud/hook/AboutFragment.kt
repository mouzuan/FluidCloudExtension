package com.colorfluidcloud.hook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * “关于”界面 Fragment
 * 用于显示模块相关信息（如版本、作者、说明等）
 * 当前仅加载布局文件，无额外逻辑
 */
class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 加载 fragment_about.xml 布局
        return inflater.inflate(R.layout.fragment_about, container, false)
    }
}