package com.rmst.biometrics

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rmst.biometrics.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.verifyButton.setOnClickListener {            binding.verifyButton.isEnabled = false
            binding.loadingProgressBar.visibility = View.VISIBLE
            val token = binding.tokenEditText.text.toString().trim()
            if (token.isNotEmpty()) {
                verifyToken(token)
            } else {
                binding.verifyButton.isEnabled = true
                binding.loadingProgressBar.visibility = View.GONE
                Toast.makeText(this, "Please enter a token", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifyToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://demo.rmstservices.com/biometrics/verifyToken?token=$token"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    if (jsonObject.getBoolean("success")) {
                        val companyObject = jsonObject.getJSONObject("company")
                        val companyName = companyObject.getString("companyName")
                        val company = companyObject.toString()
                        withContext(Dispatchers.Main) {
                            saveAuthData(token, company)
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            binding.verifyButton.isEnabled = true
                            binding.loadingProgressBar.visibility = View.GONE
                            Toast.makeText(this@LoginActivity, "Invalid token", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.verifyButton.isEnabled = true
                        binding.loadingProgressBar.visibility = View.GONE
                        Toast.makeText(this@LoginActivity, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.verifyButton.isEnabled = true
                    binding.loadingProgressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Error verifying token: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveAuthData(token: String, company: String) {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        sharedPreferences.edit()
            .putString("auth_token", token)
            .putString("company_data", company)
            .apply()
    }
}
