package com.example.ui.simulator

import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.engine.BharatTaxiParser
import com.example.domain.engine.ParsedNode
import com.example.domain.engine.RapidoParser
import com.example.domain.engine.TapMethodType
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.AccentAmberWarning
import com.example.ui.theme.AccentGreenSuccess
import com.example.ui.theme.AccentRedDanger
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SimulatorDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var simulatorAppTab by remember { mutableStateOf("bharat_taxi") }
    val tapResult by viewModel.simulatorTapResult.collectAsState()
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6060B18))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Ride Offer Simulator",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Visual mock trees for parser & tap validation",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // App Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isBharat = simulatorAppTab == "bharat_taxi"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isBharat) PrimaryCyan else Color.Transparent)
                                .clickable { simulatorAppTab = "bharat_taxi" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bharat Taxi Mock",
                                fontWeight = if (isBharat) FontWeight.Bold else FontWeight.Medium,
                                color = if (isBharat) Color(0xFF060B18) else TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        val isRapido = simulatorAppTab == "rapido"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isRapido) PrimaryCyan else Color.Transparent)
                                .clickable { simulatorAppTab = "rapido" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Rapido Mock",
                                fontWeight = if (isRapido) FontWeight.Bold else FontWeight.Medium,
                                color = if (isRapido) Color(0xFF060B18) else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Tap result indicator
                    if (tapResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = AccentGreenSuccess.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentGreenSuccess)
                        ) {
                            Text(
                                text = tapResult ?: "",
                                color = AccentGreenSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Simulated App Screen Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0E1726))
                            .padding(12.dp)
                    ) {
                        if (simulatorAppTab == "bharat_taxi") {
                            SimulatedBharatTaxiCard(
                                onTestTap = {
                                    scope.launch {
                                        delay(85)
                                        viewModel.recordSimulatorTapResult("Node Click (ACTION_CLICK)", 85)
                                    }
                                }
                            )
                        } else {
                            SimulatedRapidoOfferList(
                                onTestTap = {
                                    scope.launch {
                                        delay(110)
                                        viewModel.recordSimulatorTapResult("Gesture Tap", 110)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatedBharatTaxiCard(onTestTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // TEST REQUEST BADGE
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentAmberWarning
                ) {
                    Text(
                        text = "TEST REQUEST",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // FARE
                Text(
                    text = "₹50",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pickup Line
                Text(text = "0.1 km · 1 min", color = AccentGreenSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "Kempegowda Int'l Airport, T1 Terminal, Bengaluru 560300", color = Color(0xFFCBD5E1), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(8.dp))

                // Drop Line
                Text(text = "1 km · 5 min", color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "Aerocity Devanahalli Business Park, Bengaluru 562110", color = Color(0xFFCBD5E1), fontSize = 12.sp)

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: +10 and Accept
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(0.35f)
                    ) {
                        Text("+10", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onTestTap,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .weight(0.65f)
                            .testTag("simulator_accept_button")
                    ) {
                        Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SimulatedRapidoOfferList(onTestTap: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Bike Boost
            RapidoSimulatedCard(
                rideType = "Bike Boost",
                fare = "₹223 + ₹137",
                pickup = "2.5 km · Khanpur - A17, Khanpur Extension, New Delhi",
                drop = "25.8 km · Vishnu Garden Tilak Nagar, New Delhi",
                onAccept = onTestTap
            )

            // Card 2: Auto
            RapidoSimulatedCard(
                rideType = "Auto",
                fare = "₹100 + ₹53",
                pickup = "1.2 km · Saket Metro Station Gate 2",
                drop = "8.4 km · Cyber Hub DLF Phase 2, Gurugram",
                onAccept = onTestTap
            )

            // Card 3: Truncated
            RapidoSimulatedCard(
                rideType = "Bike Lite",
                fare = "₹41 + ₹40",
                pickup = "2.6 km · Hauz Khas Village",
                drop = "12.0 km · Connaught Place Inner Circle...",
                onAccept = onTestTap
            )
        }

        // Floating Overlapping Pill: "Extra from Customer"
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF059669),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extra from Customer ▼",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RapidoSimulatedCard(
    rideType: String,
    fare: String,
    pickup: String,
    drop: String,
    onAccept: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Text(
                        text = rideType,
                        color = Color(0xFFFACC15),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(text = fare, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Pickup: $pickup", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1)
            Text(text = "Drop: $drop", color = Color(0xFFE2E8F0), fontSize = 11.sp, maxLines = 1)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308))
                ) {
                    Text("Accept", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
