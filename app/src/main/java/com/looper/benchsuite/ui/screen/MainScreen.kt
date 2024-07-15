package com.looper.benchsuite.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.looper.benchsuite.R
import com.looper.benchsuite.ui.component.BenchmarkPreferenceComponent
import com.looper.benchsuite.viewmodel.MainViewModel

@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel?) {
    viewModel?.updateTitle(stringResource(R.string.app_name))
    viewModel?.showBackButton(false)
    viewModel?.showAboutButton(true)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            BenchmarkPreferenceComponent(
                context = LocalContext.current,
                title = "hackbench",
                summary = stringResource(R.string.hackbench_summary),
                sourceLink = "https://git.kernel.org/pub/scm/utils/rt-tests/rt-tests.git/tree/src/hackbench/hackbench.c",
                benchmarkType = stringResource(R.string.scheduler_throughput)
            ) {
                navController.navigate("run/hackbench")
            }
            BenchmarkPreferenceComponent(
                context = LocalContext.current,
                title = "pipebench",
                summary = stringResource(R.string.pipebench_summary),
                sourceLink = "https://github.com/iamlooper/BenchSuite/blob/libs/src/pipebench.c",
                benchmarkType = stringResource(R.string.scheduler_latency)
            ) {
                navController.navigate("run/pipebench")
            }
            BenchmarkPreferenceComponent(
                context = LocalContext.current,
                title = "callbench",
                summary = stringResource(R.string.callbench_summary),
                sourceLink = "https://github.com/kdrag0n/callbench/blob/master/callbench.c",
                benchmarkType = stringResource(R.string.syscalls_and_i_o_speed)
            ) {
                navController.navigate("run/callbench")
            }

            Spacer(modifier = Modifier.height(96.dp))
        }

        FloatingActionButton(
            onClick = { navController.navigate("run/all") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(
                    R.string.run_all_benchmarks
                )
            )
        }
    }
}