package com.mobilecarconnect.app.gallery

import com.mobilecarconnect.app.ui.setup.Need
import com.mobilecarconnect.app.ui.setup.SetupActions
import com.mobilecarconnect.app.ui.setup.SetupContent
import com.mobilecarconnect.app.ui.setup.SetupItem
import com.mobilecarconnect.app.ui.setup.SetupPermission
import com.mobilecarconnect.app.ui.setup.SetupPermissions
import com.mobilecarconnect.app.ui.setup.SetupStep
import com.mobilecarconnect.app.ui.setup.SetupUi
import com.mobilecarconnect.core.connection.PairingSession
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.core.LinkNotice
import com.mobilecarconnect.core.core.LinkNoticeKind
import com.mobilecarconnect.core.core.LinkStage
import com.mobilecarconnect.core.core.LinkState
import com.mobilecarconnect.core.core.PhoneKind
import com.mobilecarconnect.core.core.Transport
import com.mobilecarconnect.core.detect.PairedPhone
import com.mobilecarconnect.core.settings.CarBrand

private object GallerySetupActions : SetupActions

/** The first-time setup, one entry per step (and the pairing step's live states). */
internal fun setupEntries(): List<GalleryEntry> = with(HomeFakes) {
    val carName = "MobileCarConnect · Redmi Pad"
    // Bluetooth and location allowed; the rest still to allow.
    val permissions = SetupPermissions.items(35).map { (item, names) ->
        SetupPermission(item, names, granted = item == SetupItem.BLUETOOTH || item == SetupItem.LOCATION)
    }
    val allGranted = permissions.map { it.copy(granted = it.item != SetupItem.VPN) }
    // Everything a session needs is allowed; the recommended overlay and VPN (and optional battery) are not yet.
    val recommendedMissing = permissions.map { it.copy(granted = it.item.need == Need.REQUIRED) }
    fun ui(step: SetupStep, pairing: PairingSession.State = PairingSession.State.Waiting(carName), visible: Boolean = true,
           link: LinkState = LinkState.Idle, phone: PairedPhone? = null, cable: Boolean = false,
           known: List<PairedPhone> = emptyList(), perms: List<SetupPermission> = permissions,
           brand: CarBrand? = null, detected: CarBrand? = null, wirelessUnavailable: Boolean = false) =
        SetupUi(step, perms, brand, detected, pairing, carName, visible, link, phone, known, cable, wirelessUnavailable)
    fun entry(name: String, content: () -> SetupUi) = GalleryEntry("setup", name) { SetupContent(content(), GallerySetupActions) }
    val s25 = PairedPhone(unknown.address, "Ayush’s S25 Ultra", PhoneKind.UNKNOWN, connected = false)
    listOf(
        entry("welcome") { ui(SetupStep.WELCOME) },
        entry("permissions") { ui(SetupStep.PERMISSIONS) },
        entry("permissions-recommended") { ui(SetupStep.PERMISSIONS, perms = recommendedMissing) },
        // Nothing chosen on a generic screen: "Other" is in effect.
        entry("car") { ui(SetupStep.CAR, perms = allGranted) },
        // A BYD DiLink unit, detected; the BYD tile is in effect and the DiLink extras are explained.
        entry("car-byd-detected") { ui(SetupStep.CAR, perms = allGranted, detected = CarBrand.BYD) },
        entry("pair-waiting") { ui(SetupStep.PAIR, visible = false, perms = allGranted, known = listOf(pixel)) },
        // Paired; the iPhone answered and is linking, slower than usual.
        // A stereo whose Bluetooth is an external module: wireless cannot start yet, the cable can.
        entry("pair-external-bluetooth") { ui(SetupStep.PAIR, visible = false, perms = allGranted, wirelessUnavailable = true) },
        entry("pair-found-iphone") {
            ui(SetupStep.PAIR, PairingSession.State.Paired(iphone.address, iphone.name, PhoneKind.IPHONE), perms = allGranted,
                phone = iphone, link = LinkState(LinkStage.CONNECTING, Engine.CARPLAY, Transport.WIRELESS, iphone.name, 3, 4,
                    notice = LinkNotice(LinkNoticeKind.SLOW)))
        },
        entry("pair-kind-unknown") {
            ui(SetupStep.PAIR, PairingSession.State.Paired(s25.address, s25.name, PhoneKind.UNKNOWN), perms = allGranted, phone = s25)
        },
        entry("pair-android-connected") {
            ui(SetupStep.PAIR, PairingSession.State.Paired(pixel.address, pixel.name, PhoneKind.ANDROID), perms = allGranted, phone = pixel,
                link = LinkState(LinkStage.CONNECTED, Engine.ANDROID_AUTO, Transport.WIRELESS, pixel.name, 4, 4))
        },
        // "Plug in with a cable" chosen, nothing plugged in yet: the one-time prompts are said first.
        entry("pair-cable-waiting") { ui(SetupStep.PAIR, perms = allGranted, cable = true) },
        entry("pair-cable") {
            ui(SetupStep.PAIR, perms = allGranted, cable = true,
                link = LinkState(LinkStage.CONNECTING, Engine.CARPLAY, Transport.USB, "iPhone", 2, 4))
        },
        // Tablet 2026-10-20: the first connection was blocked because Location was off; Home's words and its fix.
        entry("pair-location-off") {
            ui(SetupStep.PAIR, PairingSession.State.Paired(iphone.address, iphone.name, PhoneKind.IPHONE), perms = allGranted,
                phone = iphone, link = failed(com.mobilecarconnect.core.core.LinkProblem.LOCATION_OFF))
        },
        entry("pair-bluetooth-off") { ui(SetupStep.PAIR, PairingSession.State.BluetoothOff, perms = allGranted) },
        entry("done") { ui(SetupStep.DONE, perms = allGranted, phone = iphone, brand = CarBrand.MARUTI_SUZUKI) },
    )
}
