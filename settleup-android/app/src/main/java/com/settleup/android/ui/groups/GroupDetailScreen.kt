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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
    var showAddMember by remember { mutableStateOf(false) }
    var showEditExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

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
                },
                actions = {
                    TextButton(onClick = { showAddMember = true }) {
                        Text("Add Member")
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
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Expenses", "Balances", "Debts", "Settle Up", "Activity Log", "Total").forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ExpensesTab(expenses, viewModel, groupId, onEdit = { showEditExpense = it })
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
                4 -> ActivityLogTab(expenses, group?.currency ?: "INR")
                5 -> TotalTab(
                    expenses = expenses, 
                    currency = group?.currency ?: "INR", 
                    currentUserId = viewModel.currentUserId.collectAsState().value
                )
            }
        }
    }

    if (showAddMember) {
        var email by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { showAddMember = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
                Text("Add Member", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Friend's Email") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.addMember(groupId, email); showAddMember = false },
                    enabled = email.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Send Invite") }
            }
        }
    }

    showEditExpense?.let { exp ->
        EditExpenseSheet(
            expense = exp,
            group = group,
            onDismiss = { showEditExpense = null },
            onSave = { req -> 
                exp.remoteId?.let { viewModel.editExpense(it, req) }
                showEditExpense = null 
            }
        )
    }
}

// ── Tab 1: Expenses (with ⏳ sync badge) ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesTab(
    expenses: List<ExpenseEntity>,
    viewModel: GroupsViewModel,
    groupId: String,
    onEdit: (ExpenseEntity) -> Unit
) {
    val currentUserId = viewModel.currentUserId.collectAsState().value
    val groupDetail = viewModel.groupDetail.collectAsState().value
    
    val reversedIndices = mutableSetOf<Int>()
    val items = expenses.toList()
    for (i in items.indices) {
        val r = items[i]
        if (r.isReversal || r.description.startsWith("REVERSAL:")) {
            val origDesc = r.description.replace(Regex("^REVERSAL:\\s*(REVERSAL:\\s*)*"), "")
            for (j in i + 1 until items.size) {
                val candidate = items[j]
                if (!reversedIndices.contains(j) &&
                    !candidate.isReversal &&
                    !candidate.description.startsWith("REVERSAL:") &&
                    (candidate.description == origDesc || candidate.description.contains(origDesc)) &&
                    candidate.totalAmount == r.totalAmount
                ) {
                    reversedIndices.add(j)
                    break
                }
            }
        }
    }

    val activeExpenses = items.filterIndexed { idx, exp ->
        if (exp.isReversal || exp.description.startsWith("REVERSAL:")) false
        else if (reversedIndices.contains(idx)) false
        else true
    }
    
    if (activeExpenses.isEmpty()) {
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
        val expensesByMonth = activeExpenses.groupBy { 
            Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            expensesByMonth.forEach { (monthStr, monthExpenses) ->
                item {
                    Text(
                        text = monthStr,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                
                items(monthExpenses, key = { it.localId }) { expense ->
                    var actionTriggered by remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.Settled) {
                                actionTriggered = false
                                return@rememberSwipeToDismissBoxState true
                            }
                            if (it == SwipeToDismissBoxValue.StartToEnd) {
                                if (!actionTriggered) {
                                    actionTriggered = true
                                    onEdit(expense)
                                }
                                return@rememberSwipeToDismissBoxState false
                            } else if (it == SwipeToDismissBoxValue.EndToStart) {
                                if (!actionTriggered) {
                                    actionTriggered = true
                                    if (expense.remoteId != null) {
                                        viewModel.reverseExpense(expense.remoteId)
                                        viewModel.loadBalances(groupId)
                                    }
                                }
                                return@rememberSwipeToDismissBoxState false
                            }
                            false
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF3b82f6)
                                SwipeToDismissBoxValue.EndToStart -> Color(0xFFef4444)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                                } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Icon(Icons.Default.Delete, contentDescription = "Reverse", tint = Color.White)
                                }
                            }
                        },
                        content = {
                            ExpenseRow(expense, currentUserId, groupDetail?.members)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(expense: ExpenseEntity, currentUserId: String?, members: List<com.settleup.android.data.remote.MemberDto>?) {
    val date = Instant.ofEpochMilli(expense.createdAt).atZone(ZoneId.systemDefault())
    val monthShort = date.format(DateTimeFormatter.ofPattern("MMM"))
    val day = date.format(DateTimeFormatter.ofPattern("dd"))
    
    val isSettlement = expense.description.startsWith("SETTLEMENT:")
    
    var amountPaid = 0.0
    var amountOwed = 0.0
    
    if (currentUserId != null && !expense.ledgerEntriesJson.isNullOrBlank()) {
        runCatching {
            val array = JSONArray(expense.ledgerEntriesJson)
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                if (entry.getString("userId") == currentUserId) {
                    val amt = entry.getString("amount").toDoubleOrNull() ?: 0.0
                    if (entry.getString("entryType") == "CREDIT") amountPaid += amt
                    if (entry.getString("entryType") == "DEBIT") amountOwed += amt
                }
            }
        }
    }
    
    val net = amountPaid - amountOwed
    
    var payerName = expense.paidBy
    if (members != null) {
        val member = members.find { it.userId == expense.paidBy }
        if (member != null) payerName = member.name.split(" ").first()
    }
    
    val paidText = if (expense.paidBy == currentUserId) {
        "You paid ${expense.currency}${"%.2f".format(expense.totalAmount.toDoubleOrNull() ?: 0.0)}"
    } else {
        "$payerName paid ${expense.currency}${"%.2f".format(expense.totalAmount.toDoubleOrNull() ?: 0.0)}"
    }
    
    val desc = if (isSettlement) expense.description.replace("SETTLEMENT: ", "") else expense.description
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Text(monthShort, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isSettlement) Color(0xFF059669).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSettlement) {
                Icon(Icons.Outlined.Payments, contentDescription = null, tint = Color(0xFF059669))
            } else {
                Icon(Icons.Outlined.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desc, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (expense.syncStatus == SyncStatus.PENDING) {
                    Spacer(Modifier.width(6.dp))
                    Text("⏳", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(if (isSettlement) "Settlement" else paidText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Column(horizontalAlignment = Alignment.End) {
            if (net > 0.001) {
                Text("you lent", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                Text("${expense.currency}${"%.2f".format(net)}", 
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
            } else if (net < -0.001) {
                Text("you borrowed", style = MaterialTheme.typography.labelSmall, color = Color(0xFFea580c))
                Text("${expense.currency}${"%.2f".format(-net)}", 
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFea580c))
            } else if (amountPaid > 0.001) {
                Text("not involved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
