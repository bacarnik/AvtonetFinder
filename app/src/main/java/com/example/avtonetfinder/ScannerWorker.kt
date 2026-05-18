package com.example.avtonetfinder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.*

class ScannerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        if (hour < 8 || hour >= 22) {
            return Result.success()
        }

        val intent = android.content.Intent(applicationContext, ScanService::class.java).apply {
            action = ScanService.ACTION_START_SCAN
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }

        return Result.success()
    }
}
