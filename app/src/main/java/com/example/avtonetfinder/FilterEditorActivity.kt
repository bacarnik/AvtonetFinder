package com.example.avtonetfinder

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.avtonetfinder.databinding.ActivityFilterEditorBinding

class FilterEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilterEditorBinding
    private lateinit var dbHelper: DatabaseHelper
    private var searchId: Int = -1

    private val makes = listOf("Any", "Audi", "BMW", "Citroen", "Fiat", "Ford", "Honda", "Hyundai", "Kia", "Mazda", "Mercedes-Benz", "Opel", "Peugeot", "Renault", "Seat", "Skoda", "Toyota", "Volkswagen", "Volvo")
    private val bodyTypes = listOf("Any", "Limuzina", "Kombilimuzina", "Karavan", "Enotrapec", "SUV / Terensko", "Crossover", "Coupe", "Cabriolet")
    private val fuelTypes = listOf("Any", "Bencin", "Diesel", "Hibrid", "Električni", "LPG", "CNG")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilterEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        searchId = intent.getIntExtra("SEARCH_ID", -1)

        setupSpinners()
        
        if (searchId != -1) {
            loadSearchData()
        }

        binding.btnSave.setOnClickListener {
            saveSearch()
        }
    }

    private fun setupSpinners() {
        val makeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, makes)
        makeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMake.adapter = makeAdapter

        val bodyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bodyTypes)
        bodyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBody.adapter = bodyAdapter

        val fuelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fuelTypes)
        fuelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFuel.adapter = fuelAdapter
    }

    private fun loadSearchData() {
        val searches = dbHelper.getAllSearches()
        val search = searches.find { it.id == searchId }
        search?.let {
            binding.editName.setText(it.name)
            
            // Simple URL parsing to restore filters
            val uri = android.net.Uri.parse(it.url)
            val make = uri.getQueryParameter("znamka")
            if (make != null) {
                val index = makes.indexOf(make)
                if (index != -1) binding.spinnerMake.setSelection(index)
            }
            
            binding.editModel.setText(uri.getQueryParameter("model") ?: "")
            
            val body = uri.getQueryParameter("oblika")
            if (body != null) {
                val index = bodyTypes.indexOf(body)
                if (index != -1) binding.spinnerBody.setSelection(index)
            }

            val fuel = uri.getQueryParameter("gorivo")
            if (fuel != null) {
                val index = fuelTypes.indexOf(fuel)
                if (index != -1) binding.spinnerFuel.setSelection(index)
            }

            binding.editPriceMin.setText(uri.getQueryParameter("cenaMin") ?: "")
            binding.editPriceMax.setText(uri.getQueryParameter("cenaMax") ?: "")
            binding.editYearMin.setText(uri.getQueryParameter("letnikMin") ?: "")
            binding.editYearMax.setText(uri.getQueryParameter("letnikMax") ?: "")
            binding.editMileageMin.setText(uri.getQueryParameter("prevozeniMin") ?: "")
            binding.editMileageMax.setText(uri.getQueryParameter("prevozeniMax") ?: "")
        }
    }

    private fun saveSearch() {
        var name = binding.editName.text.toString().trim()
        
        val make = binding.spinnerMake.selectedItem.toString()
        val model = binding.editModel.text.toString().trim()
        val body = binding.spinnerBody.selectedItem.toString()
        val fuel = binding.spinnerFuel.selectedItem.toString()
        val priceMin = binding.editPriceMin.text.toString().trim()
        val priceMax = binding.editPriceMax.text.toString().trim()
        val yearMin = binding.editYearMin.text.toString().trim()
        val yearMax = binding.editYearMax.text.toString().trim()
        val mileageMin = binding.editMileageMin.text.toString().trim()
        val mileageMax = binding.editMileageMax.text.toString().trim()

        if (name.isEmpty()) {
            name = generateSearchName(make, model, fuel, body)
        }

        val url = constructUrl(make, model, body, fuel, priceMin, priceMax, yearMin, yearMax, mileageMin, mileageMax)

        if (searchId == -1) {
            dbHelper.addSearch(name, url)
        } else {
            dbHelper.updateSearch(searchId, name, url)
        }

        finish()
    }

    private fun generateSearchName(make: String, model: String, fuel: String, body: String): String {
        val parts = mutableListOf<String>()
        if (make != "Any") parts.add(make)
        if (model.isNotEmpty()) parts.add(model)
        if (fuel != "Any") parts.add(fuel)
        if (body != "Any") parts.add(body)
        
        return if (parts.isEmpty()) "All Vehicles" else parts.joinToString(" ")
    }

    private fun constructUrl(make: String, model: String, body: String, fuel: String, pMin: String, pMax: String, yMin: String, yMax: String, mMin: String, mMax: String): String {
        val sb = StringBuilder("https://www.avto.net/Ads/results.asp?zaslon=default&SUBID=000")
        
        if (make != "Any") sb.append("&znamka=").append(make)
        if (model.isNotEmpty()) sb.append("&model=").append(model)
        if (body != "Any") sb.append("&oblika=").append(body)
        if (fuel != "Any") sb.append("&gorivo=").append(fuel)
        if (pMin.isNotEmpty()) sb.append("&cenaMin=").append(pMin)
        if (pMax.isNotEmpty()) sb.append("&cenaMax=").append(pMax)
        if (yMin.isNotEmpty()) sb.append("&letnikMin=").append(yMin)
        if (yMax.isNotEmpty()) sb.append("&letnikMax=").append(yMax)
        if (mMin.isNotEmpty()) sb.append("&prevozeniMin=").append(mMin)
        if (mMax.isNotEmpty()) sb.append("&prevozeniMax=").append(mMax)
        
        sb.append("&TIPS=0&SEZNAM=1&SORT=2")
        
        return sb.toString()
    }
}
