package de.ideenwerft.kiez.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.ideenwerft.kiez.ui.theme.LocalKiez

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("feed", "Kiez", Icons.Rounded.Home),
    Tab("mine", "Meine", Icons.Rounded.Forum),
    Tab("channels", "Kanäle", Icons.Rounded.Tag),
    Tab("profile", "Profil", Icons.Rounded.Person),
)

@Composable
fun KiezRoot(vm: KiezViewModel) {
    val k = LocalKiez.current
    val context = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "feed"
    val inTabs = TABS.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    var composing by rememberSaveable { mutableStateOf(false) }
    var choosingPlace by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics(vm.haptics)

    // Helle oder dunkle Statusleiste passend zum gewählten Design
    LaunchedEffect(k.isDark) {
        val style = if (k.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onLocationPermission(context, res.values.any { it })
    }
    val requestLocation = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.onLocationPermission(context, true)
        else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) { if (vm.usesGps()) requestLocation() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Box(Modifier.fillMaxSize().background(k.background)) {
        Column(Modifier.fillMaxSize()) {
            if (inTabs) TopBar(vm) { choosingPlace = true }
            NavHost(
                nav, startDestination = "feed", modifier = Modifier.weight(1f),
                enterTransition = { fadeIn() }, exitTransition = { fadeOut() },
            ) {
                composable("feed") { FeedScreen(vm, openPost = { nav.navigate("post/$it") }, requestLocation = requestLocation) }
                composable("mine") { MineScreen(vm, openPost = { nav.navigate("post/$it") }) }
                composable("channels") { ChannelsScreen(vm) { tag -> vm.openTag(tag); nav.switchTab("feed") } }
                composable("profile") { ProfileScreen(vm) { choosingPlace = true } }
                composable(
                    "post/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally { it } + fadeIn() },
                    popExitTransition = { slideOutHorizontally { it } + fadeOut() },
                ) { e ->
                    DetailScreen(
                        vm, e.arguments?.getLong("id") ?: 0L,
                        back = { nav.popBackStack() },
                        openTag = { tag -> vm.openTag(tag); nav.switchTab("feed") },
                    )
                }
            }
        }

        if (inTabs) {
            BottomBar(
                route,
                onTab = { haptics.tick(); if (it == route && it == "feed") vm.loadFeed() else nav.switchTab(it) },
                onCompose = { haptics.strong(); if (vm.place == null) choosingPlace = true else composing = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (inTabs) 92.dp else 80.dp),
        ) { Snackbar(it, containerColor = k.text, contentColor = k.background, shape = RoundedCornerShape(14.dp)) }
    }

    if (composing) ComposeSheet(vm) { composing = false }
    if (choosingPlace) LocationSheet(vm, useGps = requestLocation) { choosingPlace = false }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun TopBar(vm: KiezViewModel, choosePlace: () -> Unit) {
    val k = LocalKiez.current
    Surface(color = k.glass, shadowElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(k.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Anchor, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("KIEZ", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Bold), color = k.text)
            Spacer(Modifier.weight(1f))
            Surface(onClick = choosePlace, shape = CircleShape, color = k.muted, contentColor = k.text) {
                Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, tint = k.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (vm.locating) "Suche …" else vm.place?.label ?: "Ort wählen",
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = k.primarySoft, contentColor = k.primary) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, "Karma", Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${vm.karma}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(route: String, onTab: (String) -> Unit, onCompose: () -> Unit, modifier: Modifier) {
    val k = LocalKiez.current
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = k.glass, shadowElevation = 10.dp,
        border = BorderStroke(1.dp, k.border),
    ) {
        Row(Modifier.height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            TABS.take(2).forEach { TabItem(it, it.route == route, Modifier.weight(1f)) { onTab(it.route) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Surface(onClick = onCompose, shape = RoundedCornerShape(18.dp), color = k.primary, contentColor = Color.White, shadowElevation = 6.dp) {
                    Icon(Icons.Rounded.Add, "Neuer Post", Modifier.padding(12.dp).size(26.dp))
                }
            }
            TABS.drop(2).forEach { TabItem(it, it.route == route, Modifier.weight(1f)) { onTab(it.route) } }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = LocalKiez.current
    val color by animateColorAsState(if (selected) k.primary else k.mutedText, label = "tab")
    val bg by animateColorAsState(if (selected) k.primarySoft else Color.Transparent, label = "tabbg")
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 14.dp, vertical = 4.dp)) {
            Icon(tab.icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
