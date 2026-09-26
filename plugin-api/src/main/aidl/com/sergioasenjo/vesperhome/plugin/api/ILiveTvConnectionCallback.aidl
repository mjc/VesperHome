package com.sergioasenjo.vesperhome.plugin.api;

import android.os.Bundle;

interface ILiveTvConnectionCallback {
    void onConnection(in Bundle connection);
    void onUnavailable();
}
