package com.mobilecarconnect.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mobilecarconnect.app.ui.ConnectionPrefs
import com.mobilecarconnect.app.ui.DisplayPrefs
import com.mobilecarconnect.app.ui.theme.MobileCarConnectTheme
import com.mobilecarconnect.app.ui.theme.ThemeMode
import com.mobilecarconnect.app.ui.theme.UiProfile
import com.mobilecarconnect.app.ui.theme.UiProfileMode
import com.mobilecarconnect.core.settings.AdvancedSettings
import com.mobilecarconnect.core.settings.ConnectionMethod
import com.mobilecarconnect.core.settings.Control
import com.mobilecarconnect.core.settings.DayNight
import com.mobilecarconnect.core.settings.Picture
import com.mobilecarconnect.core.settings.SettingsCategory
import com.mobilecarconnect.core.settings.UnifiedSettings
import com.mobilecarconnect.core.settings.WifiDetails

/** Fake engine values: plausible defaults for every Advanced and Lab row. */
internal val previewEngineValues: Map<String, String> = AdvancedSettings.all.associate { setting ->
    setting.id to when (val control = setting.control) {
        is Control.Toggle -> (setting.id in setOf("cp_hevc", "cp_focus", "cp_echo", "aa_sink", "aa_stretch")).toString()
        is Control.Choice -> control.options.getOrElse(if (control.options.size > 2) 2 else 0) { control.options.first() }.value
    }
}

/** Settings state for previews and the screenshot gallery. */
fun settingsPreviewUi(
    showAdvanced: Boolean = false,
    lab: Boolean = false,
    paused: Boolean = false,
    schedule: Boolean = false,
    customPicture: Boolean = false,
    byd: Boolean = true,
    loading: Boolean = false,
    /** The chosen car brand; by default BYD when [byd], else none chosen. */
    carBrand: com.mobilecarconnect.core.settings.CarBrand? = null,
    wheelKeys: Map<com.mobilecarconnect.core.settings.WheelAction, Int> = emptyMap(),
): SettingsUi = SettingsUi(
    unified = UnifiedSettings(
        connectionMethod = ConnectionMethod.AUTOMATIC,
        carHotspot = WifiDetails("BYD-Seal-4F2A", "drive2024go"),
        homeWifi = WifiDetails(),
        picture = if (customPicture) Picture.CUSTOM else Picture.AUTOMATIC,
        dayNight = if (schedule) DayNight.SCHEDULE else DayNight.AUTOMATIC,
        showAdvanced = showAdvanced,
        labUnlocked = lab,
        wheelKeys = wheelKeys,
    ),
    connection = ConnectionPrefs(autoConnectUsb = true, autoConnectBluetooth = true, startOnBoot = false, autoConnectPaused = paused),
    display = DisplayPrefs(ThemeMode.FOLLOW_SYSTEM, UiProfileMode.AUTO, 1f),
    joinedWifi = "Garage Wi-Fi",
    isByd = byd,
    carBrand = carBrand ?: if (byd) com.mobilecarconnect.core.settings.CarBrand.BYD else null,
    detectedBrand = null,
    versionName = "1.0.0",
    advancedValues = if (loading) null else previewEngineValues,
)

/** Does nothing; previews and the gallery only render. */
object SettingsPreviewActions : SettingsActions

@Preview(name = "Car landscape", widthDp = 1440, heightDp = 810)
@Composable
private fun SettingsCarPreview() {
    MobileCarConnectTheme(profile = UiProfile.CAR, themeMode = ThemeMode.DARK) {
        SettingsContent(settingsPreviewUi(), SettingsPreviewActions, onBack = {}, startCategory = SettingsCategory.CONNECTION)
    }
}

@Preview(name = "Car landscape, advanced", widthDp = 1440, heightDp = 810)
@Composable
private fun SettingsAdvancedPreview() {
    MobileCarConnectTheme(profile = UiProfile.CAR, themeMode = ThemeMode.DARK) {
        SettingsContent(settingsPreviewUi(showAdvanced = true, lab = true), SettingsPreviewActions, onBack = {},
            startCategory = SettingsCategory.DISPLAY)
    }
}

@Preview(name = "Phone portrait", widthDp = 400, heightDp = 860)
@Composable
private fun SettingsPhonePreview() {
    MobileCarConnectTheme(profile = UiProfile.HANDHELD, themeMode = ThemeMode.DARK) {
        SettingsContent(settingsPreviewUi(), SettingsPreviewActions, onBack = {})
    }
}
