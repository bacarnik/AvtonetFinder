package com.example.avtonetfinder

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.example.avtonetfinder.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: SearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupListeners()
        loadSearches()
        scheduleScanner()
        requestNotificationPermission()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMessage("Scanning is active from 08:00 to 22:00 every 30-45 minutes.\n\nThis app is for personal use.")
                .setPositiveButton("OK", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
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

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun loadSearches() {
        val searches = dbHelper.getAllSearches()
        adapter.updateData(searches)
        binding.emptyView.isVisible = searches.isEmpty()
    }

    private fun showAddEditDialog(search: Search?) {
        val view = LayoutInflater.from(this).inflate(android.R.layout.two_line_list_item, null)
        // For simplicity, using a basic custom dialog. In real app, create a proper layout.
        val nameInput = EditText(this).apply { 
            hint = "Search Name"
            setText(search?.name ?: "")
        }
        val urlInput = EditText(this).apply { 
            hint = "Avto.net URL"
            setText(search?.url ?: "")
        }
        
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
            addView(nameInput)
            addView(urlInput)
        }

        AlertDialog.Builder(this)
            .setTitle(if (search == null) "Add Search" else "Edit Search")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString()
                val url = urlInput.text.toString()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    if (search == null) {
                        dbHelper.addSearch(name, url)
                    } else {
                        dbHelper.updateSearch(search.id, name, url)
                    }
                    loadSearches()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleScanner() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ScannerWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AvtonetScanner",
            ExistingPeriodicWorkPolicy.KEEP,
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
