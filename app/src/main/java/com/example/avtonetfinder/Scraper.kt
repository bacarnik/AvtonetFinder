package com.example.avtonetfinder

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class Scraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun fetchNewListings(url: String, dbHelper: DatabaseHelper): List<Listing> {
        val newListings = mutableListOf<Listing>()
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val html = response.body.string()
                val doc = Jsoup.parse(html)

                // Avto.net listings are typically in div with class "GO-Results-Row" or similar
                val rows = doc.select(".GO-Results-Row")
                for (row in rows) {
                    val linkElement = row.select("a.GO-Results-Data-Top").first() ?: continue
                    val title = linkElement.text()
                    val href = linkElement.attr("href")
                    
                    // Extract ID from href: details.asp?id=12345678&...
                    val id = href.substringAfter("id=", "").substringBefore("&")
                    if (id.isEmpty()) continue

                    val yearElement = row.select(".GO-Results-Data-Table-Cell:contains(Letnik)").next().first()
                    val year = yearElement?.text() ?: ""

                    if (!dbHelper.isListingSeen(id)) {
                        newListings.add(Listing(id, title, year, "https://www.avto.net/Ads/$href"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return newListings
    }
}

data class Listing(
    val id: String,
    val title: String,
    val year: String,
    val url: String
)
