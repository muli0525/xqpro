package com.chesspro.app

import android.app.Application
import android.util.Log
import com.chesspro.app.core.engine.PikafishEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 象棋Pro Application类
 * 初始化全局资源
 */
class ChineseChessProApp : Application() {

    companion object {
        private const val TAG = "ChineseChessProApp"
        lateinit var instance: ChineseChessProApp
            private set
    }

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pikafish引擎实例
    lateinit var engine: PikafishEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化Pikafish引擎
        engine = PikafishEngine.getInstance(this)
        appScope.launch {
            val success = engine.initialize()
            if (success) {
                Log.i(TAG, "Pikafish引擎初始化成功")
                engine.start()
            } else {
                Log.e(TAG, "Pikafish引擎初始化失败")
            }
        }
    }
}
