package com.settleup.android.ui.groups

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.settleup.android.data.local.entity.ExpenseEntity
import com.settleup.android.data.remote.CreateExpenseRequest
import com.settleup.android.data.remote.GroupDto
import com.settleup.android.data.remote.SplitEntry
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseSheet(
    expense: ExpenseEntity,
    group: GroupDto?,
    onDismiss: () -> Unit,
    onSave: (CreateExpenseRequest) -> Unit
) {
    var description by remember { mutableStateOf(expense.description) }
    var totalAmount by remember { mutableStateOf(expense.totalAmount) }
    var submitted by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()) {
            Text("Edit Transaction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = totalAmount, onValueChange = { totalAmount = it },
                label = { Text("Total Amount (${group?.currency ?: "INR"})") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Note: Editing splits is currently only supported via the Web portal or by reversing and recreating.", 
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { 
                    submitted = true
                    // Re-parse existing splits from DB to preserve them
                    val splits = expense.splitsJson?.let { parseSimpleSplitsForEdit(it) }
                    val req = CreateExpenseRequest(
                        description = description,
                        totalAmount = totalAmount,
                        paidBy = expense.paidBy,
                        splitType = expense.splitType,
                        splits = splits
                    )
                    onSave(req)
                },
                enabled = description.isNotBlank() && (totalAmount.toDoubleOrNull() ?: 0.0) > 0 && !submitted,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (submitted) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun parseSimpleSplitsForEdit(json: String): List<SplitEntry>? {
    return runCatching {
        val array = JSONArray(json)
        val list = mutableListOf<SplitEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(SplitEntry(obj.getString("userId"), obj.getString("value")))
        }
        list
    }.getOrNull()
}
