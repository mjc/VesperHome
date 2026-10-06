package com.sergioasenjo.vesperhome.update

import android.app.Activity
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ReleaseUpdateController(
    activity: Activity,
    private val scope: CoroutineScope,
    private val repository: ReleaseUpdateRepository,
    private val currentAppearance: () -> LauncherAppearance,
    onDismissed: () -> Unit
) {
    private var update: ReleaseUpdate? = null
    private var loading = false
    private var failed = false
    private val panelDelegate = lazy { ReleaseUpdatePanel(activity, ::check, onDismissed) }
    private val panel by panelDelegate

    fun show() {
        panel.show(update, loading = false, failed = false, currentAppearance())
        check()
    }

    fun renderAppearance() {
        render()
    }

    fun release() {
        if (panelDelegate.isInitialized()) panel.release()
    }

    private fun check() {
        if (loading) return
        loading = true
        failed = false
        render()
        scope.launch {
            try {
                update = repository.latest()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed = true
            } finally {
                loading = false
                render()
            }
        }
    }

    private fun render() {
        if (panelDelegate.isInitialized() && panel.isShowing) {
            panel.render(update, loading, failed, currentAppearance())
        }
    }
}
