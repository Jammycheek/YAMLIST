package com.example.nestprogress.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nestprogress.ui.backup.BackupScreen
import com.example.nestprogress.ui.bulkchild.BulkChildCreateScreen
import com.example.nestprogress.ui.history.HistoryScreen
import com.example.nestprogress.ui.movetask.MoveTaskScreen
import com.example.nestprogress.ui.project.ProjectEditScreen
import com.example.nestprogress.ui.project.ProjectListScreen
import com.example.nestprogress.ui.print.PrintScreen
import com.example.nestprogress.ui.settings.SettingsScreen
import com.example.nestprogress.ui.taskdetail.TaskDetailScreen
import com.example.nestprogress.ui.taskedit.TaskEditScreen
import com.example.nestprogress.ui.tasktree.TaskTreeScreen
import com.example.nestprogress.ui.yaml.YamlScreen

object Routes {
    const val PROJECTS = "projects"
    const val PROJECT_EDIT = "project_edit"          // ?projectId=
    const val TASKS = "tasks"                          // /{projectId}
    const val TASK_DETAIL = "task_detail"              // /{taskId}
    const val TASK_EDIT = "task_edit"                  // /{projectId}?taskId=&parentId=
    const val BULK_CHILD = "bulk_child"                // /{parentId}   (SCR-10)
    const val MOVE_TASK = "move_task"                  // /{taskId}     (SCR-11)
    const val YAML = "yaml"                             // ?projectId=
    const val PRINT = "print"                          // /{projectId}
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
}

@Composable
fun NestNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.PROJECTS) {

        composable(Routes.PROJECTS) {
            ProjectListScreen(
                onOpenProject = { id -> navController.navigate("${Routes.TASKS}/$id") },
                onNewProject = { navController.navigate(Routes.PROJECT_EDIT) },
                onEditProject = { id -> navController.navigate("${Routes.PROJECT_EDIT}?projectId=$id") },
                onOpenYaml = { navController.navigate(Routes.YAML) },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(
            route = "${Routes.PROJECT_EDIT}?projectId={projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            ProjectEditScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.TASKS}/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
        ) { entry ->
            val projectId = entry.arguments?.getLong("projectId") ?: return@composable
            TaskTreeScreen(
                onBack = { navController.popBackStack() },
                onOpenTask = { taskId -> navController.navigate("${Routes.TASK_DETAIL}/$taskId") },
                onAddRoot = { navController.navigate("${Routes.TASK_EDIT}/$projectId") },
                onAddChild = { parentId -> navController.navigate("${Routes.TASK_EDIT}/$projectId?parentId=$parentId") },
                onBulkAddChild = { parentId -> navController.navigate("${Routes.BULK_CHILD}/$parentId") },
                onMoveTask = { taskId -> navController.navigate("${Routes.MOVE_TASK}/$taskId") },
                onEditTask = { taskId -> navController.navigate("${Routes.TASK_EDIT}/$projectId?taskId=$taskId") },
                onExportYaml = { navController.navigate("${Routes.YAML}?projectId=$projectId") },
                onPrint = { navController.navigate("${Routes.PRINT}/$projectId") },
            )
        }

        composable(
            route = "${Routes.TASK_DETAIL}/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            TaskDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { projectId, taskId ->
                    navController.navigate("${Routes.TASK_EDIT}/$projectId?taskId=$taskId")
                },
                onAddChild = { projectId, parentId ->
                    navController.navigate("${Routes.TASK_EDIT}/$projectId?parentId=$parentId")
                },
                onBulkAddChild = { parentId ->
                    navController.navigate("${Routes.BULK_CHILD}/$parentId")
                },
                onMoveTask = { taskId ->
                    navController.navigate("${Routes.MOVE_TASK}/$taskId")
                },
            )
        }

        composable(
            route = "${Routes.TASK_EDIT}/{projectId}?taskId={taskId}&parentId={parentId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("parentId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            TaskEditScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.BULK_CHILD}/{parentId}",
            arguments = listOf(navArgument("parentId") { type = NavType.LongType }),
        ) {
            BulkChildCreateScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.MOVE_TASK}/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            MoveTaskScreen(onDone = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.YAML}?projectId={projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            YamlScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "${Routes.PRINT}/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
        ) {
            PrintScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
