package com.sergioasenjo.vesperhome.launcher

import androidx.appcompat.app.AppCompatActivity
import com.sergioasenjo.vesperhome.screensaver.ScreensaverController
import com.sergioasenjo.vesperhome.screensaver.SystemScreensaverRepository
import com.sergioasenjo.vesperhome.status.StatusBarController
import com.sergioasenjo.vesperhome.status.StatusBarSettingsAction

internal fun createScreensaverController(
    activity: AppCompatActivity,
    viewModel: LauncherViewModel,
    statusBarController: StatusBarController,
    systemRepository: SystemScreensaverRepository
): ScreensaverController = ScreensaverController(
    activity = activity,
    currentSettings = { viewModel.uiState.value.screensaver },
    systemRepository = systemRepository,
    setStartDelay = viewModel::setScreensaverStartDelay,
    setStandbyDelay = viewModel::setScreensaverStandbyDelay,
    setClockStyle = viewModel::setScreensaverClockStyle,
    setBackButtonAction = viewModel::setBackButtonAction,
    chooseDateFormat = { statusBarController.handleSettingsAction(StatusBarSettingsAction.ChooseDateFormat) },
    chooseTimeFormat = { statusBarController.handleSettingsAction(StatusBarSettingsAction.ChooseTimeFormat) }
)
