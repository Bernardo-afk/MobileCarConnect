package com.mobilecarconnect.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mobilecarconnect.app.R
import com.mobilecarconnect.app.ui.theme.MobileCarConnectTokens
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.core.PhoneKind
import com.mobilecarconnect.core.core.Transport

/** Small shared building blocks. Every size comes from MobileCarConnectTokens.dimens. */

/**
 * The MobileCarConnect brand mark, cropped tight to the glyph. Two tinted layers so it follows the active
 * day/night palette: the body in [MaterialTheme.colorScheme.onSurface], the node in the Ion accent.
 * The geometry comes from design/logo/tools/build_logo.py (swap the two drawables to change logo).
 */
@Composable
fun MobileCarConnectMark(
    modifier: Modifier = Modifier,
    primaryTint: Color = MaterialTheme.colorScheme.onSurface,
    accentTint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier) {
        Icon(painterResource(R.drawable.mobilecarconnect_logo_mark_primary), null, Modifier.matchParentSize(), tint = primaryTint)
        Icon(painterResource(R.drawable.mobilecarconnect_logo_mark_accent), null, Modifier.matchParentSize(), tint = accentTint)
    }
}

@Composable
fun CarIcon(@DrawableRes icon: Int, contentDescription: String?, modifier: Modifier = Modifier,
            size: Dp = MobileCarConnectTokens.dimens.iconSecondary, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

/** Low-emphasis action (text button), still at the minimum touch size. */
@Composable
fun TertiaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val dimens = MobileCarConnectTokens.dimens
    TextButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = dimens.minTouch),
        shape = RoundedCornerShape(dimens.buttonRadius)) {
        if (icon != null) {
            CarIcon(icon, null, size = dimens.iconTertiary + 4.dp, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2)
    }
}

/**
 * One glyph per phone type that still reads at 24dp: Material's iPhone and Android handsets differ
 * only by a dot, so Android phones get the Android glyph and unknown devices a question mark.
 */
@DrawableRes
fun phoneIcon(kind: PhoneKind): Int = when (kind) {
    PhoneKind.IPHONE -> R.drawable.mobilecarconnect_ic_phone_iphone
    PhoneKind.ANDROID -> R.drawable.mobilecarconnect_ic_android
    PhoneKind.UNKNOWN -> R.drawable.mobilecarconnect_ic_device_unknown
}

@Composable
fun engineName(engine: Engine): String = stringResource(
    if (engine == Engine.CARPLAY) R.string.mobilecarconnect_engine_carplay else R.string.mobilecarconnect_engine_android_auto)

@Composable
fun transportName(transport: Transport): String = stringResource(
    if (transport == Transport.USB) R.string.mobilecarconnect_transport_usb else R.string.mobilecarconnect_transport_wireless)

/** Phone glyph in a circle (76dp hero / 56dp rows on car). */
@Composable
fun PhoneAvatar(kind: PhoneKind, size: Dp, modifier: Modifier = Modifier, active: Boolean = false) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        CarIcon(phoneIcon(kind), null, size = size * 0.5f,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}
