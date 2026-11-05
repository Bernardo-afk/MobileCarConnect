package com.mobilecarconnect.app.ui.switcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobilecarconnect.app.R
import com.mobilecarconnect.app.ui.components.PhoneAvatar
import com.mobilecarconnect.app.ui.components.engineName
import com.mobilecarconnect.app.ui.components.isolate
import com.mobilecarconnect.app.ui.premium.GlassButton
import com.mobilecarconnect.app.ui.premium.IonButton
import com.mobilecarconnect.app.ui.theme.MobileCarConnectTokens
import com.mobilecarconnect.app.ui.theme.UiProfile
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.core.PhoneKind

/**
 * "Switch to <phone>?": asked when another phone is plugged in while a session runs, over the
 * projection or MobileCarConnect's own screens. Tapping the scrim does nothing (a stray touch must not
 * end the running session or dismiss the question); Back means "Not now".
 */
@Composable
fun SwitchPrompt(
    phoneName: String,
    newEngine: Engine,
    currentEngine: Engine,
    onSwitch: () -> Unit,
    onDecline: () -> Unit,
    /** How the offered phone is reachable: plugged in (USB) or over Bluetooth/Wi-Fi. */
    newTransport: com.mobilecarconnect.core.core.Transport = com.mobilecarconnect.core.core.Transport.USB,
    modifier: Modifier = Modifier,
    safePadding: PaddingValues = PaddingValues(0.dp),
) {
    val dimens = MobileCarConnectTokens.dimens
    val scheme = MaterialTheme.colorScheme
    val colors = MobileCarConnectTokens.colors
    val car = MobileCarConnectTokens.profile == UiProfile.CAR
    val shape = MaterialTheme.shapes.extraLarge
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(safePadding)
            .padding(dimens.gutter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width((if (car) 720.dp else 460.dp).coerceAtMost(maxWidth))
                .heightIn(max = maxHeight)
                .background(scheme.surfaceContainer, shape)
                .border(com.mobilecarconnect.app.ui.premium.glassBorder(), shape)
                .verticalScroll(rememberScrollState())
                .padding(dimens.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhoneAvatar(if (newEngine == Engine.CARPLAY) PhoneKind.IPHONE else PhoneKind.ANDROID, dimens.avatarHero, active = true)
            Spacer(Modifier.height(dimens.gutter))
            Text(stringResource(R.string.mobilecarconnect_home_switch_title, isolate(phoneName)),
                style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive })
            Spacer(Modifier.height(8.dp))
            // Which phone, which app and how it is connected (user request, 2026-10-09).
            Text(stringResource(R.string.mobilecarconnect_switch_how, engineName(newEngine),
                    stringResource(if (newTransport == com.mobilecarconnect.core.core.Transport.USB) R.string.mobilecarconnect_switch_via_usb
                        else R.string.mobilecarconnect_switch_via_wireless)),
                style = MaterialTheme.typography.titleSmall, color = scheme.primary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.mobilecarconnect_home_switch_body, engineName(currentEngine)),
                style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(dimens.gutter * 1.5f))
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                GlassButton(stringResource(R.string.mobilecarconnect_switcher_not_now), onDecline,
                    Modifier.weight(1f).heightIn(min = dimens.primaryButton))
                IonButton(stringResource(R.string.mobilecarconnect_home_switch_action), onSwitch, Modifier.weight(1f))
            }
        }
    }
}
