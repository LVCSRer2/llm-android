package com.example.gemma

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gemma.ui.theme.GemmaChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GemmaChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GemmaChatApp()
                }
            }
        }
    }
}

@Composable
fun GemmaChatApp() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = viewModel()

    NavHost(navController = navController, startDestination = "select") {
        composable("select") {
            ModelSelectScreen(
                onModelSelected = { modelType ->
                    chatViewModel.modelType = modelType
                    if (ModelDownloader.modelExists(navController.context, modelType)) {
                        chatViewModel.loadModel(modelType)
                        navController.navigate("chat") {
                            popUpTo("select") { inclusive = true }
                        }
                    } else {
                        navController.navigate("download/${modelType.name}")
                    }
                }
            )
        }

        composable("download/{modelName}") { backStackEntry ->
            val modelName = backStackEntry.arguments?.getString("modelName") ?: return@composable
            val modelType = ModelType.valueOf(modelName)

            DownloadScreen(
                modelType = modelType,
                onDownloadComplete = {
                    chatViewModel.loadModel(modelType)
                    navController.navigate("chat") {
                        popUpTo("select") { inclusive = true }
                    }
                }
            )
        }

        composable("download_from_settings/{modelName}") { backStackEntry ->
            val modelName = backStackEntry.arguments?.getString("modelName") ?: return@composable
            val modelType = ModelType.valueOf(modelName)

            DownloadScreen(
                modelType = modelType,
                onDownloadComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable("chat") {
            ChatScreen(
                chatViewModel = chatViewModel,
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                currentSettings = chatViewModel.settings,
                currentModel = chatViewModel.modelType,
                onSave = { newSettings, newModel ->
                    chatViewModel.updateSettings(newSettings, newModel)
                    navController.popBackStack()
                },
                onBack = {
                    navController.popBackStack()
                },
                onDownload = { modelType ->
                    navController.navigate("download_from_settings/${modelType.name}")
                }
            )
        }
    }
}
