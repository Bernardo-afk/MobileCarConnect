package com.mobilecarconnect.app.gallery

import com.mobilecarconnect.app.ui.ConnectionPrefs
import com.mobilecarconnect.app.ui.HomeState
import com.mobilecarconnect.app.ui.NetworkPreview
import com.mobilecarconnect.app.ui.home.HomeActions
import com.mobilecarconnect.app.ui.home.HomeUi
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.core.LinkProblem
import com.mobilecarconnect.core.core.LinkStage
import com.mobilecarconnect.core.core.LinkState
import com.mobilecarconnect.core.core.PhoneKind
import com.mobilecarconnect.core.core.Transport
import com.mobilecarconnect.core.detect.BluetoothAvailability
import com.mobilecarconnect.core.detect.PairedPhone
import com.mobilecarconnect.core.settings.WirelessPath

/** Fake state for Home's gallery entries. */
internal object HomeFakes {
    val iphone = PairedPhone("AA:BB:CC:00:00:01", "Sara’s iPhone", PhoneKind.IPHONE, connected = false)
    val iphoneConnected = iphone.copy(connected = true)
    val pixel = PairedPhone("AA:BB:CC:00:00:02", "Pixel 8", PhoneKind.ANDROID, connected = false)
    val unknown = PairedPhone("AA:BB:CC:00:00:03", "Galaxy Buds", PhoneKind.UNKNOWN, connected = false)

    val prefs = ConnectionPrefs(autoConnectUsb = true, autoConnectBluetooth = true, startOnBoot = true, autoConnectPaused = false)

    val hotspot = NetworkPreview(WirelessPath.CAR_HOTSPOT, ssid = null)
    /** Same Wi-Fi chosen, the screen on "Home Wi-Fi", no password saved yet. */
    val sameWifiNoPassword = NetworkPreview(WirelessPath.SAME_WIFI, ssid = "Home Wi-Fi", joinedSsid = "Home Wi-Fi", savedSameWifi = "Home Wi-Fi")
    val sameWifi = NetworkPreview(WirelessPath.SAME_WIFI, ssid = "Home Wi-Fi", joinedSsid = "Home Wi-Fi")

    val actions: HomeActions = object : HomeActions {}

    fun ui(
        link: LinkState = LinkState.Idle,
        phones: List<PairedPhone> = listOf(iphone),
        defaultPhone: PairedPhone? = phones.firstOrNull(),
        bluetooth: BluetoothAvailability = BluetoothAvailability.READY,
        network: NetworkPreview? = hotspot,
        connectedSince: Long? = null,
        permissionsNeeded: Boolean = false,
        stereo: com.mobilecarconnect.core.detect.StereoBluetooth? = null,
    ) = HomeUi(HomeState(link, phones, bluetooth, defaultPhone, connectedSince), prefs, network, permissionsNeeded, stereo = stereo)

    fun failed(problem: LinkProblem, engine: Engine = Engine.CARPLAY, transport: Transport = Transport.WIRELESS) =
        LinkState(LinkStage.FAILED, engine, transport, if (engine == Engine.CARPLAY) iphone.name else pixel.name,
            problem = problem, detail = "Failed")
}
