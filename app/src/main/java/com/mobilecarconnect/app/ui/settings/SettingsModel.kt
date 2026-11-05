package com.mobilecarconnect.app.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.mobilecarconnect.app.R
import com.mobilecarconnect.app.ui.ConnectionPrefs
import com.mobilecarconnect.app.ui.DisplayPrefs
import com.mobilecarconnect.app.ui.Route
import com.mobilecarconnect.app.ui.theme.ThemeMode
import com.mobilecarconnect.app.ui.theme.UiProfileMode
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.settings.AdvancedSetting
import com.mobilecarconnect.core.settings.AdvancedSettings
import com.mobilecarconnect.core.settings.ConnectionMethod
import com.mobilecarconnect.core.settings.Control
import com.mobilecarconnect.core.settings.DayNight
import com.mobilecarconnect.core.settings.InterfaceSize
import com.mobilecarconnect.core.settings.Picture
import com.mobilecarconnect.core.settings.SettingsCategory
import com.mobilecarconnect.core.settings.Tier
import com.mobilecarconnect.core.settings.UnifiedSettings
import com.mobilecarconnect.core.settings.CarBrand
import com.mobilecarconnect.core.settings.VehicleProfile
import java.util.Locale
import kotlin.math.abs

/**
 * Everything Settings renders (design/RESEARCH_PREMIUM_UX.md §8), so previews and the screenshot
 * gallery can draw it without a ViewModel or engines.
 */
@Immutable
data class SettingsUi(
    val unified: UnifiedSettings,
    val connection: ConnectionPrefs,
    val display: DisplayPrefs,
    /** The car's current Wi-Fi when readable: offered as a one-tap fill for "Same Wi-Fi". */
    val joinedWifi: String?,
    /** BYD head units also show directions on the HUD and instrument cluster. */
    val isByd: Boolean,
    val versionName: String,
    /**
     * The engines' current values of Advanced and Lab settings by [AdvancedSetting.id]: "true" or
     * "false" for switches, the option value for choices. Null while they load from the engines.
     */
    val advancedValues: Map<String, String>?,
    /** Android lets MobileCarConnect draw over other apps (the floating switcher needs it). */
    val overlayAllowed: Boolean = true,
    /** Settings › Car › Car brand: the driver's choice, and what this screen looks like. */
    /** The driver's car brand; null until they choose. */
    val carBrand: CarBrand? = null,
    /** BYD on a BYD DiLink unit, else null. */
    val detectedBrand: CarBrand? = null,
    /** This stereo's Bluetooth looks like an external module (detect/ExternalBluetoothProbe found signs of one). */
    val stereoBluetoothExternal: Boolean = false,
    /** That module is FYT's, whose own "Car Link" app (com.syu.carlink) must be disabled for the module route. */
    val stereoBluetoothFyt: Boolean = false,
    /** "CarPlay" / "Android Auto" while a session shows a picture: Settings offers the way back to it. */
    val liveEngine: String? = null,
)

/** What Settings can change; the ViewModel implements it, previews fake it. */
interface SettingsActions {
    /** Changes the unified settings; both engines receive them. */
    fun update(change: (UnifiedSettings) -> UnifiedSettings) {}
    fun setAutoConnectBluetooth(on: Boolean) {}
    fun setAutoConnectUsb(on: Boolean) {}
    fun setStartOnBoot(on: Boolean) {}
    /** CarPlay only (MobileCarConnectPreferences.carPlayOnly): Android Auto never loads. */
    fun setCarPlayOnly(on: Boolean) {}
    fun setFloatingSwitcher(on: Boolean) {}
    /** "Wireless through the stereo's Bluetooth module (experimental)" (MobileCarConnectPreferences.wirelessOnExternalBluetooth). */
    fun setWirelessOnExternalBluetooth(on: Boolean) {}
    /** Root mode (MobileCarConnectPreferences.rootMode). */
    fun setRootMode(on: Boolean) {}
    /** Android's "Display over other apps" screen for MobileCarConnect. */
    fun openOverlayPermission() {}
    fun resumeAutoConnect() {}
    fun setThemeMode(mode: ThemeMode) {}
    fun setUiProfileMode(mode: UiProfileMode) {}
    fun setInterfaceScale(scale: Float) {}
    fun setRenderingMode(mode: com.mobilecarconnect.app.ui.theme.RenderingMode) {}
    /** Writes one engine value ("true"/"false" or an option value) through [AdvancedSetting.control]. */
    fun setAdvanced(setting: AdvancedSetting, value: String) {}
    fun openBluetoothSettings() {}
    fun openAppSettings() {}
    /** Stores the car brand (applies from the next connection). */
    fun setCarBrand(brand: CarBrand) {}
    fun navigate(route: Route) {}
    /** Brings the live projection back to the front. */
    fun returnToProjection() {}
    /** The car hotspot read from the stereo itself, or null where Android does not allow it. */
    suspend fun readCarHotspot(): com.mobilecarconnect.core.settings.WifiDetails? = null
    /** A saved Wi-Fi network's password (rooted stereos only), or null. */
    suspend fun readSavedWifiPassword(ssid: String): String? = null
    /** Saves the car hotspot, and applies it to the car's own hotspot where Android allows it. */
    fun saveCarHotspot(details: com.mobilecarconnect.core.settings.WifiDetails) = update { it.copy(carHotspot = details) }
}

/** Rail and grid order. */
internal val categoryOrder = listOf(
    SettingsCategory.CONNECTION, SettingsCategory.DISPLAY, SettingsCategory.SOUND,
    SettingsCategory.NAVIGATION, SettingsCategory.CAR, SettingsCategory.GENERAL,
)

@get:StringRes
internal val SettingsCategory.title: Int
    get() = when (this) {
        SettingsCategory.CONNECTION -> R.string.mobilecarconnect_settings_cat_connection
        SettingsCategory.DISPLAY -> R.string.mobilecarconnect_settings_cat_display
        SettingsCategory.SOUND -> R.string.mobilecarconnect_settings_cat_sound
        SettingsCategory.NAVIGATION -> R.string.mobilecarconnect_settings_cat_navigation
        SettingsCategory.CAR -> R.string.mobilecarconnect_settings_cat_car
        SettingsCategory.GENERAL -> R.string.mobilecarconnect_settings_cat_general
    }

@get:StringRes
internal val SettingsCategory.summary: Int
    get() = when (this) {
        SettingsCategory.CONNECTION -> R.string.mobilecarconnect_settings_cat_connection_summary
        SettingsCategory.DISPLAY -> R.string.mobilecarconnect_settings_cat_display_summary
        SettingsCategory.SOUND -> R.string.mobilecarconnect_settings_cat_sound_summary
        SettingsCategory.NAVIGATION -> R.string.mobilecarconnect_settings_cat_navigation_summary
        SettingsCategory.CAR -> R.string.mobilecarconnect_settings_cat_car_summary
        SettingsCategory.GENERAL -> R.string.mobilecarconnect_settings_cat_general_summary
    }

@get:DrawableRes
internal val SettingsCategory.icon: Int
    get() = when (this) {
        SettingsCategory.CONNECTION -> R.drawable.mobilecarconnect_ic_link
        SettingsCategory.DISPLAY -> R.drawable.mobilecarconnect_ic_display_settings
        SettingsCategory.SOUND -> R.drawable.mobilecarconnect_ic_volume_up
        SettingsCategory.NAVIGATION -> R.drawable.mobilecarconnect_ic_navigation
        SettingsCategory.CAR -> R.drawable.mobilecarconnect_ic_directions_car
        SettingsCategory.GENERAL -> R.drawable.mobilecarconnect_ic_tune
    }

@get:DrawableRes
internal val SettingsCategory.selectedIcon: Int
    get() = when (this) {
        SettingsCategory.CONNECTION -> R.drawable.mobilecarconnect_ic_link_filled
        SettingsCategory.DISPLAY -> R.drawable.mobilecarconnect_ic_display_settings_filled
        SettingsCategory.SOUND -> R.drawable.mobilecarconnect_ic_volume_up_filled
        SettingsCategory.NAVIGATION -> R.drawable.mobilecarconnect_ic_navigation_filled
        SettingsCategory.CAR -> R.drawable.mobilecarconnect_ic_directions_car_filled
        SettingsCategory.GENERAL -> R.drawable.mobilecarconnect_ic_tune_filled
    }

/** The bottom sheets Settings opens; saved by [key] so a rotation keeps the sheet open. */
internal enum class EssentialSheet {
    CONNECTION_METHOD, CAR_HOTSPOT, SAME_WIFI, PICTURE, INTERFACE_SIZE, DAY_NIGHT, NIGHT_START, NIGHT_END,
    DRIVING_SIDE, SOUND, APPEARANCE, LAYOUT, MOBILECARCONNECT_SIZE, RENDERING, CAR_BRAND, WHEEL_KEYS,
}

internal object SheetKey {
    private const val ADVANCED = "advanced:"
    fun of(sheet: EssentialSheet) = sheet.name
    fun of(setting: AdvancedSetting) = ADVANCED + setting.id
    fun essential(key: String?): EssentialSheet? = key?.let { k -> EssentialSheet.entries.firstOrNull { it.name == k } }
    fun advanced(key: String?): AdvancedSetting? =
        key?.takeIf { it.startsWith(ADVANCED) }?.removePrefix(ADVANCED)?.let { id -> AdvancedSettings.all.firstOrNull { it.id == id } }
}

/** Advanced or Lab rows of a category, engine by engine, in a stable order. */
internal fun engineRows(category: SettingsCategory, tier: Tier, carPlayOnly: Boolean = false): List<Pair<Engine, List<AdvancedSetting>>> =
    AdvancedSettings.forCategory(category, showAdvanced = tier == Tier.ADVANCED, lab = tier == Tier.LAB)
        .filter { it.tier == tier && !(carPlayOnly && it.engine == Engine.ANDROID_AUTO) }
        .groupBy { it.engine }
        .let { groups -> Engine.entries.mapNotNull { engine -> groups[engine]?.let { engine to it } } }

/** The label of an Advanced row's current value, or null when it is not known yet. */
internal fun advancedValueLabel(setting: AdvancedSetting, value: String?): String? {
    val control = setting.control as? Control.Choice ?: return null
    return value?.let { v -> control.options.firstOrNull { it.value == v }?.label ?: v }
}

// ---- Labels -------------------------------------------------------------------------------

@Composable
internal fun connectionMethodLabel(method: ConnectionMethod): String = stringResource(
    when (method) {
        ConnectionMethod.AUTOMATIC -> R.string.mobilecarconnect_settings_method_auto
        ConnectionMethod.SAME_WIFI -> R.string.mobilecarconnect_settings_method_same_wifi
        ConnectionMethod.CAR_HOTSPOT -> R.string.mobilecarconnect_settings_method_hotspot
        ConnectionMethod.WIFI_DIRECT -> R.string.mobilecarconnect_settings_method_direct
    },
)

@Composable
internal fun vehicleLabel(profile: VehicleProfile): String = stringResource(
    when (profile) {
        VehicleProfile.GENERIC -> R.string.mobilecarconnect_settings_car_generic
        VehicleProfile.BYD_DILINK -> R.string.mobilecarconnect_settings_car_byd
    },
)

/** What a car brand reads as: its name, "Other / not listed", or "Other / Generic" when none was chosen or detected. */
@Composable
internal fun carBrandLabel(brand: CarBrand?): String = when (brand) {
    null -> stringResource(R.string.mobilecarconnect_settings_car_not_chosen)
    CarBrand.OTHER -> stringResource(R.string.mobilecarconnect_settings_car_other)
    else -> brand.displayName
}

/** "Detected: BYD (DiLink)" or "Detected: Generic", the hint under the car brand. */
@Composable
internal fun detectedCarLabel(detected: CarBrand?): String =
    stringResource(R.string.mobilecarconnect_settings_car_detected, vehicleLabel(detected?.profile ?: VehicleProfile.GENERIC))

@Composable
internal fun pictureLabel(picture: Picture): String = stringResource(
    when (picture) {
        Picture.AUTOMATIC -> R.string.mobilecarconnect_settings_picture_auto
        Picture.BEST -> R.string.mobilecarconnect_settings_picture_best
        Picture.BALANCED -> R.string.mobilecarconnect_settings_picture_balanced
        Picture.LIGHT -> R.string.mobilecarconnect_settings_picture_light
        Picture.CUSTOM -> R.string.mobilecarconnect_settings_custom
    },
)

@Composable
internal fun interfaceSizeLabel(size: InterfaceSize): String = stringResource(
    when (size) {
        InterfaceSize.SMALL -> R.string.mobilecarconnect_settings_size_small
        InterfaceSize.MEDIUM -> R.string.mobilecarconnect_settings_size_medium
        InterfaceSize.LARGE -> R.string.mobilecarconnect_settings_size_large
        InterfaceSize.CUSTOM -> R.string.mobilecarconnect_settings_custom
    },
)

@Composable
internal fun dayNightLabel(mode: DayNight): String = stringResource(
    when (mode) {
        DayNight.AUTOMATIC -> R.string.mobilecarconnect_settings_daynight_auto
        DayNight.DAY -> R.string.mobilecarconnect_settings_daynight_day
        DayNight.NIGHT -> R.string.mobilecarconnect_settings_daynight_night
        DayNight.SCHEDULE -> R.string.mobilecarconnect_settings_daynight_schedule
    },
)

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.FOLLOW_SYSTEM -> R.string.mobilecarconnect_settings_appearance_follow
        ThemeMode.DARK -> R.string.mobilecarconnect_settings_appearance_dark
        ThemeMode.LIGHT -> R.string.mobilecarconnect_settings_appearance_light
    },
)

@Composable
internal fun renderingLabel(mode: com.mobilecarconnect.app.ui.theme.RenderingMode): String = stringResource(
    when (mode) {
        com.mobilecarconnect.app.ui.theme.RenderingMode.AUTO -> R.string.mobilecarconnect_settings_rendering_auto
        com.mobilecarconnect.app.ui.theme.RenderingMode.FULL -> R.string.mobilecarconnect_settings_rendering_full
        com.mobilecarconnect.app.ui.theme.RenderingMode.LITE -> R.string.mobilecarconnect_settings_rendering_lite
    },
)

/** The row's value: the choice, and for Automatic what it picked on this device. */
@Composable
internal fun renderingValue(display: com.mobilecarconnect.app.ui.DisplayPrefs): String =
    if (display.renderingMode == com.mobilecarconnect.app.ui.theme.RenderingMode.AUTO) {
        stringResource(R.string.mobilecarconnect_settings_rendering_auto_value, stringResource(
            if (display.autoLite) R.string.mobilecarconnect_settings_rendering_lite else R.string.mobilecarconnect_settings_rendering_full))
    } else renderingLabel(display.renderingMode)

@Composable
internal fun layoutLabel(mode: UiProfileMode): String = stringResource(
    when (mode) {
        UiProfileMode.AUTO -> R.string.mobilecarconnect_settings_layout_auto
        UiProfileMode.CAR -> R.string.mobilecarconnect_settings_layout_car
        UiProfileMode.HANDHELD -> R.string.mobilecarconnect_settings_layout_handheld
    },
)

/** MobileCarConnect's own interface scales (UiPreferences.INTERFACE_SCALES) and their names. */
internal val mobileCarConnectScales = listOf(0.9f, 1f, 1.15f, 1.3f)

internal fun nearestScale(scale: Float): Float = mobileCarConnectScales.minBy { abs(it - scale) }

@Composable
internal fun mobileCarConnectScaleLabel(scale: Float): String = stringResource(
    when (mobileCarConnectScales.indexOf(nearestScale(scale))) {
        0 -> R.string.mobilecarconnect_settings_size_small
        2 -> R.string.mobilecarconnect_settings_size_large
        3 -> R.string.mobilecarconnect_settings_size_extra_large
        else -> R.string.mobilecarconnect_settings_size_default
    },
)

internal fun percentText(scale: Float): String = "${Math.round(scale * 100)}%"

/** Minutes after midnight as 24-hour "HH:MM" (tabular digits, no locale surprises in the car). */
internal fun clockText(minute: Int): String {
    val m = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
    return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}
