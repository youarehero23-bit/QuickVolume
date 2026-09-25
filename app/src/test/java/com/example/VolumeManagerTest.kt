package com.example

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumeManagerTest {

    private lateinit var volumeManager: VolumeManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        volumeManager = VolumeManager(context)
    }

    @Test
    fun testCallVolumeMethods() {
        val maxCallVol = volumeManager.getMaxCallVolume()
        val minCallVol = volumeManager.getMinCallVolume()
        assertTrue("Max call volume should be >= min call volume", maxCallVol >= minCallVol)

        val targetVol = (maxCallVol + minCallVol) / 2
        volumeManager.setCallVolume(targetVol, showUi = false)
        val currentCallVol = volumeManager.getCallVolume()
        assertEquals(targetVol, currentCallVol)

        val percentage = volumeManager.getCallVolumePercentage()
        assertTrue("Call percentage should be between 0 and 100", percentage in 0..100)
    }

    @Test
    fun testMediaVolumeMethods() {
        val maxVol = volumeManager.getMaxVolume()
        val minVol = volumeManager.getMinVolume()
        assertTrue("Max media volume should be >= min media volume", maxVol >= minVol)

        val targetVol = (maxVol + minVol) / 2
        volumeManager.setVolume(targetVol, showUi = false)
        val currentVol = volumeManager.getVolume()
        assertEquals(targetVol, currentVol)

        val percentage = volumeManager.getVolumePercentage()
        assertTrue("Media percentage should be between 0 and 100", percentage in 0..100)
    }
}
