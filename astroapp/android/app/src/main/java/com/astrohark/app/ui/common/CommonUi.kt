package com.astrohark.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ModernSummaryDialog(
    title: String = "Session Summary",
    duration: Int, // in seconds
    amount: Double,
    isAstrologer: Boolean,
    onDismiss: () -> Unit,
    onSubmitReview: ((Int, String) -> Unit)? = null
) {
    var rating by remember { mutableStateOf(5) }
    var comment by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (isSubmitted || isAstrologer) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(20.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Icon with Glow
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(56.dp)
                    )
                }
                
                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2D3436)
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Details Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    SummaryItem("Total Duration", String.format("%02d:%02d", duration / 60, duration % 60))
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFE9ECEF))
                    SummaryItem(
                        label = if (isAstrologer) "Estimated Earnings" else "Amount Deducted",
                        value = "₹${String.format("%.2f", amount)}",
                        valueColor = if (isAstrologer) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                    )
                }

                // Review Section for Clients
                if (!isAstrologer && !isSubmitted) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "How was your experience?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3436)
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    // Star Rating
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (i <= rating) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { rating = i }
                                    .padding(2.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = { Text("Write your review (optional)", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF7F00),
                            unfocusedBorderColor = Color(0xFFE9ECEF)
                        ),
                        maxLines = 3
                    )
                } else if (isSubmitted) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Thank you for your feedback!",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                
                val buttonText = if (!isAstrologer && !isSubmitted) "SUBMIT REVIEW & EXIT" else "DONE"
                
                Button(
                    onClick = {
                        if (!isAstrologer && !isSubmitted && onSubmitReview != null) {
                            onSubmitReview(rating, comment)
                            isSubmitted = true
                            // Optionally dismiss after a small delay or keep "DONE"
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F00))
                ) {
                    Text(buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color = Color(0xFF2D3436)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF636E72),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
