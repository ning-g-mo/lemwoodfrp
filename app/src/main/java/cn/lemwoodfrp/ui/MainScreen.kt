package cn.lemwoodfrp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.lemwoodfrp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val tabs = listOf(
        TabItem(stringResource(R.string.frpc_tab), Icons.Default.DeviceHub),
        TabItem(stringResource(R.string.frps_tab), Icons.Default.Dns),
        TabItem(stringResource(R.string.settings_tab), Icons.Default.Settings),
        TabItem(stringResource(R.string.about_tab), Icons.Default.Info)
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> FRPClientScreen()
                1 -> FRPServerScreen()
                2 -> SettingsScreen()
                3 -> AboutScreen()
            }
        }
    }
}

data class TabItem(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)