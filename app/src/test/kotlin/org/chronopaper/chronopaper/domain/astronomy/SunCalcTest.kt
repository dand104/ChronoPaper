package org.chronopaper.chronopaper.domain.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SunCalcTest {

    @Test
    fun `angularDistance to identical point is zero`() {
        val distance = SunCalc.angularDistanceDegrees(
            alt1Deg = 45.0,
            az1Deg = 180.0,
            alt2Deg = 45.0,
            az2Deg = 180.0
        )
        assertEquals(0.0, distance, 0.0001)
    }

    @Test
    fun `angularDistance is symmetric`() {
        val d1 = SunCalc.angularDistanceDegrees(10.0, 50.0, 70.0, 200.0)
        val d2 = SunCalc.angularDistanceDegrees(70.0, 200.0, 10.0, 50.0)
        assertEquals(d1, d2, 0.0001)
    }

    @Test
    fun `angularDistance between opposite points on horizon is 180`() {
        val distance = SunCalc.angularDistanceDegrees(
            alt1Deg = 0.0,
            az1Deg = 0.0,
            alt2Deg = 0.0,
            az2Deg = 180.0
        )
        assertEquals(180.0, distance, 0.001)
    }

    @Test
    fun `solar altitude is higher at solar noon than midnight`() {
        val noonMillis = Instant.parse("2026-06-21T12:00:00Z").toEpochMilli()
        val midnightMillis = Instant.parse("2026-06-21T00:00:00Z").toEpochMilli()

        val noonPos = SunCalc.calculatePosition(noonMillis, 51.5074, 0.0)
        val midnightPos = SunCalc.calculatePosition(midnightMillis, 51.5074, 0.0)

        assertTrue(
            "At noon, the sun should be above the horizon (> 0)",
            noonPos.altitudeDegrees > 50.0
        )
        assertTrue(
            "At midnight, the sun should be well below the horizon (< 0)",
            midnightPos.altitudeDegrees < -10.0
        )
        assertTrue(noonPos.altitudeDegrees > midnightPos.altitudeDegrees)
    }

    @Test
    fun `azimuth stays within 0 to 360 degrees range`() {
        val times = listOf(
            Instant.parse("2026-03-20T06:00:00Z").toEpochMilli(),
            Instant.parse("2026-03-20T12:00:00Z").toEpochMilli(),
            Instant.parse("2026-03-20T18:00:00Z").toEpochMilli(),
            Instant.parse("2026-03-20T23:00:00Z").toEpochMilli()
        )

        for (t in times) {
            val pos = SunCalc.calculatePosition(t, 0.0, 0.0)
            assertTrue("Azimuth $pos >= 0", pos.azimuthDegrees >= 0.0)
            assertTrue("Azimuth $pos < 360", pos.azimuthDegrees < 360.0)
            assertTrue("Attitude $pos in range [-90, 90]", pos.altitudeDegrees in -90.0..90.0)
        }
    }
}