package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RitDatabase
import com.example.data.voice.GeminiVoiceCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches RIT AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RIT AI", appName)
    }

    @Test
    fun `gemini female voice catalog contains all 18 official voices`() {
        assertEquals(18, GeminiVoiceCatalog.officialFemaleVoices.size)
        assertTrue(GeminiVoiceCatalog.officialFemaleVoices.any { it.voiceName == "Aoede" })
        assertTrue(GeminiVoiceCatalog.officialFemaleVoices.any { it.voiceName == "Sulafat" })
    }

    @Test
    fun `room database seeds defaults with audio storage off by default`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = RitDatabase.getInstance(context)
        RitDatabase.seedDefaultsIfNeeded(db)
        val settings = db.appSettingsDao().getSettings()
        assertEquals("Aoede", settings?.selectedVoiceName)
        assertFalse(settings?.storeAudioRecordings ?: true)
    }
}
