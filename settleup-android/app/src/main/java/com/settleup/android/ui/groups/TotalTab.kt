package com.settleup.android.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.settleup.android.data.local.entity.ExpenseEntity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun TotalTab(expenses: List<ExpenseEntity>, currency: String, currentUserId: String?) {
    val activeExpenses = expenses.filter { !it.isReversal && !it.description.startsWith("SETTLEMENT:") }
    
    // Group by month "yyyy-MM"
    val expensesByMonth = activeExpenses.groupBy { 
        Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }
    
    val allMonths = expensesByMonth.keys.sorted()
    var selectedMonth by remember { mutableStateOf<String?>(allMonths.lastOrNull()) }
    
    val currentExpenses = if (selectedMonth == null) activeExpenses else expensesByMonth[selectedMonth] ?: emptyList()
    
    var totalSpent = 0.0
    var userShare = 0.0
    
    currentExpenses.forEach { exp ->
        val amount = exp.totalAmount.toDoubleOrNull() ?: 0.0
        totalSpent += amount
        if (currentUserId != null && !exp.ledgerEntriesJson.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(exp.ledgerEntriesJson)
                for (i in 0 until array.length()) {
                    val entry = array.getJSONObject(i)
                    if (entry.getString("userId") == currentUserId && entry.getString("entryType") == "DEBIT") {
                        userShare += entry.getString("amount").toDoubleOrNull() ?: 0.0
                    }
                }
            }
        }
    }
    
    val percent = if (totalSpent > 0) ((userShare / totalSpent) * 100).toInt() else 0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Month Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
            val displayMonth = selectedMonth?.let {
                val year = it.substring(0, 4).toInt()
                val month = it.substring(5, 7).toInt()
                java.time.YearMonth.of(year, month).format(formatter)
            } ?: "All Time"
            
            Text(displayMonth, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedMonth = null }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("All Time")
                }
                if (allMonths.isNotEmpty()) {
                    val currentIndex = selectedMonth?.let { allMonths.indexOf(it) } ?: -1
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) selectedMonth = allMonths[currentIndex - 1] },
                        enabled = currentIndex > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("<") }
                    OutlinedButton(
                        onClick = { if (currentIndex in 0 until allMonths.size - 1) selectedMonth = allMonths[currentIndex + 1] },
                        enabled = currentIndex in 0 until allMonths.size - 1,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text(">") }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Metrics
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currency ${String.format(Locale.getDefault(), "%.2f", totalSpent)}", 
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color(0xFF0284c7))
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                
                Text("Your share", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$currency ${String.format(Locale.getDefault(), "%.2f", userShare)}", 
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("$percent% of total group spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Bar Chart (last 3 months)
        if (allMonths.isNotEmpty()) {
            val last3 = allMonths.takeLast(3)
            val maxSpend = last3.maxOfOrNull { m -> expensesByMonth[m]?.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 } ?: 0.0 } ?: 1.0
            
            Text("Recent Spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                last3.forEach { m ->
                    val mExpenses = expensesByMonth[m] ?: emptyList()
                    val mSpend = mExpenses.sumOf { it.totalAmount.toDoubleOrNull() ?: 0.0 }
                    val heightRatio = if (maxSpend > 0) (mSpend / maxSpend).toFloat() else 0f
                    val isSelected = m == selectedMonth
                    
                    val monthLabel = java.time.YearMonth.parse(m).format(DateTimeFormatter.ofPattern("MMM").withLocale(Locale.getDefault())).uppercase()
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).clickable { selectedMonth = m }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(heightRatio.coerceAtLeast(0.01f))
                                .background(
                                    color = if (isSelected) Color(0xFF38bdf8) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(monthLabel, style = MaterialTheme.typography.labelSmall, 
                             color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                             fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
