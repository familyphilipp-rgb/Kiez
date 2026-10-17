package de.ideenwerft.kiez

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import de.ideenwerft.kiez.ui.KiezRoot
import de.ideenwerft.kiez.ui.KiezViewModel
import de.ideenwerft.kiez.ui.theme.KiezTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: KiezViewModel = viewModel()
            KiezTheme(vm.theme) { KiezRoot(vm) }
        }
    }
}
