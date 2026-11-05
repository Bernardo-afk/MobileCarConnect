package com.mobilecarconnect.app.update

import com.mobilecarconnect.core.connection.FailureReason
import com.mobilecarconnect.core.connection.Problem
import com.mobilecarconnect.core.connection.SessionRequest
import com.mobilecarconnect.core.connection.SessionState
import com.mobilecarconnect.core.core.Engine
import com.mobilecarconnect.core.core.Transport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1_760_000_000_000L
    private val request = SessionRequest(Engine.CARPLAY, Transport.WIRELESS, "AA:BB", "iPhone")

    @Test fun firstCheckIsDue() = assertTrue(UpdatePolicy.autoCheckDue(now, 0L, enabled = true, supported = true))

    @Test fun checksAtMostEverySixHours() {
        assertFalse(UpdatePolicy.autoCheckDue(now, now - 1 * hour, enabled = true, supported = true))
        assertFalse(UpdatePolicy.autoCheckDue(now, now - 6 * hour + 1, enabled = true, supported = true))
        assertTrue(UpdatePolicy.autoCheckDue(now, now - 6 * hour, enabled = true, supported = true))
        assertTrue(UpdatePolicy.autoCheckDue(now, now - 30 * hour, enabled = true, supported = true))
    }

    @Test fun clockThatWentBackIsDue() = assertTrue(UpdatePolicy.autoCheckDue(now, now + 48 * hour, enabled = true, supported = true))

    @Test fun neverWhenSwitchedOffOrDebug() {
        assertFalse(UpdatePolicy.autoCheckDue(now, 0L, enabled = false, supported = true))
        assertFalse(UpdatePolicy.autoCheckDue(now, 0L, enabled = true, supported = false))
    }

    @Test fun quietOnlyWithoutASession() {
        assertTrue(UpdatePolicy.sessionQuiet(SessionState.Idle, null))
        assertTrue(UpdatePolicy.sessionQuiet(SessionState.Failed(request, null, FailureReason.values().first(), null), null))
        assertFalse("starting", UpdatePolicy.sessionQuiet(
            SessionState.Active(request, null, 1, SessionState.Phase.Starting), null))
        assertFalse("waiting for the driver to fix something", UpdatePolicy.sessionQuiet(
            SessionState.Blocked(request, listOf(Problem.WIFI_OFF)), null))
        assertFalse("a phone switch is asked", UpdatePolicy.sessionQuiet(SessionState.Idle, request))
    }

    @Test fun offersNewerUnlessIgnored() {
        val release = release("v1.1.0")
        val current = AppVersion(1, 0, 0)
        assertTrue(UpdatePolicy.shouldOffer(release, current, ignoredTag = null, manual = false))
        assertFalse(UpdatePolicy.shouldOffer(release, current, ignoredTag = "v1.1.0", manual = false))
        // The driver asked: show it even though it was ignored.
        assertTrue(UpdatePolicy.shouldOffer(release, current, ignoredTag = "v1.1.0", manual = true))
        // A newer tag than the ignored one is offered again.
        assertTrue(UpdatePolicy.shouldOffer(release("v1.2.0"), current, ignoredTag = "v1.1.0", manual = false))
        assertFalse(UpdatePolicy.shouldOffer(release("v1.0.0"), current, ignoredTag = null, manual = true))
    }

    companion object {
        fun release(tag: String): ReleaseInfo {
            val version = AppVersion.parseTag(tag)!!
            return ReleaseInfo(tag, version, "MobileCarConnect $version", "notes",
                "${ReleaseRepo.DOWNLOAD_PREFIX}$tag/${ReleaseRepo.apkName(version)}", 1000L,
                "${ReleaseRepo.DOWNLOAD_PREFIX}$tag/${ReleaseRepo.checksumName(version)}")
        }
    }
}
