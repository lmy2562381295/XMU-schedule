package com.zako.xmuschedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zako.xmuschedule.data.db.AppDatabase
import com.zako.xmuschedule.ui.AppRoot
import com.zako.xmuschedule.ui.theme.XmuScheduleTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            try {
                AppDatabase.ensureDefaultPeriods(this@MainActivity)
            } catch (t: Throwable) {
                android.util.Log.e("xmu-schedule", "init failed", t)
            }
        }
        requestNotificationPermissionIfNeeded()
        setContent {
            XmuScheduleTheme {
                AppRoot()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
