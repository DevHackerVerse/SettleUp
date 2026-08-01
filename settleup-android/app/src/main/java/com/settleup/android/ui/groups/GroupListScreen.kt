package com.settleup.android.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.settleup.android.data.local.entity.GroupEntity
import com.settleup.android.data.remote.CreateGroupRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onNavigateToGroup: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    // groups is now a StateFlow<List<GroupEntity>> from Room — updates automatically
    val groups by viewModel.groups.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadGroups() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Groups", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, "New Group") },
                text = { Text("New Group") }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                    Text("Create a group to get started",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    GroupCard(group = group, onClick = { onNavigateToGroup(group.groupId) })
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateGroupSheet(
            onDismiss = { showCreateSheet = false },
            onConfirm = { name, description, currency, budget ->
                viewModel.createGroup(CreateGroupRequest(name, description.ifBlank { null }, currency, budget))
                showCreateSheet = false
            }
        )
    }
}

@Composable
private fun GroupCard(group: GroupEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SuggestionChip(onClick = {}, label = { Text(group.currency, style = MaterialTheme.typography.labelSmall) })
            }
            if (!group.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(group.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Text("${group.memberCount} member${if (group.memberCount != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Double?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    var budget by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text("Create Group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Currency") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("Budget Amount (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { onConfirm(name, description, currency, budget.toDoubleOrNull()) },
                    enabled = name.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Create") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
