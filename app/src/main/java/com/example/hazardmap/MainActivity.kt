package com.example.hazardmap

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.hazardmap.service.ReportExpiryWorker
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        scheduleExpiryWorker()
    }

    private fun scheduleExpiryWorker() {
        val request = PeriodicWorkRequestBuilder<ReportExpiryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "report_expiry",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
