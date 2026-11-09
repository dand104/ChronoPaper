package org.chronopaper.chronopaper.domain.astronomy

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class SolarPosition(
    val azimuthDegrees: Double,
    val altitudeDegrees: Double
)

object SunCalc {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val J1970 = 2_440_588.0
    private const val J2000 = 2_451_545.0
    private const val DEG_TO_RAD = PI / 180.0
    private const val RAD_TO_DEG = 180.0 / PI
    private const val OBLIQUITY = 23.4397 * DEG_TO_RAD

    fun calculatePosition(
        epochMillis: Long,
        latitudeDegrees: Double,
        longitudeDegrees: Double
    ): SolarPosition {
        val days = toDays(epochMillis)
        val lw = -longitudeDegrees * DEG_TO_RAD
        val phi = latitudeDegrees * DEG_TO_RAD

        val meanAnomaly = solarMeanAnomaly(days)
        val eclipticLongitude = eclipticLongitude(meanAnomaly)

        val declination = declination(eclipticLongitude)
        val rightAscension = rightAscension(eclipticLongitude)

        val siderealTime = siderealTime(days, lw)
        val hourAngle = siderealTime - rightAscension

        val sinAltitude = (sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle))
            .coerceIn(-1.0, 1.0)
        val altitudeRad = asin(sinAltitude)

        val azimuthFromSouthRad = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(phi) - tan(declination) * cos(phi)
        )
        val altitudeDeg = altitudeRad * RAD_TO_DEG
        val compassAzimuthDeg = ((azimuthFromSouthRad * RAD_TO_DEG) + 180.0).mod(360.0)

        return SolarPosition(
            azimuthDegrees = compassAzimuthDeg,
            altitudeDegrees = altitudeDeg
        )
    }

    fun angularDistanceDegrees(
        alt1Deg: Double,
        az1Deg: Double,
        alt2Deg: Double,
        az2Deg: Double
    ): Double {
        val alt1 = alt1Deg * DEG_TO_RAD
        val alt2 = alt2Deg * DEG_TO_RAD
        val deltaAz = (az1Deg - az2Deg) * DEG_TO_RAD

        val cosAngle = (sin(alt1) * sin(alt2) + cos(alt1) * cos(alt2) * cos(deltaAz))
            .coerceIn(-1.0, 1.0)
        return acos(cosAngle) * RAD_TO_DEG
    }

    private fun toDays(epochMillis: Long): Double =
        (epochMillis.toDouble() / MILLIS_PER_DAY) - 0.5 + J1970 - J2000

    private fun solarMeanAnomaly(days: Double): Double =
        (357.5291 + 0.98560028 * days) * DEG_TO_RAD

    private fun eclipticLongitude(meanAnomaly: Double): Double {
        val center = (1.9148 * sin(meanAnomaly) +
                0.02 * sin(2.0 * meanAnomaly) +
                0.0003 * sin(3.0 * meanAnomaly)) * DEG_TO_RAD
        val perihelion = 102.9372 * DEG_TO_RAD
        return meanAnomaly + center + perihelion + PI
    }

    private fun declination(eclipticLongitude: Double): Double {
        val sinDec = (sin(OBLIQUITY) * sin(eclipticLongitude)).coerceIn(-1.0, 1.0)
        return asin(sinDec)
    }

    private fun rightAscension(eclipticLongitude: Double): Double =
        atan2(sin(eclipticLongitude) * cos(OBLIQUITY), cos(eclipticLongitude))

    private fun siderealTime(days: Double, lw: Double): Double =
        (280.16 + 360.9856235 * days) * DEG_TO_RAD - lw
}