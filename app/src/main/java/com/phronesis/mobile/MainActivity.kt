package com.phronesis.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.launch
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallActivityLauncher
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallResultHandler
import com.revenuecat.purchases.ui.revenuecatui.activity.PaywallResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Assignment
import com.phronesis.mobile.BuildConfig

object Routes {
    const val DASHBOARD = "Dashboard"
    const val SCHEDULE = "Schedule"
    const val UNITS = "Units"
    const val QUIZ = "Quiz"
    const val CONTROL_PANEL = "ControlPanel"
}

private val pageOrder = listOf(Routes.SCHEDULE, Routes.UNITS, Routes.DASHBOARD, Routes.QUIZ, Routes.CONTROL_PANEL)

class MainActivity : ComponentActivity() {

    lateinit var paywallLauncher: PaywallActivityLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        paywallLauncher = PaywallActivityLauncher(this, object : PaywallResultHandler {
            override fun onActivityResult(result: PaywallResult) {
                // After the paywall closes, QuizScreen will re-check access automatically.
            }
        })

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        if (BuildConfig.DEBUG) {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(this, "test_ENqGSljZcZxGVssHerDUCMWJUhZ").build())
        }
        setContent {
            PhronesisTheme {
                PhronesisApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhronesisApp() {
    val pagerState = rememberPagerState(initialPage = 2) { pageOrder.size }
    val coroutineScope = rememberCoroutineScope()
    var showProfileDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun goTo(route: String) {
        val index = pageOrder.indexOf(route)
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(labelFor(pageOrder[pagerState.currentPage])) },
                actions = {
                    if (pageOrder[pagerState.currentPage] == Routes.DASHBOARD) {
                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = "Profile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = WarmSurface) {
                pageOrder.forEachIndexed { index, route ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { goTo(route) },
                        icon = { Icon(iconFor(route), contentDescription = labelFor(route)) },
                        label = { Text(labelFor(route)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = pageOrder.size,
            modifier = Modifier.padding(innerPadding)
        ) { pageIndex ->
            when (pageOrder[pageIndex]) {
                Routes.DASHBOARD -> DashboardScreen(onBubbleClick = { route -> goTo(route) })
                Routes.SCHEDULE -> ScheduleScreen()
                Routes.UNITS -> UnitsScreen()
                Routes.QUIZ -> QuizScreen()
                Routes.CONTROL_PANEL -> ControlPanelScreen()
            }
        }
    }

    if (showProfileDialog) {
        ProfileDialog(context = context, onDismiss = { showProfileDialog = false })
    }
}

private fun labelFor(route: String): String = when (route) {
    Routes.DASHBOARD -> "Dashboard"
    Routes.SCHEDULE -> "Schedule"
    Routes.UNITS -> "Units"
    Routes.QUIZ -> "Quiz"
    Routes.CONTROL_PANEL -> "Assignments"
    else -> route
}

private fun iconFor(route: String) = when (route) {
    Routes.DASHBOARD -> Icons.Outlined.Home
    Routes.SCHEDULE -> Icons.Outlined.CalendarMonth
    Routes.UNITS -> Icons.Outlined.MenuBook
    Routes.QUIZ -> Icons.Outlined.Quiz
    Routes.CONTROL_PANEL -> Icons.Outlined.Assignment
    else -> Icons.Outlined.Home
}

@Composable
private fun ProfileDialog(context: android.content.Context, onDismiss: () -> Unit) {
    var editName by remember { mutableStateOf(UserPrefs.getName(context) ?: "") }
    var editProgram by remember { mutableStateOf(UserPrefs.getProgram(context) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column {
                OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") })
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = editProgram, onValueChange = { editProgram = it }, label = { Text("Program") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (editName.isNotBlank()) UserPrefs.setName(context, editName)
                if (editProgram.isNotBlank()) UserPrefs.setProgram(context, editProgram)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}