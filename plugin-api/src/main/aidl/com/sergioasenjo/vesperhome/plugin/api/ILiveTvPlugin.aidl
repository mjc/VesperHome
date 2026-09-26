package com.sergioasenjo.vesperhome.plugin.api;

import android.os.Bundle;
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvChannelsCallback;
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionProvider;
import com.sergioasenjo.vesperhome.plugin.api.ILiveTvPlaybackCallback;

interface ILiveTvPlugin {
    int getApiVersion();
    void loadChannels(ILiveTvConnectionProvider connectionProvider, ILiveTvChannelsCallback callback);
    void resolvePlayback(String channelId, ILiveTvConnectionProvider connectionProvider, ILiveTvPlaybackCallback callback);
    void stopPlayback(in Bundle playback, ILiveTvConnectionProvider connectionProvider);
}
