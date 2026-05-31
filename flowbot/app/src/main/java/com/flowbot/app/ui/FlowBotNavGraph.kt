package com.flowbot.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object FlowBotRoutes {
    const val MAIN = "main"
    const val WORKFLOW_DETAIL = "workflow/{fileName}"
    const val RUN_LOG = "log/{runId}"

    fun workflowDetail(fileName: String) = "workflow/$fileName"
    fun runLog(runId: String) = "log/$runId"
}

@Composable
fun FlowBotNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = FlowBotRoutes.MAIN) {

        composable(FlowBotRoutes.MAIN) {
            MainScreen(
                onWorkflowClick = { fileName ->
                    navController.navigate(FlowBotRoutes.workflowDetail(fileName))
                },
            )
        }

        composable(
            route = FlowBotRoutes.WORKFLOW_DETAIL,
            arguments = listOf(navArgument("fileName") { type = NavType.StringType }),
        ) {
            WorkflowDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onViewLog = { runId ->
                    navController.navigate(FlowBotRoutes.runLog(runId))
                },
            )
        }

        composable(
            route = FlowBotRoutes.RUN_LOG,
            arguments = listOf(navArgument("runId") { type = NavType.StringType }),
        ) {
            RunLogScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
