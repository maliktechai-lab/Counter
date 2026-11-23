package com.reeltracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reeltracker.ui.screens.home.HomeScreen
import com.reeltracker.ui.screens.privacy.PrivacyScreen
import com.reeltracker.ui.screens.stats.StatsScreen
import com.reeltracker.ui.theme.ReelTrackerTheme

/**
 * Single-Activity entry point. Hosts the bottom-navigation scaffold and
 * the Compose NavHost with three top-level destinations:
 *   - Home     — today's counts + service status
 *   - Stats    — daily / weekly / monthly bar charts
 *   - Privacy  — privacy policy explanation
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReelTrackerTheme {
                val navController = rememberNavController()

                val items = listOf(
                    NavItem("home",    "Home",    Icons.Filled.Home),
                    NavItem("stats",   "Stats",   Icons.Filled.Info),
                    NavItem("privacy", "Privacy", Icons.Filled.Lock),
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination
                            items.forEach { item ->
                                NavigationBarItem(
                                    icon     = { Icon(item.icon, contentDescription = item.label) },
                                    label    = { Text(item.label) },
                                    selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                                    onClick  = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController    = navController,
                        startDestination = "home",
                        modifier         = Modifier.padding(innerPadding),
                    ) {
                        composable("home")    { HomeScreen() }
                        composable("stats")   { StatsScreen() }
                        composable("privacy") { PrivacyScreen() }
                    }
                }
            }
        }
    }
}

/** Lightweight descriptor for a bottom-nav destination. */
private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
