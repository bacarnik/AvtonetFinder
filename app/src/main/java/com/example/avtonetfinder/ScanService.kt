package com.example.avtonetfinder

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.webkit.*
import androidx.core.app.NotificationCompat
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class ScanService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var webView: WebView? = null
    private val isScanning = AtomicBoolean(false)
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "scan_service_channel"
        const val ACTION_START_SCAN = "ACTION_START_SCAN"
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
        notificationHelper = NotificationHelper(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("ScanService", "Page started: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("ScanService", "Page finished: $url")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Scan Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AvtoNet Monitor")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SCAN) {
            runScanQueue()
        }
        return START_STICKY
    }

    private fun runScanQueue() {
        if (isScanning.getAndSet(true)) return

        serviceScope.launch {
            val searches = withContext(Dispatchers.IO) { dbHelper.getAllSearches().filter { it.isEnabled } }
            val total = searches.size
            
            searches.forEachIndexed { index, search ->
                processSearch(search, index + 1, total)
            }
            
            isScanning.set(false)
            updateNotification("Last scan finished at ${Date()}")
            ScanManager.updateProgress(-1, ScanStatus.IDLE)
        }
    }

    private suspend fun processSearch(search: Search, currentIdx: Int, total: Int) {
        Log.d("ScanService", "Processing ${search.name}")
        val progressMsg = "${search.name} ($currentIdx/$total)"
        updateNotification("Scanning: $progressMsg")
        ScanManager.updateProgress(search.id, ScanStatus.LOADING, "Loading page...", currentIdx, total)

        try {
            // Total scan timeout for one search: 45 seconds
            withTimeout(45000) {
                val html = loadUrlInWebView(search.url, search.id, currentIdx, total)
                
                ScanManager.updateProgress(search.id, ScanStatus.PARSING, "Parsing listings...", currentIdx, total)
                val newListings = parseHtml(html)
                Log.d("ScanService", "Found ${newListings.size} listings in HTML for ${search.name}")
                
                val resultMsg = "Completed - ${newListings.size} found"
                withContext(Dispatchers.IO) {
                    var newFound = 0
                    val isFirstScan = dbHelper.isFirstScan(search.id)
                    Log.d("ScanService", "Is first scan for ${search.name}: $isFirstScan")

                    newListings.forEach { listing ->
                        val seen = dbHelper.isListingSeen(listing.id)
                        if (!seen) {
                            if (!isFirstScan) {
                                notificationHelper.showNotification(listing)
                                dbHelper.addNotificationHistory(listing.title, listing.url)
                                newFound++
                                Log.d("ScanService", "NEW listing detected: ${listing.id} - ${listing.title}")
                            } else {
                                Log.d("ScanService", "Baseline listing marked as seen: ${listing.id}")
                            }
                            dbHelper.markListingAsSeen(listing.id)
                        } else {
                            Log.d("ScanService", "Listing already seen: ${listing.id}")
                        }
                    }
                    dbHelper.updateLastChecked(search.id, System.currentTimeMillis())
                    val finalMsg = if (isFirstScan) {
                        "Baseline established (${newListings.size} listings)"
                    } else {
                        if (newFound > 0) "Completed - $newFound new listings" else "Completed - no new listings"
                    }
                    dbHelper.updateLastResult(search.id, finalMsg)
                    ScanManager.updateProgress(search.id, ScanStatus.COMPLETED, finalMsg, currentIdx, total)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val msg = "Timeout"
            Log.e("ScanService", "Timeout scanning ${search.name}")
            ScanManager.updateProgress(search.id, ScanStatus.TIMEOUT, msg, currentIdx, total)
            withContext(Dispatchers.IO) { dbHelper.updateLastResult(search.id, msg) }
            stopWebViewLoading()
        } catch (e: Exception) {
            Log.e("ScanService", "Error scanning ${search.name}", e)
            val msg = if (e.message?.contains("Cloudflare") == true) "Blocked by Cloudflare" else "Failed to load"
            ScanManager.updateProgress(search.id, ScanStatus.FAILED, msg, currentIdx, total)
            withContext(Dispatchers.IO) { dbHelper.updateLastResult(search.id, msg) }
            stopWebViewLoading()
        }
    }

    private fun stopWebViewLoading() {
        Handler(Looper.getMainLooper()).post {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
        }
    }

    private suspend fun loadUrlInWebView(url: String, id: Int, current: Int, total: Int): String = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        
        val timeoutRunnable = Runnable {
            if (cont.isActive) {
                cont.resumeWithException(Exception("WEBVIEW_TIMEOUT"))
            }
        }

        // Internal WebView timeout: 30 seconds for the page load itself
        mainHandler.postDelayed(timeoutRunnable, 30000)

        cont.invokeOnCancellation {
            mainHandler.removeCallbacks(timeoutRunnable)
            stopWebViewLoading()
        }

        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                private var finished = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    ScanManager.updateProgress(id, ScanStatus.LOADING, "Loading page...", current, total)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (finished) return
                    if (url == "about:blank") return
                    
                    Log.d("ScanService", "Page finished: $url")
                    ScanManager.updateProgress(id, ScanStatus.WAITING_FOR_DOM, "Waiting for content...", current, total)
                    
                    // Give it some time for JS content to appear, but with a timeout
                    mainHandler.postDelayed({
                        if (!cont.isActive) return@postDelayed
                        
                        view?.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                            if (cont.isActive) {
                                finished = true
                                mainHandler.removeCallbacks(timeoutRunnable)
                                // evaluateJavascript returns JSON-wrapped string
                                val unescaped = if (html != null && html != "null") {
                                    html.removeSurrounding("\"")
                                        .replace("\\u003C", "<")
                                        .replace("\\u003E", ">")
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                        .replace("\\n", "\n")
                                        .replace("\\r", "\r")
                                } else ""
                                cont.resume(unescaped) { }
                            }
                        }
                    }, 3000)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true && cont.isActive) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cont.resumeWithException(Exception("Failed to load page: ${error?.description}"))
                    }
                }
            }
            webView?.loadUrl(url)
        }
    }

    private suspend fun parseHtml(html: String): List<Listing> = withContext(Dispatchers.IO) {
        val newListings = mutableListOf<Listing>()
        try {
            if (html.isEmpty()) return@withContext emptyList<Listing>()
            
            val doc = org.jsoup.Jsoup.parse(html)
            
            if (html.contains("cf-browser-verification") || html.contains("cf-challenge") || html.contains("Forbidden")) {
                throw Exception("Blocked or Cloudflare detected")
            }

            val rows = doc.select(".GO-Results-Row")
            Log.d("ScanService", "Found ${rows.size} rows with class .GO-Results-Row")
            
            for (row in rows) {
                val linkElement = row.select("a.GO-Results-Data-Top").first() ?: continue
                val title = linkElement.text()
                val href = linkElement.attr("href")
                
                // Stable ID extraction: Extract the numeric ID from the URL
                // Example: details.asp?id=12345678&... or ad.asp?id=12345678
                val idMatch = Regex("id=(\\d+)").find(href)
                val id = idMatch?.groupValues?.get(1) ?: ""
                
                if (id.isEmpty()) {
                    Log.w("ScanService", "Could not extract ID from href: $href")
                    continue
                }

                val yearElement = row.select(".GO-Results-Data-Table-Cell:contains(Letnik)").next().first()
                val year = yearElement?.text() ?: ""

                newListings.add(Listing(id, title, year, "https://www.avto.net/Ads/$href"))
            }
            
            // If .GO-Results-Row is not found, try alternative selectors
            if (newListings.isEmpty()) {
                val altRows = doc.select("div[id^=promoted_], div[class*=GO-Results-Row]")
                Log.d("ScanService", "Found ${altRows.size} alternative rows")
            }
        } catch (e: Exception) {
            Log.e("ScanService", "Parse error", e)
            throw e
        }
        newListings
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
