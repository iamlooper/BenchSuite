package com.looper.benchsuite.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.looper.android.support.util.AppUtils
import com.looper.benchsuite.R
import com.looper.benchsuite.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun RunScreen(viewModel: MainViewModel?, benchmark: String) {
    viewModel?.updateTitle(stringResource(R.string.benchmarking))
    viewModel?.showBackButton(true)
    viewModel?.showAboutButton(false)

    val context = LocalContext.current
    val isBenchmarked = remember { mutableStateOf(false) }
    val output = remember { mutableStateOf("") }

    if (isBenchmarked.value) {
        viewModel?.updateTitle(stringResource(R.string.benchmarked))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        BasicTextField(
            value = output.value,
            onValueChange = {},
            readOnly = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            )
        )
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val benchmarks =
                if (benchmark == "all") listOf("hackbench", "pipebench", "callbench") else listOf(
                    benchmark
                )

            for (bm in benchmarks) {
                withContext(Dispatchers.Main) {
                    output.value += context.getString(R.string.starting_benchmark, bm) + "\n\n"
                }

                val benchmarkLibPath = AppUtils.getNativeLibraryPath(context, bm)
                val process = if (bm == "hackbench") {
                    Runtime.getRuntime().exec("$benchmarkLibPath -s 2000 -l 2000")
                } else {
                    Runtime.getRuntime().exec(benchmarkLibPath)
                }
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                reader.forEachLine { line ->
                    output.value += line + "\n"
                }

                process.waitFor()

                withContext(Dispatchers.Main) {
                    output.value += "\n" + context.getString(
                        R.string.completed_benchmark,
                        bm
                    ) + "\n\n"
                }
            }

            withContext(Dispatchers.Main) {
                if (benchmark == "all") {
                    output.value += context.getString(R.string.notes) + ":\n" + "- hackbench: " + context.getString(
                        R.string.hackbench_note
                    ) + "\n" +
                            "- pipebench: " + context.getString(R.string.pipebench_note) + "\n" +
                            "- callbench: " + context.getString(R.string.callbench_note)
                } else {
                    when (benchmark) {
                        "hackbench" -> {
                            output.value += context.getString(R.string.note) + ": " + context.getString(
                                R.string.hackbench_note
                            )
                        }

                        "pipebench" -> {
                            output.value += context.getString(R.string.note) + ": " + context.getString(
                                R.string.pipebench_note
                            )
                        }

                        else -> {
                            output.value += context.getString(R.string.note) + ": " + context.getString(
                                R.string.callbench_note
                            )
                        }
                    }
                }
                isBenchmarked.value = true
            }
        }
    }
}


