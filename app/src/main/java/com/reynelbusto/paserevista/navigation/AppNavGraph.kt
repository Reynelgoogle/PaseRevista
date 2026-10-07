package com.reynelbusto.paserevista.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.presentation.board.BoardScreen
import com.reynelbusto.paserevista.presentation.camas.CamasScreen
import com.reynelbusto.paserevista.presentation.more.MoreScreen

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.CAMAS, "Camas", Icons.Filled.Home),
    BottomDestination(Routes.BOARD, "Pizarra", Icons.Filled.DateRange),
    BottomDestination(Routes.MORE, "Más", Icons.Filled.MoreVert),
)

/** Navegación principal: 3 destinos con barra inferior Material 3. */
@Composable
fun AppNavGraph(container: AppContainer) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by navController.currentBackStackEntryAsState()
                val current = backStack?.destination
                BOTTOM_DESTINATIONS.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAMAS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CAMAS) { CamasScreen(container = container) }
            composable(Routes.BOARD) { BoardScreen(container = container) }
            composable(Routes.MORE) { MoreScreen(container = container) }
        }
    }
}
