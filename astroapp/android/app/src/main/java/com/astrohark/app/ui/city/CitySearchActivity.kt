package com.astrohark.app.ui.city

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class CitySearchActivity : ComponentActivity() {
    private val viewModel: CityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CSCScreen(viewModel) { city ->
                        val intent = Intent().apply {
                            // Format: City, State, Country
                            val state = viewModel.uiState.value.selectedState?.name ?: ""
                            val country = viewModel.uiState.value.selectedCountry?.name ?: ""
                            val fullName = "${city.name}, $state, $country"

                            putExtra("name", fullName)
                            putExtra("city", city.name)
                            putExtra("state", state)
                            putExtra("country", country)
                            putExtra("lat", city.lat)
                            putExtra("lon", city.lon)
                            // Pass timezone ID from csc.db (ex: "Asia/Kolkata")
                            putExtra("timezoneId", city.timezone ?: "")
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
fun CSCScreen(viewModel: CityViewModel, onFinalSelect: (LocationItem) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()

    // Query for currently active search (City only)
    var cityQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Simple Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Text(
                "Select Location",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge
            )
        }

        var showCountryDialog by remember { mutableStateOf(false) }
        var showStateDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.padding(16.dp)) {
            // STEP 1: Country Selection
            Text("Country", style = MaterialTheme.typography.titleSmall)
            LocationPickerField(
                value = uiState.selectedCountry?.name ?: "Select Country",
                onClick = { showCountryDialog = true }
            )

            if (showCountryDialog) {
                AlertDialog(
                    onDismissRequest = { showCountryDialog = false },
                    title = { Text("Select Country") },
                    text = {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(uiState.countries) { country ->
                                ListItem(
                                    headlineContent = { Text(country.name) },
                                    modifier = Modifier.clickable {
                                        viewModel.onCountrySelected(country)
                                        showCountryDialog = false
                                    }
                                )
                                Divider()
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCountryDialog = false }) {
                            Text("Close")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STEP 2: State Selection
            if (uiState.selectedCountry != null) {
                Text("Select State", style = MaterialTheme.typography.titleSmall)
                LocationPickerField(
                    value = uiState.selectedState?.name ?: "Select State",
                    onClick = { showStateDialog = true }
                )

                if (showStateDialog) {
                    AlertDialog(
                        onDismissRequest = { showStateDialog = false },
                        title = { Text("Select State") },
                        text = {
                            if (uiState.states.isEmpty() && uiState.isLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                    items(uiState.states) { state ->
                                        ListItem(
                                            headlineContent = { Text(state.name) },
                                            modifier = Modifier.clickable {
                                                viewModel.onStateSelected(state)
                                                showStateDialog = false
                                            }
                                        )
                                        Divider()
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showStateDialog = false }) {
                                Text("Close")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STEP 3: City Search & Select
            if (uiState.selectedState != null) {
                Text("Select City", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = cityQuery,
                    onValueChange = { cityQuery = it },
                    label = { Text("Search city in ${uiState.selectedState!!.name}") },
                    leadingIcon = { Icon(Icons.Default.Search, "") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    singleLine = true
                )

                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val filteredCities = if (cityQuery.isBlank()) uiState.cities else uiState.cities.filter {
                    it.name.contains(cityQuery, ignoreCase = true)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredCities) { city ->
                        ListItem(
                            headlineContent = { Text(city.name) },
                            modifier = Modifier.clickable { onFinalSelect(city) }
                        )
                        Divider()
                    }
                    if (filteredCities.isEmpty() && !uiState.isLoading) {
                        item {
                            Text(
                                "No cities found.",
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationPickerField(value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(56.dp)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(value, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Default.ArrowDropDown, "", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
