package com.settleup.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.settleup.android.data.remote.TokenProvider
import com.settleup.android.ui.auth.LoginScreen
import com.settleup.android.ui.auth.RegisterScreen
import com.settleup.android.ui.groups.GroupDetailScreen
import com.settleup.android.ui.groups.GroupListScreen
import com.settleup.android.ui.expenses.AddExpenseScreen
import kotlinx.coroutines.runBlocking

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    // A simple hack to check token on startup, normally use SplashScreen logic
    val tokenProvider = hiltViewModel<TokenViewModel>().tokenProvider
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        val token = tokenProvider.getToken()
        startDestination = if (token != null) "groups" else "login"
    }

    if (startDestination != null) {
        NavHost(navController = navController, startDestination = startDestination!!) {
            composable("login") {
                LoginScreen(
                    onNavigateToGroups = { navController.navigate("groups") { popUpTo("login") { inclusive = true } } },
                    onNavigateToRegister = { navController.navigate("register") }
                )
            }
            composable("register") {
                RegisterScreen(
                    onNavigateToGroups = { navController.navigate("groups") { popUpTo("register") { inclusive = true } } },
                    onNavigateToLogin = { navController.navigate("login") { popUpTo("register") { inclusive = true } } }
                )
            }
            composable("groups") {
                GroupListScreen(
                    onNavigateToGroup = { id -> navController.navigate("groups/$id") },
                    onLogout = { 
                        runBlocking { tokenProvider.clear() }
                        navController.navigate("login") { popUpTo(0) }
                    }
                )
            }
            composable("groups/{groupId}") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                GroupDetailScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() },
                    onAddExpense = { id -> navController.navigate("groups/$id/add-expense") }
                )
            }
            composable("groups/{groupId}/add-expense") { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                AddExpenseScreen(
                    groupId = groupId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
