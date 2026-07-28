package com.t2v.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.t2v.R
import com.t2v.ui.navigation.Routes

/**
 * Scaffold с верхней панелью и нижней навигацией.
 *
 * Использует самодельную BottomBar вместо Material3 NavigationBar —
 * чтобы не зависеть от experimental API и обеспечить совместимость
 * с разными версиями Compose BOM.
 */
@Composable
fun LTVScaffold(
    nav: NavController,
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStackEntry by nav.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            LTVTopBar(title = title, onBack = onBack)
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = current,
                nav = nav,
            )
        },
        content = content,
    )
}

@Composable
private fun BottomNavBar(
    currentRoute: String?,
    nav: NavController,
) {
    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavButton(nav, currentRoute, Routes.Editor, R.string.nav_editor, Icons.Default.Edit)
            BottomNavButton(nav, currentRoute, Routes.Projects, R.string.nav_projects, Icons.Default.Folder)
            BottomNavButton(nav, currentRoute, Routes.Voices, R.string.nav_voices, Icons.Default.MicNone)
            BottomNavButton(nav, currentRoute, Routes.Models, R.string.nav_models, Icons.Default.Cloud)
            BottomNavButton(nav, currentRoute, Routes.Settings, R.string.nav_settings, Icons.Default.Settings)
        }
    }
}

@Composable
private fun BottomNavButton(
    nav: NavController,
    currentRoute: String?,
    route: String,
    labelRes: Int,
    icon: ImageVector,
) {
    val isSelected = currentRoute?.startsWith(route) == true
    val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .clickable {
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = stringResource(labelRes),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
