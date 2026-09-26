package com.sergioasenjo.vesperhome.plugin.api;

import com.sergioasenjo.vesperhome.plugin.api.ILiveTvConnectionCallback;

interface ILiveTvConnectionProvider {
    void requestConnection(ILiveTvConnectionCallback callback);
}
