package com.astrohark.app.ui.rituals

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.astrohark.app.ui.theme.CosmicAppTheme
import com.astrohark.app.ui.home.getImageUrl

class RitualDetailActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val title = intent.getStringExtra("title") ?: "Ritual Detail"
        val subtitle = intent.getStringExtra("subtitle") ?: ""
        val description = intent.getStringExtra("description") ?: ""
        val imageUrl = intent.getStringExtra("imageUrl") ?: ""
        val price = intent.getDoubleExtra("price", 0.0)
        val isTamil = intent.getBooleanExtra("isTamil", true)

        setContent {
            CosmicAppTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text(title, fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        if (price > 0) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shadowElevation = 8.dp,
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .navigationBarsPadding(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Price", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        Text("₹${price.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                    }
                                    Button(
                                        onClick = { /* Implement Booking Flow */ },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("BOOK NOW", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        AsyncImage(
                            model = getImageUrl(imageUrl),
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentScale = ContentScale.Crop
                        )
                        
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                            
                            if (subtitle.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.Gray
                                )
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color(0xFFF0F0F0))
                            
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 26.sp,
                                color = Color.Black.copy(alpha = 0.8f)
                            )
                            
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
    }
}
