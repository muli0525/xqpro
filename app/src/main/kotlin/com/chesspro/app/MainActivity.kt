package com.chesspro.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
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
import com.chesspro.app.core.capture.ScreenCaptureService
import com.chesspro.app.core.overlay.OverlayService
import com.chesspro.app.ui.screens.ChessMainScreen
import com.chesspro.app.ui.theme.ChineseChessProTheme

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.savePermission(result.resultCode, result.data)
            startOverlayService()
            Toast.makeText(this, "悬浮窗已启动，点击\"识别\"按钮开始分析", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "需要屏幕录制权限才能识别棋盘", Toast.LENGTH_SHORT).show()
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

    private fun requestOverlayAndStart() {
        if (OverlayService.isRunning()) {
            stopOverlayService()
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        if (ScreenCaptureService.hasPermission()) {
            startOverlayService()
            Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
            return
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlayPermissionLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        startForegroundService(intent)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
    }
}
