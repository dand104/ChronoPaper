package org.chronopaper.chronopaper.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoCoordinateTest {

    @Test
    fun `valid coordinates are created successfully`() {
        val coord = GeoCoordinate(latitude = 55.7558, longitude = 37.6173)
        assertEquals(55.7558, coord.latitude, 0.0001)
        assertEquals(37.6173, coord.longitude, 0.0001)
    }

    @Test
    fun `boundary coordinates are accepted`() {
        val minBounds = GeoCoordinate(latitude = -90.0, longitude = -180.0)
        val maxBounds = GeoCoordinate(latitude = 90.0, longitude = 180.0)

        assertEquals(-90.0, minBounds.latitude, 0.0)
        assertEquals(90.0, maxBounds.latitude, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude below -90 throws exception`() {
        GeoCoordinate(latitude = -90.001, longitude = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `latitude above 90 throws exception`() {
        GeoCoordinate(latitude = 90.1, longitude = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude below -180 throws exception`() {
        GeoCoordinate(latitude = 0.0, longitude = -180.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `longitude above 180 throws exception`() {
        GeoCoordinate(latitude = 0.0, longitude = 180.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `NaN or Infinite latitude throws exception`() {
        GeoCoordinate(latitude = Double.NaN, longitude = 0.0)
    }
}