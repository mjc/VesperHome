package com.sergioasenjo.vesperhome.plugin.api;

import android.os.Bundle;

interface ILiveTvPlaybackCallback {
    void onPlayback(in Bundle playback);
    void onError(String message);
}
