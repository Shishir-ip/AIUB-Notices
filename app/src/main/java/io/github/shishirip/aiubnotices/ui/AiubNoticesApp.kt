package io.github.shishirip.aiubnotices.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shishirip.aiubnotices.R

private enum class AppScreen { NOTICES, SETTINGS }

/** Single-activity shell with the two screens: notices list and settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiubNoticesApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf(AppScreen.NOTICES) }

    BackHandler(enabled = screen == AppScreen.SETTINGS) {
        screen = AppScreen.NOTICES
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (screen == AppScreen.NOTICES) {
                            stringResource(R.string.app_name)
                        } else {
                            stringResource(R.string.settings_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (screen == AppScreen.SETTINGS) {
                        IconButton(onClick = { screen = AppScreen.NOTICES }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        )
                    }
                },
                actions = {
                    if (screen == AppScreen.NOTICES) {
                        IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh_now),
                            )
                        }
                        IconButton(onClick = { screen = AppScreen.SETTINGS }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Crossfade(targetState = screen, label = "screen") { current ->
                when (current) {
                    AppScreen.NOTICES -> NoticesScreen(state = state, viewModel = viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(state = state, viewModel = viewModel)
                }
            }
        }
    }
}
