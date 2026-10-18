package com.medtrack.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medtrack.ui.theme.MedTrackTheme

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MedTrackTheme {
                Scaffold { padding ->
                    Column(Modifier.padding(padding).padding(16.dp)) {
                        Text("How Pillhaven uses Health Connect", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Pillhaven only reads the meals you log (nutrition records), including in the background, " +
                                "to remind you to take medications marked \"after meal\". It never writes to Health Connect, " +
                                "and the data stays on this phone.",
                            Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
