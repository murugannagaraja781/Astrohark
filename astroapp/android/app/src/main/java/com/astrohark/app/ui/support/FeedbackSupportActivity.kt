package com.astrohark.app.ui.support

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astrohark.app.ui.components.CustomCurvedHeader
import com.astrohark.app.ui.components.CustomTextField
import com.astrohark.app.ui.theme.CosmicAppTheme

class FeedbackSupportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicAppTheme {
                FeedbackSupportScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun FeedbackSupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Contact Support, 1: Feedback

    // Form State
    var email by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var issueType by remember { mutableStateOf("") }
    var suggestion by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        CustomCurvedHeader(
            title = "Feedback & Support",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(140.dp))

            // Content Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Type", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTab == 0, 
                            onClick = { selectedTab = 0 }, 
                            colors = RadioButtonDefaults.colors(selectedColor = Color.Red)
                        )
                        Text("Contact Support", modifier = Modifier.clickable { selectedTab = 0 })
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = selectedTab == 1, 
                            onClick = { selectedTab = 1 }, 
                            colors = RadioButtonDefaults.colors(selectedColor = Color.Red)
                        )
                        Text("Feedback", modifier = Modifier.clickable { selectedTab = 1 })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    CustomTextField("Email", email, { email = it }, "Enter your Email Id")
                    CustomTextField("Contact Number", contactNumber, { contactNumber = it }, "Enter your contact number")
                    
                    if (selectedTab == 0) {
                        CustomTextField("Subject", subject, { subject = it }, "Message subject")
                        
                        Text("Issue Type", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        OutlinedTextField(
                            value = issueType,
                            onValueChange = { issueType = it },
                            placeholder = { Text("Select option", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                            readOnly = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE87A1E),
                                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        CustomTextField("I suggest you", subject, { subject = it }, "Enter your idea")
                    }

                    Text("Suggestion Box", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
                    OutlinedTextField(
                        value = suggestion,
                        onValueChange = { suggestion = it },
                        placeholder = { Text("How can we help you today?", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE87A1E),
                            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            Toast.makeText(context, "Request Submitted!", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                    ) {
                        Text(
                            text = if (selectedTab == 0) "Send Message" else "Post Feedback", 
                            color = Color.Black, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
