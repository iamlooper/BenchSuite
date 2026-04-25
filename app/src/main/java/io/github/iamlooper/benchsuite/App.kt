package io.github.iamlooper.benchsuite

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.HiltAndroidApp
import io.github.iamlooper.benchsuite.engine.BenchmarkEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class. Hilt entry point and engine initialisation host.
 *
 * The [BenchmarkEngine] is initialised on app start in a background coroutine.
 * It is safe to call from any Activity/ViewModel because Hilt injects the same
 * singleton instance everywhere, and [BenchmarkEngine.initialize] uses a state
 * machine to guard against double-init.
 *
 * [ProcessLifecycleOwner] handles teardown and reinit on real hardware. ON_STOP fires when the
 * last activity leaves the started state; ON_START fires when any activity re-enters it.
 * [Application.onTerminate] is only called in the emulator, never on production devices.
 */
@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var engine: BenchmarkEngine

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            try {
                engine.initialize()
            } catch (e: UnsatisfiedLinkError) {
                // Engine stays in UNINITIALIZED
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                engine.teardown()
            }
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    try {
                        engine.initialize()
                    } catch (e: UnsatisfiedLinkError) {
                        // Engine stays in UNINITIALIZED
                    }
                }
            }
        })
    }
}
