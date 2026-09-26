package com.sergioasenjo.vesperhome.livetv

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.databinding.ActivityLauncherBinding
import com.sergioasenjo.vesperhome.databinding.ViewLiveTvBinding
import com.sergioasenjo.vesperhome.settings.LauncherAppearance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LiveTvController(
    private val activity: AppCompatActivity,
    private val launcherBinding: ActivityLauncherBinding,
    private val repository: LiveTvPluginRepository,
    private val preferences: LiveTvPreferencesRepository,
    private val currentAppearance: () -> LauncherAppearance,
    private val onPlaybackStarting: () -> Unit
) {
    private val channelAdapter = LiveTvChannelAdapter(::playChannel) { position, event ->
        channelNavigator?.onKey(position, event) ?: false
    }
    private val focusGuard = LiveTvFocusGuard(launcherBinding.root)
    private var binding: ViewLiveTvBinding? = null
    private var channelNavigator: LiveTvChannelGridNavigator? = null
    private var player: ExoPlayer? = null
    private var pluginState = LiveTvPluginState()
    private var lastChannel: LastLiveTvChannel? = null
    private var selectedChannel: LiveTvChannel? = null
    private var currentPlayback: LiveTvPlayback? = null
    private var fullscreenJob: Job? = null
    private var openChannelWhenLoaded = false
    private var selectionVersion = 0
    private var fullscreen = false
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val view = binding ?: return
            view.playerLoading.isVisible = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY && player?.playWhenReady == true) scheduleFullscreen()
        }

        override fun onPlayerError(error: PlaybackException) {
            showPlaybackError()
        }
    }

    fun start() {
        launcherBinding.statusLiveTv.setOnClickListener { showBrowser() }
        repository.start()
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { repository.state.collect(::render) }
                launch { preferences.lastChannel.collect { lastChannel = it } }
            }
        }
    }

    fun refresh() = repository.refresh()

    fun setAppearance(appearance: LauncherAppearance) {
        val view = binding ?: return
        val palette = appearance.palette
        channelAdapter.setAppearance(appearance)
        view.root.setBackgroundColor(palette.backgroundEnd.withAlpha(245))
        view.browserTitle.setTextColor(palette.primaryText)
        view.channelsEmpty.setTextColor(palette.secondaryText)
        view.channelsLoading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
        view.playerLoading.indeterminateTintList = ColorStateList.valueOf(palette.focus)
        view.playerContainer.strokeColor = palette.focus
        val buttonBackground = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedSurface, Color.TRANSPARENT)
        )
        val buttonText = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(palette.focusedText, palette.primaryText)
        )
        view.closeLiveTv.backgroundTintList = buttonBackground
        view.closeLiveTv.iconTint = buttonText
        view.root.isSoundEffectsEnabled = appearance.keyClickSounds
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val channelDirection = when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> 1
            KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
            else -> null
        }
        val view = binding
        if (view?.root?.isShown == true) {
            if (view.playerContainer.isShown) {
                if (channelDirection != null) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        switchChannel(channelDirection)
                    }
                    return true
                }
                if (event.keyCode in LIVE_TV_DIRECTIONAL_DPAD_KEYS) return true
            }
            return false
        }
        if (channelDirection == null || !pluginState.available) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (pluginState.loading && pluginState.channels.isEmpty()) {
                openChannelWhenLoaded = true
            } else {
                openLastChannelOrBrowser()
            }
        }
        return true
    }

    fun handleBack(): Boolean {
        val view = binding ?: return false
        if (!view.root.isShown) return false
        when {
            fullscreen -> {
                fullscreenJob?.cancel()
                setFullscreen(false)
                view.playerContainer.requestFocus()
            }

            view.playerContainer.isShown -> showBrowser()

            else -> close()
        }
        return true
    }

    fun onHostStopped() {
        if (binding?.root?.isShown == true) close()
    }

    fun release() {
        close()
        player?.removeListener(playerListener)
        binding?.player?.player = null
        player?.release()
        player = null
        repository.release()
    }

    private fun render(state: LiveTvPluginState) {
        pluginState = state
        launcherBinding.statusLiveTv.isVisible = state.channels.isNotEmpty()
        if (openChannelWhenLoaded && !state.loading) {
            openChannelWhenLoaded = false
            openLastChannelOrBrowser()
        }
        val view = binding ?: return
        channelAdapter.submitList(state.channels)
        view.channelsLoading.isVisible = state.loading && state.channels.isEmpty()
        view.channelsEmpty.isVisible = !state.loading && state.channels.isEmpty()
        if (!state.available && view.root.isShown) close()
    }

    private fun showBrowser() {
        if (!pluginState.available) return
        val view = ensureBinding()
        stopCurrentPlayback()
        view.root.isVisible = true
        view.channelBrowser.isVisible = true
        view.closeLiveTv.requestFocus()
        blockLauncherFocus()
        view.playerContainer.isVisible = false
        setFullscreen(false)
        channelNavigator?.reset()
        channelAdapter.submitList(pluginState.channels) {
            channelNavigator?.focusFirst()
        }
    }

    private fun playChannel(channel: LiveTvChannel) {
        val view = ensureBinding()
        stopCurrentPlayback()
        onPlaybackStarting()
        selectedChannel = channel
        lastChannel = LastLiveTvChannel(channel.plugin, channel.id)
        activity.lifecycleScope.launch { preferences.setLastChannel(channel) }
        val version = selectionVersion
        view.root.isVisible = true
        view.playerContainer.isVisible = true
        view.playerContainer.requestFocus()
        blockLauncherFocus()
        view.channelBrowser.isVisible = false
        view.playerLoading.isVisible = true
        view.playerError.isVisible = false
        view.channelName.text = channel.number?.let { number -> "$number  ${channel.name}" } ?: channel.name
        view.channelProgram.text = channel.currentProgram.orEmpty()
        view.channelProgram.isVisible = !channel.currentProgram.isNullOrBlank()
        setFullscreen(false)
        activity.lifecycleScope.launch {
            repository.resolvePlayback(channel).fold(
                onSuccess = { playback ->
                    if (version != selectionVersion) {
                        repository.stopPlayback(playback)
                        return@fold
                    }
                    currentPlayback = playback
                    ensurePlayer().apply {
                        setMediaItem(MediaItem.fromUri(playback.url))
                        prepare()
                        play()
                    }
                },
                onFailure = {
                    if (version == selectionVersion) showPlaybackError()
                }
            )
        }
    }

    private fun switchChannel(direction: Int) {
        val channels = pluginState.channels
        val current = selectedChannel ?: return
        val index = channels.indexOfFirst { it.plugin == current.plugin && it.id == current.id }
        if (index < 0 || channels.isEmpty()) return
        playChannel(channels[(index + direction + channels.size) % channels.size])
    }

    private fun openLastChannelOrBrowser() {
        val channel = lastChannel?.let { previous ->
            pluginState.channels.firstOrNull { item ->
                item.plugin == previous.plugin && item.id == previous.channelId
            }
        }
        if (channel == null) showBrowser() else playChannel(channel)
    }

    private fun scheduleFullscreen() {
        if (fullscreenJob != null || fullscreen || selectedChannel == null) return
        val version = selectionVersion
        fullscreenJob = activity.lifecycleScope.launch {
            delay(FULLSCREEN_DELAY_MS)
            if (version == selectionVersion && binding?.playerContainer?.isShown == true) setFullscreen(true)
        }
    }

    private fun setFullscreen(fullscreen: Boolean) {
        val view = binding ?: return
        this.fullscreen = fullscreen
        val horizontalMargin = if (fullscreen) 0 else dp(PREVIEW_HORIZONTAL_MARGIN_DP)
        val verticalMargin = if (fullscreen) 0 else dp(PREVIEW_VERTICAL_MARGIN_DP)
        view.playerContainer.layoutParams = (view.playerContainer.layoutParams as FrameLayout.LayoutParams).apply {
            setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
        }
        view.playerContainer.radius = if (fullscreen) 0f else dp(PREVIEW_RADIUS_DP).toFloat()
        view.playerContainer.strokeWidth = if (fullscreen) 0 else dp(PREVIEW_STROKE_DP)
        view.channelInfo.isVisible = !fullscreen
    }

    private fun showPlaybackError() {
        fullscreenJob?.cancel()
        fullscreenJob = null
        binding?.let { view ->
            view.playerLoading.isVisible = false
            view.playerError.setText(R.string.live_tv_playback_failed)
            view.playerError.isVisible = true
        }
        currentPlayback?.let(repository::stopPlayback)
        currentPlayback = null
        player?.stop()
        player?.clearMediaItems()
    }

    private fun stopCurrentPlayback() {
        selectionVersion += 1
        fullscreenJob?.cancel()
        fullscreenJob = null
        setFullscreen(false)
        player?.stop()
        player?.clearMediaItems()
        currentPlayback?.let(repository::stopPlayback)
        currentPlayback = null
        selectedChannel = null
    }

    private fun close() {
        stopCurrentPlayback()
        channelNavigator?.reset()
        binding?.root?.isVisible = false
        focusGuard.restore()
        launcherBinding.statusLiveTv.takeIf(View::isShown)?.requestFocus()
    }

    private fun blockLauncherFocus() {
        val overlay = binding?.root ?: return
        focusGuard.blockOutside(overlay)
    }

    private fun ensureBinding(): ViewLiveTvBinding {
        binding?.let { return it }
        val created = ViewLiveTvBinding.bind(launcherBinding.liveTvStub.inflate())
        binding = created
        created.channels.layoutManager = GridLayoutManager(activity, CHANNEL_COLUMNS)
        created.channels.adapter = channelAdapter
        created.channels.itemAnimator = null
        channelNavigator = LiveTvChannelGridNavigator(created.channels, created.closeLiveTv, CHANNEL_COLUMNS)
        created.closeLiveTv.setOnClickListener { close() }
        setAppearance(currentAppearance())
        render(pluginState)
        return created
    }

    private fun ensurePlayer(): ExoPlayer = player ?: ExoPlayer.Builder(activity).build().also { created ->
        player = created
        created.repeatMode = Player.REPEAT_MODE_ONE
        created.addListener(playerListener)
        binding?.player?.player = created
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun Int.withAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private companion object {
        const val CHANNEL_COLUMNS = 3
        const val FULLSCREEN_DELAY_MS = 10_000L
        const val PREVIEW_HORIZONTAL_MARGIN_DP = 56
        const val PREVIEW_VERTICAL_MARGIN_DP = 40
        const val PREVIEW_RADIUS_DP = 18
        const val PREVIEW_STROKE_DP = 2
    }
}
