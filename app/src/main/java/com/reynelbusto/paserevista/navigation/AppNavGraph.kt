package com.reynelbusto.paserevista.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.reynelbusto.paserevista.di.AppContainer
import com.reynelbusto.paserevista.presentation.board.BoardScreen
import com.reynelbusto.paserevista.presentation.chart.PatientChartScreen
import com.reynelbusto.paserevista.presentation.history.HistoryScreen
import com.reynelbusto.paserevista.presentation.home.HomeScreen
import com.reynelbusto.paserevista.presentation.more.MoreScreen
import com.reynelbusto.paserevista.presentation.patients.PatientsScreen
import com.reynelbusto.paserevista.presentation.pending.PendingsScreen

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_DESTINATIONS = listOf(
    BottomDestination(Routes.HOME, "Hoy", Icons.Filled.Home),
    BottomDestination(Routes.BOARD, "Pizarra", Icons.Filled.DateRange),
    BottomDestination(Routes.PATIENTS, "Pacientes", Icons.Filled.Person),
    BottomDestination(Routes.PENDINGS, "Pendientes", Icons.AutoMirrored.Filled.List),
    BottomDestination(Routes.MORE, "Más", Icons.Filled.MoreVert),
)

/** Navegación principal: 5 destinos con barra inferior Material 3. */
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
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onOpenPatient = { patientId -> navController.navigate(Routes.chart(patientId)) },
                )
            }
            composable(Routes.BOARD) {
                BoardScreen(
                    container = container,
                    onOpenPatient = { patientId -> navController.navigate(Routes.chart(patientId)) },
                )
            }
            composable(Routes.PATIENTS) {
                PatientsScreen(
                    container = container,
                    onOpenPatient = { patientId -> navController.navigate(Routes.chart(patientId)) },
                )
            }
            composable(Routes.PENDINGS) {
                PendingsScreen(
                    container = container,
                    onOpenPatient = { patientId -> navController.navigate(Routes.chart(patientId)) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    container = container,
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CHART,
                arguments = listOf(navArgument("patientId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                PatientChartScreen(
                    container = container,
                    patientId = patientId,
                    onBack = { navController.popBackStack() },
                    onOpenPatient = { nextId ->
                        // Siguiente paciente: reemplaza la ficha actual (sin pila infinita).
                        navController.navigate(Routes.chart(nextId)) {
                            popUpTo(Routes.CHART) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
