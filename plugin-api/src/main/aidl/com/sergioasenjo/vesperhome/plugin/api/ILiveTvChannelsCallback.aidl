package com.sergioasenjo.vesperhome.plugin.api;

import android.os.Bundle;

interface ILiveTvChannelsCallback {
    void onChannels(in List<Bundle> channels);
    void onError(String message);
}
