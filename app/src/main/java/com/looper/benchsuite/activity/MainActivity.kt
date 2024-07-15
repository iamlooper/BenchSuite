package com.looper.benchsuite.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.looper.benchsuite.R
import com.looper.benchsuite.ui.dialog.AboutDialog
import com.looper.benchsuite.ui.screen.MainScreen
import com.looper.benchsuite.ui.screen.RunScreen
import com.looper.benchsuite.ui.theme.AppTheme
import com.looper.benchsuite.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Content(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Content(viewModel: MainViewModel? = null) {
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val title = viewModel?.title?.observeAsState(stringResource(R.string.app_name))
    val showBackButton = viewModel?.showBackButton?.observeAsState(false)
    val showAboutButton = viewModel?.showAboutButton?.observeAsState(true)
    val showAboutDialog = remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = title?.value ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showBackButton?.value == true) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(
                                    R.string.back
                                )
                            )
                        }
                    }
                },
                actions = {
                    if (showAboutButton?.value == true) {
                        IconButton(onClick = { showAboutDialog.value = true }) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.ic_info),
                                contentDescription = stringResource(R.string.about)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") { MainScreen(navController, viewModel) }
                    composable("run/{benchmark}") { backStackEntry ->
                        val benchmark = backStackEntry.arguments?.getString("benchmark") ?: ""
                        RunScreen(viewModel, benchmark)
                    }
                }
            }
        }
    }

    if (showAboutDialog.value) {
        AboutDialog(
            onDismissRequest = {
                showAboutDialog.value = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ContentPreview() {
    AppTheme {
        Content()
    }
}