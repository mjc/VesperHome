package com.sergioasenjo.vesperhome.launcher

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

internal fun AppCompatActivity.showMessage(messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
