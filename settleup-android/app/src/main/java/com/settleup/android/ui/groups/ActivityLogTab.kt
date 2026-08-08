package com.settleup.android.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.settleup.android.data.local.entity.ExpenseEntity

@Composable
fun ActivityLogTab(expenses: List<ExpenseEntity>, currency: String) {
    if (expenses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(expenses.sortedByDescending { it.createdAt }, key = { it.localId }) { expense ->
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (expense.description.startsWith("SETTLEMENT:")) {
                                Text("🤝 Settlement", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(
                                    text = expense.description,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (expense.isReversal) TextDecoration.LineThrough else null,
                                    color = if (expense.isReversal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (expense.isReversal) {
                                SuggestionChip(
                                    onClick = {},
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    label = { Text("Reversed", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    },
                    supportingContent = { 
                        if (expense.description.startsWith("SETTLEMENT:")) {
                            Text("Payment recorded")
                        } else {
                            Text("Paid by ${expense.paidBy} • ${expense.splitType}")
                        }
                    },
                    trailingContent = {
                        Text(
                            "${currency} ${expense.totalAmount}",
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (expense.isReversal) TextDecoration.LineThrough else null,
                            color = if (expense.isReversal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
