package com.colorfluidcloud.hook

import androidx.lifecycle.ViewModel

/** 配置文件所在目录 */
const val CONFIG_DIR_PATH = "/storage/emulated/0/Android/FluidConfig"

/** 配置文件完整路径 */
const val HOLE_CONFIG_PATH = "$CONFIG_DIR_PATH/hole_config.txt"

/**
 * 共享配置数据的 ViewModel
 * 用于在 Fragment 和 Activity 之间暂存用户修改的配置值
 */
class ConfigViewModel : ViewModel() {
    var leftMargin: String = "540"
    var rightMargin: String = "540"
    var shapeEnabled: Boolean = false
    var muteEnabled: Boolean = false
    var otgEnabled: Boolean = false
    var bg2x1Enabled: Boolean = false
}