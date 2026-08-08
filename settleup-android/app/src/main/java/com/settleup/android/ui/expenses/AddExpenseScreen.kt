package com.settleup.android.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.settleup.android.data.remote.CreateExpenseRequest
import com.settleup.android.data.remote.SplitEntry
import com.settleup.android.ui.groups.GroupsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val group by viewModel.groupDetail.collectAsState()
    val members = group?.members ?: emptyList()

    var description by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf("") }
    var splitType by remember { mutableStateOf("EQUAL") }
    var splitValues by remember { mutableStateOf(mapOf<String, String>()) }
    var payerExpanded by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(members) {
        viewModel.loadGroup(groupId)
        if (members.isNotEmpty() && paidBy.isBlank()) paidBy = members.first().userId
        splitValues = members.associate { it.userId to "" }
    }

    var lentTo by remember { mutableStateOf("") }
    
    val splitTypes = listOf("EQUAL", "PERCENTAGE", "CUSTOM", "LENT")
    val total = totalAmount.toDoubleOrNull() ?: 0.0
    val splitSum = splitValues.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val isValid = description.isNotBlank() && total > 0 && paidBy.isNotBlank() && when (splitType) {
        "PERCENTAGE" -> Math.abs(splitSum - 100.0) < 0.01
        "CUSTOM"     -> Math.abs(splitSum - total) < 0.01
        "LENT"       -> lentTo.isNotBlank()
        else         -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = totalAmount, onValueChange = { totalAmount = it },
                    label = { Text("Total Amount (${group?.currency ?: "INR"})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                ExposedDropdownMenuBox(expanded = payerExpanded, onExpandedChange = { payerExpanded = it }) {
                    OutlinedTextField(
                        value = members.find { it.userId == paidBy }?.name ?: "Select…",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Paid By") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payerExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = payerExpanded, onDismissRequest = { payerExpanded = false }) {
                        members.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) },
                                onClick = { paidBy = m.userId; payerExpanded = false })
                        }
                    }
                }
            }
            item {
                Text("Split Type", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    splitTypes.forEachIndexed { idx, type ->
                        SegmentedButton(
                            selected = splitType == type,
                            onClick = { splitType = type },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = splitTypes.size)
                        ) { Text(type) }
                    }
                }
            }
            if (splitType != "EQUAL" && splitType != "LENT" && members.isNotEmpty()) {
                item {
                    val label = if (splitType == "PERCENTAGE")
                        "Sum: ${splitSum.toInt()}% / 100%" else "Sum: $splitSum / $total"
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                itemsIndexed(members) { _, member ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(member.name, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = splitValues[member.userId] ?: "",
                            onValueChange = { v -> splitValues = splitValues.toMutableMap().apply { put(member.userId, v) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = { Text(if (splitType == "PERCENTAGE") "%" else group?.currency ?: "") },
                            modifier = Modifier.width(120.dp)
                        )
                    }
                }
            }
            if (splitType == "LENT" && members.isNotEmpty()) {
                item {
                    var lentExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = lentExpanded, onExpandedChange = { lentExpanded = it }) {
                        OutlinedTextField(
                            value = members.find { it.userId == lentTo }?.name ?: "Select friend...",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Lend To (owes 100% of ${group?.currency ?: "INR"} ${totalAmount.ifBlank { "0.00" }})") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(lentExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = lentExpanded, onDismissRequest = { lentExpanded = false }) {
                            members.forEach { m ->
                                DropdownMenuItem(text = { Text("${m.name} ${if (m.userId == paidBy) "(You / Payer)" else ""}") },
                                    onClick = { lentTo = m.userId; lentExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("💡 This records a direct 1-to-1 loan.", 
                         style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                if (submitted) {
                    // Show brief saving indicator
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    }
                } else {
                    Button(
                        onClick = {
                            submitted = true
                            val actualSplitType = if (splitType == "LENT") "CUSTOM" else splitType
                            val splits = if (actualSplitType == "EQUAL") null else members.map { m ->
                                val v = if (splitType == "LENT") {
                                    if (m.userId == lentTo) totalAmount else "0"
                                } else {
                                    splitValues[m.userId] ?: "0"
                                }
                                SplitEntry(m.userId, v)
                            }
                            // Uses repository: saves locally as PENDING, enqueues SyncWorker
                            viewModel.addExpense(groupId, CreateExpenseRequest(
                                description, totalAmount, paidBy, actualSplitType, splits))
                            onBack()
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text("Add Expense", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✓ Works offline — will sync when connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
