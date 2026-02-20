package com.example.highspeedcamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

class WebViewActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://focusntrack.vercel.app/"))
        startActivity(browserIntent)
        
        finish()
    }
}
