package com.mobilecarconnect.core.connection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.settings.WifiEnvironmentReader
import com.mobilecarconnect.core.settings.UnifiedSettings
import com.mobilecarconnect.core.settings.UnifiedSettingsStore
import com.mobilecarconnect.core.settings.CarBrand
import com.mobilecarconnect.core.settings.MobileCarConnectPreferences
import com.mobilecarconnect.core.settings.CarBrandStore
import com.mobilecarconnect.core.settings.VehicleProfile
import com.mobilecarconnect.core.settings.WifiEnvironment
import com.mobilecarconnect.core.settings.WirelessPath
import com.mobilecarconnect.core.settings.isValid
import com.mobilecarconnect.core.connection.aa.AaSessionSettings
import java.util.concurrent.Executors

/**
 * MobileCarConnect's single [SessionManager] with the real engines (main process). Everything that starts,
 * stops or watches a session goes through [get].
 */
object Connection {
    private const val TAG = "MobileCarConnect-Connection"

    @Volatile private var instance: SessionManager? = null

    fun get(context: Context): SessionManager = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also {
            instance = it
            watchRadios(context.applicationContext, it)
        }
    }

    /**
     * Location, Bluetooth or Wi-Fi switched on from anywhere (quick settings included): a start
     * blocked on it checks again at once. Only returning through Home's own fix button did, so a
     * driver who turned Location on from the panel stayed blocked (Galaxy Tab, 2026-10-27).
     */
    private fun watchRadios(app: Context, sessions: SessionManager) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                // Wi-Fi's state is a sticky broadcast: registering replays the current one, which is not a change.
                if (isInitialStickyBroadcast) return
                val turnedOn = when (intent.action) {
                    android.location.LocationManager.MODE_CHANGED_ACTION ->
                        app.getSystemService(android.location.LocationManager::class.java)
                            ?.let(androidx.core.location.LocationManagerCompat::isLocationEnabled) == true
                    android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED ->
                        intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1) == android.bluetooth.BluetoothAdapter.STATE_ON
                    android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION ->
                        intent.getIntExtra(android.net.wifi.WifiManager.EXTRA_WIFI_STATE, -1) == android.net.wifi.WifiManager.WIFI_STATE_ENABLED
                    else -> false
                }
                if (turnedOn) sessions.onSettingsChanged()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.location.LocationManager.MODE_CHANGED_ACTION)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        androidx.core.content.ContextCompat.registerReceiver(app, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Settings › Connection › CarPlay only. Turning it on stops any Android Auto session or request
     * and then ends the ":aa" process, so its memory is free at once rather than at the next start.
     * Main thread.
     */
    fun setCarPlayOnly(context: Context, on: Boolean) {
        val app = context.applicationContext
        MobileCarConnectPreferences(app).carPlayOnly = on
        com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "CarPlay only: ${if (on) "on" else "off"}")
        if (on) get(app).releaseEngine(Engine.ANDROID_AUTO) { endAndroidAutoProcess(app) }
        else prewarm(app)
    }

    /** Ends ":aa" (same app, so allowed); it is started again only by an Android Auto session or prewarm. */
    private fun endAndroidAutoProcess(app: Context) {
        val name = app.packageName + com.mobilecarconnect.core.connection.aa.AaProtocol.ENGINE_PROCESS_SUFFIX
        val process = app.getSystemService(android.app.ActivityManager::class.java)?.runningAppProcesses
            ?.firstOrNull { it.processName == name } ?: return
        android.os.Process.killProcess(process.pid)
        com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "Android Auto engine process ended: CarPlay only is on")
    }

    /**
     * Starts Android Auto's ":aa" process ahead of time (reading one setting starts the process
     * but not the engine), so a phone plugged in later does not wait for a cold process start:
     * 4.5 s from plug-in to the engine on the tablet (2026-10-09). Cheap when it already runs.
     * Skipped on a memory-tight unit that knows no Android phone ([EnginePrewarmPolicy]).
     */
    fun prewarm(context: Context) {
        val app = context.applicationContext
        if (MobileCarConnectPreferences(app).carPlayOnly) {
            if (prewarmSkipLogged.compareAndSet(false, true)) {
                com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "Android Auto engine not prewarmed: CarPlay only is on")
            }
            return
        }
        // App start and Home's resume both ask within milliseconds of each other on a cold start.
        if (!prewarming.compareAndSet(false, true)) return
        Thread({
            try {
                val known = runCatching { com.mobilecarconnect.core.detect.KnownPhones(app).all().values }.getOrDefault(emptyList())
                if (!EnginePrewarmPolicy.shouldPrewarm(DeviceCapabilities.signals(app), known)) {
                    logPrewarmSkipped()
                    return@Thread
                }
                val started = android.os.SystemClock.uptimeMillis()
                runCatching { com.mobilecarconnect.core.engine.AndroidAutoSettings.read(app, listOf("wifi-connection-mode")) }
                val took = android.os.SystemClock.uptimeMillis() - started
                if (took > 300) com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "Android Auto engine process warmed in $took ms")
            } finally {
                prewarming.set(false)
            }
        }, "mobilecarconnect-prewarm").start()
    }

    private val prewarming = java.util.concurrent.atomic.AtomicBoolean(false)
    private val prewarmSkipLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun logPrewarmSkipped() {
        if (prewarmSkipLogged.compareAndSet(false, true)) {
            com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect",
                "Android Auto engine not prewarmed: little memory and no Android phone known (it starts when one connects)")
        }
    }

    /** Home is in front: start the last-used phone wirelessly when the rules allow (see [SessionManager.onHomeShown]). */
    @android.annotation.SuppressLint("MissingPermission") // The name is read only when the permission allows.
    fun onHomeShown(context: Context) {
        val app = context.applicationContext
        val address = DeviceEnvironment.lastPhoneAddress(app) ?: return
        val kind = com.mobilecarconnect.core.detect.KnownPhones(app).kindOf(address)
        val name = runCatching {
            app.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.firstOrNull { it.address.equals(address, ignoreCase = true) }?.name
        }.getOrNull()
        get(app).onHomeShown(address, kind, name)
    }

    /**
     * What the engines may do in this car: the chosen [carBrand]'s profile (only BYD is
     * [VehicleProfile.BYD_DILINK]), or what this device looks like when the driver never chose.
     */
    fun vehicleProfile(context: Context): VehicleProfile = CarBrandStore(context).profile()

    /** The driver's car brand, or null when they never chose (then [CarBrand.detect] may still say BYD). */
    fun carBrand(context: Context): CarBrand? = CarBrandStore(context).chosen()

    /**
     * Stores the driver's car brand. Only its [CarBrand.profile] reaches the engines; the brand is
     * never sent to the phone (see [CarBrand]). Android Auto reads the profile at each session
     * start; CarPlay's hooks read it at app start, so they are installed again here when the
     * profile changes ([CarPlayAdapter.install] only sets DiPlay's host fields, so a second call is
     * safe). The driver is told it applies from the next connection: Android Auto's session keeps
     * its profile, and DiPlay reads the BYD switch each time it uses a BYD feature, so a running
     * CarPlay session is consistent only after a reconnect.
     */
    fun setCarBrand(context: Context, brand: CarBrand) {
        val app = context.applicationContext
        if (CarBrandStore(app).set(brand)) CarPlayAdapter.install(app, brand.profile)
    }

    /**
     * Before car brands: [VehicleProfile.BYD_DILINK] stores [CarBrand.BYD]; [VehicleProfile.GENERIC]
     * keeps a chosen non-BYD brand and otherwise stores [CarBrand.OTHER].
     */
    @Deprecated("Choose a car brand; its profile follows from it.", ReplaceWith("Connection.setCarBrand(context, brand)"))
    fun setVehicleProfile(context: Context, profile: VehicleProfile) {
        val current = carBrand(context)
        val brand = if (current != null && current.profile == profile) current else CarBrand.fromProfile(profile)
        setCarBrand(context, brand)
    }

    private fun create(app: Context): SessionManager {
        val unified = UnifiedSettingsStore(app)
        val adapters = mapOf(
            Engine.CARPLAY to CarPlayAdapter(app),
            Engine.ANDROID_AUTO to AndroidAutoAdapter(app) { vehicleProfile(app) },
        )
        val environment = DeviceEnvironment(app) { request, path -> enginePreflight(app, unified.load(), request, path) }
        com.mobilecarconnect.core.detect.PairingLoss.watch(app)
        return SessionManager(
            adapters = adapters,
            environment = environment,
            applySettings = { plan -> applySession(app, plan) },
            worker = Executors.newSingleThreadExecutor { Thread(it, "mobilecarconnect-session") },
            main = Handler(Looper.getMainLooper()),
            profiles = ConnectionProfiles.sharedPreferences(app),
        )
    }

    /** Settings with the car's own hotspot filled in where the firmware lets MobileCarConnect read it. */
    fun effectiveSettings(settings: UnifiedSettings, wifi: WifiEnvironment): UnifiedSettings =
        if (!settings.carHotspot.isValid() && wifi.systemHotspot != null) settings.copy(carHotspot = wifi.systemHotspot) else settings

    /** Writes the session's settings into its engine (worker thread; IPC to ":aa" for Android Auto). */
    private fun applySession(app: Context, plan: SessionPlan) {
        val started = android.os.SystemClock.uptimeMillis()
        try {
            writeSession(app, plan)
        } finally {
            com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect",
                "${plan.engine} settings written in ${android.os.SystemClock.uptimeMillis() - started} ms")
        }
    }

    private fun writeSession(app: Context, plan: SessionPlan) {
        val stored = UnifiedSettingsStore(app).load()
        val wifi = WifiEnvironmentReader.read(app, stored)
        val settings = effectiveSettings(stored, wifi)
        val profile = vehicleProfile(app)
        val picture = automaticPicture(app, settings)
        when (plan.engine) {
            Engine.CARPLAY -> {
                val fineLocation = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                CarPlaySettingsPolicy.apply(app, CarPlaySettingsPolicy.map(settings, plan.path, profile, fineLocation,
                    brandName = MobileCarConnectCore.config.accessoryName, picture = picture))
            }
            Engine.ANDROID_AUTO -> {
                val moduleRoute = DeviceEnvironment.moduleRoute(com.mobilecarconnect.core.detect.ExternalBluetoothProbe.current(app).classification,
                    com.mobilecarconnect.core.settings.MobileCarConnectPreferences(app).wirelessOnExternalBluetooth)
                if (moduleRoute != null && plan.transport == com.mobilecarconnect.core.core.Transport.WIRELESS) {
                    com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "Android Auto Bluetooth through the stereo's module: $moduleRoute (experimental)")
                }
                val values = AaSessionSettings.values(settings, plan.transport, plan.path, profile,
                    carName = MobileCarConnectCore.config.accessoryName, panelDensityDpi = app.resources.displayMetrics.densityDpi, wifi = wifi,
                    picture = picture, moduleRoute = moduleRoute)
                values.problem?.let { Log.w(TAG, "Android Auto settings: $it") }
                AaSessionSettings.apply(app, values)
            }
        }
    }

    /** Automatic picture from this device's decoders ([PicturePolicy]); probed once, on the session worker. */
    private fun automaticPicture(app: Context, settings: UnifiedSettings): PictureChoice {
        if (settings.picture != com.mobilecarconnect.core.settings.Picture.AUTOMATIC) return PicturePolicy.ENGINE_DEFAULTS
        val choice = runCatching { PicturePolicy.choose(DeviceCapabilities.read(app)) }.getOrDefault(PicturePolicy.ENGINE_DEFAULTS)
        com.mobilecarconnect.core.diagnostics.EventLog.add("MobileCarConnect", "Automatic picture: ${choice.summary}")
        return choice
    }

    /** Each engine's own checks, in [Problem] terms. */
    private fun enginePreflight(app: Context, settings: UnifiedSettings, request: SessionRequest, path: WirelessPath?): List<Problem> {
        if (request.engine != Engine.CARPLAY) return emptyList()
        val plan = SessionPlan(request.engine, request.transport, path, request.phoneAddress, request.phoneName, request.usbAttach)
        return CarPlayPreflight.check(app, plan, settings).map { problem ->
            when (problem) {
                CarPlayProblem.AuthenticationMissing -> Problem.CARPLAY_AUTH_MISSING
                CarPlayProblem.VpnConsentNeeded -> Problem.VPN_CONSENT
                is CarPlayProblem.PermissionsMissing -> Problem.PERMISSIONS
                CarPlayProblem.BluetoothOff -> Problem.BLUETOOTH_OFF
                CarPlayProblem.NoIphoneSelected -> Problem.NO_IPHONE_SELECTED
                is CarPlayProblem.IphoneNotBonded -> Problem.PHONE_NOT_PAIRED
                CarPlayProblem.WifiOff -> Problem.WIFI_OFF
                is CarPlayProblem.SameWifiNameUnreadable ->
                    if (problem.needsLocationOn) Problem.LOCATION_OFF
                    else if (problem.needsLocationPermission) Problem.PERMISSIONS else Problem.SAME_WIFI_NOT_JOINED
                CarPlayProblem.SameWifiDetailsMissing -> Problem.SAME_WIFI_DETAILS
                CarPlayProblem.SameWifiNotConnected, CarPlayProblem.SameWifiMultipleNetworks,
                is CarPlayProblem.SameWifiDifferentNetwork -> Problem.SAME_WIFI_NOT_JOINED
                CarPlayProblem.CarHotspotDetailsMissing -> Problem.CAR_HOTSPOT_DETAILS
                CarPlayProblem.CarHotspotOff -> Problem.CAR_HOTSPOT_OFF
            }
        }
    }
}
