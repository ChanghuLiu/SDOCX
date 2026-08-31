package com.notesescape.sdocx

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.res.Configuration
import android.view.View
import java.util.Locale

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.notesescape.sdocx", appContext.packageName)
    }

    @Test
    fun localizedResourcesResolveAndArabicUsesRtl() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Notes Escape: SDOCX", base.getString(com.notesescape.sdocx.R.string.app_name))

        val frenchConfig = Configuration(base.resources.configuration)
        frenchConfig.setLocale(Locale.FRENCH)
        val french = base.createConfigurationContext(frenchConfig)
        assertEquals("Export de notes : SDOCX", french.getString(com.notesescape.sdocx.R.string.app_name))

        val arabicConfig = Configuration(base.resources.configuration)
        arabicConfig.setLocale(Locale("ar"))
        val arabic = base.createConfigurationContext(arabicConfig)
        assertEquals("تصدير الملاحظات: SDOCX", arabic.getString(com.notesescape.sdocx.R.string.app_name))
        assertEquals(View.LAYOUT_DIRECTION_RTL, arabic.resources.configuration.layoutDirection)
    }
}
