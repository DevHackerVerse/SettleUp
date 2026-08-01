package com.settleup.android.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.settleup.android.data.local.entity.BalanceEntity
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.local.entity.SyncStatus
import com.settleup.android.data.remote.MemberDto
import com.settleup.android.data.remote.SettlementDto
import com.settleup.android.data.remote.SettlementRequest
import com.settleup.android.data.remote.SettlementSuggestion
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: (String) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    val group by viewModel.groupDetail.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val settlement by viewModel.settlement.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    // Count pending expenses for tab badge
    val pendingCount = expenses.count { it.syncStatus == SyncStatus.PENDING }

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
        viewModel.loadBalances(groupId)
        viewModel.loadDebts(groupId)
    }

    LaunchedEffect(settlement?.settlementId) {
        val sid = settlement?.settlementId ?: return@LaunchedEffect
        if (settlement?.status == "PENDING" || settlement?.status == "PROCESSING") {
            viewModel.pollSettlement(sid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(group?.name ?: "Loading…", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${group?.members?.size ?: 0} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (pendingCount > 0) {
                                SuggestionChip(
                                    onClick = { selectedTab = 0 },
                                    label = {
                                        Text(
                                            "⏳ $pendingCount pending",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { onAddExpense(groupId) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Expenses", "Balances", "Debts", "Settle Up").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ExpensesTab(expenses, viewModel, groupId)
                1 -> BalancesTab(balances, group?.currency ?: "INR")
                2 -> DebtsTab(debts?.settlementsSuggested ?: emptyList())
                3 -> SettleUpTab(
                    members = group?.members ?: emptyList(),
                    currency = group?.currency ?: "INR",
                    settlement = settlement,
                    onSettle = { payeeId, amount ->
                        viewModel.initiateSettlement(groupId,
                            SettlementRequest(payeeId, amount, UUID.randomUUID().toString()))
                    },
                    onReset = { viewModel.resetSettlement() }
                )
            }
        }
    }
}

// ── Tab 1: Expenses (with ⏳ sync badge) ─────────────────────────────

@Composable
private fun ExpensesTab(
    expenses: List<ExpenseEntity>,
    viewModel: GroupsViewModel,
    groupId: String
) {
    if (expenses.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💸", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("No expenses yet", style = MaterialTheme.typography.titleMedium)
                Text("Tap + to add one", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(expenses, key = { it.localId }) { expense ->
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(expense.description, fontWeight = FontWeight.SemiBold)
                            // ⏳ Pending Sync badge — the Phase 4 core UI feature
                            when (expense.syncStatus) {
                                SyncStatus.PENDING -> SuggestionChip(
                                    onClick = {},
                                    label = { Text("⏳ Pending", style = MaterialTheme.typography.labelSmall) }
                                )
                                SyncStatus.FAILED -> SuggestionChip(
                                    onClick = {},
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    label = { Text("❌ Sync failed", style = MaterialTheme.typography.labelSmall) }
                                )
                                else -> Unit
                            }
                        }
                    },
                    supportingContent = { Text("${expense.splitType} • ${expense.currency}") },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${expense.currency} ${expense.totalAmount}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Only allow reverse on synced expenses (have a remoteId)
                            if (expense.remoteId != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.reverseExpense(expense.remoteId)
                                        viewModel.loadBalances(groupId)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reverse", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

// ── Tab 2: Balances ──────────────────────────────────────────────────

@Composable
private fun BalancesTab(balances: List<BalanceEntity>, currency: String) {
    if (balances.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No balance data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(balances) { b ->
                val amount = b.netBalance.toDoubleOrNull() ?: 0.0
                val color = when {
                    amount > 0 -> Color(0xFF10B981)
                    amount < 0 -> MaterialTheme.colorScheme.error
                    else       -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListItem(
                    headlineContent = { Text(b.name, fontWeight = FontWeight.Medium) },
                    trailingContent = {
                        Text(
                            "${if (amount >= 0) "+" else ""}${b.netBalance} $currency",
                            color = color, fontWeight = FontWeight.Bold
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

// ── Tab 3: Debts ─────────────────────────────────────────────────────

@Composable
private fun DebtsTab(debts: List<SettlementSuggestion>) {
    if (debts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("All settled up!", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(debts) { d ->
                ListItem(
                    headlineContent = { Text("${d.fromName} → ${d.toName}", fontWeight = FontWeight.Medium) },
                    trailingContent = { Text(d.amount, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                )
                HorizontalDivider()
            }
        }
    }
}

// ── Tab 4: Settle Up ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleUpTab(
    members: List<MemberDto>,
    currency: String,
    settlement: SettlementDto?,
    onSettle: (payeeId: String, amount: String) -> Unit,
    onReset: () -> Unit
) {
    var payeeId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (settlement != null) {
            val (containerColor, statusText) = when (settlement.status) {
                "PENDING"    -> Color(0xFFF59E0B) to "⏳ PENDING"
                "PROCESSING" -> Color(0xFF6366F1) to "⚙️ PROCESSING"
                "COMPLETED"  -> Color(0xFF10B981) to "✅ COMPLETED"
                "FAILED"     -> MaterialTheme.colorScheme.error to "❌ FAILED"
                else         -> MaterialTheme.colorScheme.surfaceVariant to settlement.status
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.12f))
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = containerColor)
                    Spacer(Modifier.height(8.dp))
                    Text("$currency ${settlement.amount}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (!settlement.mockUpiRef.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("UPI Ref: ${settlement.mockUpiRef}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (settlement.status == "PENDING" || settlement.status == "PROCESSING") {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (settlement.status == "COMPLETED" || settlement.status == "FAILED") {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onReset) { Text("Record Another") }
                    }
                }
            }
        } else {
            Text("Record a Payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = members.find { it.userId == payeeId }?.name ?: "",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Pay to") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    members.forEach { m ->
                        DropdownMenuItem(text = { Text(m.name) }, onClick = { payeeId = m.userId; expanded = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount, onValueChange = { amount = it },
                label = { Text("Amount ($currency)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (payeeId.isNotBlank() && amount.isNotBlank()) onSettle(payeeId, amount) },
                enabled = payeeId.isNotBlank() && amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Settle Up", fontWeight = FontWeight.SemiBold) }
        }
    }
}
