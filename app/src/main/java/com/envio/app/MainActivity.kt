package com.envio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.envio.app.ui.AddContactScreen
import com.envio.app.ui.ChatScreen
import com.envio.app.ui.ChatsScreen
import com.envio.app.ui.EnvioTheme
import com.envio.app.ui.WelcomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EnvioTheme { AppNav() } }
    }
}

@Composable
fun AppNav() {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val start = if (ctx.core.identity.isSetup && Perms.allGranted(ctx)) "chats" else "welcome"

    NavHost(navController = nav, startDestination = start) {
        composable("welcome") {
            WelcomeScreen(onDone = {
                nav.navigate("chats") { popUpTo("welcome") { inclusive = true } }
            })
        }
        composable("chats") {
            ChatsScreen(
                onOpen = { id -> nav.navigate("chat/$id") },
                onAdd = { nav.navigate("add") }
            )
        }
        composable("add") {
            AddContactScreen(
                onBack = { nav.popBackStack() },
                onAdded = { id -> nav.navigate("chat/$id") { popUpTo("chats") } }
            )
        }
        composable(
            "chat/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            ChatScreen(
                contactId = entry.arguments?.getString("id") ?: "",
                onBack = { nav.popBackStack() }
            )
        }
    }
}
