package com.example.avtonetfinder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.example.avtonetfinder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: SearchAdapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        setSupportActionBar(binding.toolbar)
        
        setupRecyclerView()
        setupHistoryRecyclerView()
        setupBottomNavigation()
        setupListeners()
        
        loadSearches()
        loadHistory()
        
        scheduleScanner()
        requestNotificationPermission()
        observeScanProgress()
    }

    private fun observeScanProgress() {
        lifecycleScope.launch {
            ScanManager.scanProgress.collect {
                if (binding.recyclerView.isVisible) {
                    adapter.notifyDataSetChanged()
                }
            }
        }
        lifecycleScope.launch {
            ScanManager.isAnyScanning.collect { isScanning ->
                invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val scanItem = menu.findItem(R.id.action_scan_now)
        scanItem?.isEnabled = !ScanManager.isAnyScanning.value
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_now -> {
                startManualScan()
                return true
            }
            R.id.action_settings -> {
                AlertDialog.Builder(this)
                    .setTitle("Settings")
                    .setMessage("Scanning is active from 08:00 to 22:00 every 15-30 minutes.")
                    .setPositiveButton("OK", null)
                    .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startManualScan() {
        val intent = android.content.Intent(this, ScanService::class.java).apply {
            action = ScanService.ACTION_START_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = SearchAdapter(emptyList(),
            onToggle = { id, enabled ->
                dbHelper.updateSearchEnabled(id, enabled)
            },
            onEdit = { search ->
                showAddEditDialog(search)
            },
            onDelete = { id ->
                dbHelper.deleteSearch(id)
                loadSearches()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(emptyList()) { url ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewHistory.adapter = historyAdapter
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_searches -> {
                    showSearches(true)
                    true
                }
                R.id.nav_history -> {
                    showSearches(false)
                    loadHistory()
                    true
                }
                else -> false
            }
        }
    }

    private fun showSearches(show: Boolean) {
        binding.recyclerView.isVisible = show
        binding.emptyView.isVisible = show && adapter.itemCount == 0
        binding.recyclerViewHistory.isVisible = !show
        binding.emptyViewHistory.isVisible = !show && historyAdapter.itemCount == 0
        binding.fabAdd.isVisible = show
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun loadSearches() {
        val searches = dbHelper.getAllSearches()
        adapter.updateData(searches)
        if (binding.recyclerView.isVisible) {
            binding.emptyView.isVisible = searches.isEmpty()
        }
    }

    private fun loadHistory() {
        val history = dbHelper.getNotificationHistory()
        historyAdapter.updateData(history)
        if (binding.recyclerViewHistory.isVisible) {
            binding.emptyViewHistory.isVisible = history.isEmpty()
        }
    }

    private fun showAddEditDialog(search: Search?) {
        val intent = android.content.Intent(this, FilterEditorActivity::class.java)
        if (search != null) {
            intent.putExtra("SEARCH_ID", search.id)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadSearches()
    }

    private fun scheduleScanner() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ScannerWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AvtonetScanner",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
    }
}
