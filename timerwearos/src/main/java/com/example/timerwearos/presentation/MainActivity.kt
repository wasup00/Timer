package com.example.timerwearos.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    var countdownText by remember { mutableStateOf("No timer set") }
    val countdownRef = remember {
        FirebaseDatabase.getInstance().reference.child("selectedDate")
    }
    val coroutineScope = rememberCoroutineScope()
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dateStr = snapshot.getValue(String::class.java)
                countdownJob?.cancel()
                if (dateStr != null) {
                    countdownJob = coroutineScope.launch {
                        updateCountdownText(dateStr) { newText ->
                            countdownText = newText
                        }
                    }
                } else {
                    countdownText = "No timer set"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                countdownText = "Error: ${error.message}"
            }
        }
        countdownRef.addValueEventListener(listener)

        onDispose {
            countdownRef.removeEventListener(listener)
            countdownJob?.cancel()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Countdown",
                color = MaterialTheme.colors.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = countdownText,
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp
            )
        }
    }
}

private suspend fun updateCountdownText(dateStr: String, onUpdate: (String) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val targetDate = LocalDateTime.parse(dateStr, formatter)

    while (true) {
        val now = LocalDateTime.now()
        val remainingTime = java.time.Duration.between(now, targetDate)

        if (remainingTime.isNegative) {
            onUpdate("Countdown finished!")
            break
        }

        val days = remainingTime.toDays()
        val hours = remainingTime.toHours() % 24
        val minutes = remainingTime.toMinutes() % 60
        val seconds = remainingTime.seconds % 60

        val countdownText = "${days}d ${hours}h ${minutes}m ${seconds}s"
        onUpdate(countdownText)

        delay(1000) // Update every second
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}