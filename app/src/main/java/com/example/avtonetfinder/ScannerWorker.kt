package com.example.avtonetfinder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.*

class ScannerWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        // Stop scans after 22:00 and before 08:00
        // Exception: we can let it run at 08:00 exactly or the first one after 08:00
        if (hour < 8 || hour >= 22) {
            return Result.success()
        }

        val dbHelper = DatabaseHelper(applicationContext)
        val scraper = Scraper()
        val notifier = NotificationHelper(applicationContext)

        val searches = dbHelper.getAllSearches()
        for (search in searches) {
            if (search.isEnabled) {
                val newListings = scraper.fetchNewListings(search.url, dbHelper)
                for (listing in newListings) {
                    notifier.showNotification(listing)
                    dbHelper.markListingAsSeen(listing.id)
                }
                dbHelper.updateLastChecked(search.id, System.currentTimeMillis())
            }
        }

        return Result.success()
    }
}
