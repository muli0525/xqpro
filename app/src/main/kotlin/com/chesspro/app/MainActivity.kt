package com.chesspro.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.chesspro.app.core.overlay.OverlayService
import com.chesspro.app.ui.screens.ChessMainScreen
import com.chesspro.app.ui.theme.ChineseChessProTheme

/**
 * 象棋Pro 主Activity
 */
class MainActivity : ComponentActivity() {

    // 悬浮窗权限
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能使用此功能", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ChineseChessProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChessMainScreen(
                        onStartOverlay = { requestOverlayAndStart() }
                    )
                }
            }
        }
    }

    /**
     * 请求悬浮窗权限并启动
     */
    private fun requestOverlayAndStart() {
        if (checkOverlayPermission()) {
            if (OverlayService.isRunning()) {
                // 已运行则停止
                stopOverlayService()
                Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
            } else {
                startOverlayService()
                Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestOverlayPermission()
        }
    }

    /**
     * 检查悬浮窗权限
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startForegroundService(intent)
    }

    /**
     * 停止悬浮窗服务
     */
    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
    }
}
