package com.criteo.publisher.privacy.gdpr

import com.criteo.publisher.util.SafeSharedPreferences
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Tcf2GdprStrategyTest {
    @Test
    fun testGetConsentString() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn "fake_consent_string"
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertEquals("fake_consent_string", tcf2Strategy.consentString)
    }

    @Test
    fun testGetGdprApplies_WhenKeyIsProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getInt("IABTCF_gdprApplies", -1) } doReturn 0
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertEquals("0", tcf2Strategy.subjectToGdpr)
    }

    @Test
    fun testGetGdprApplies_WhenKeyIsNotProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getInt("IABTCF_gdprApplies", -1) } doReturn -1
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertEquals("", tcf2Strategy.subjectToGdpr)
    }

    @Test
    fun testIsProvided_True_AllKeysProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn "fake_consent_string"
            on { getInt("IABTCF_gdprApplies", -1) } doReturn 0
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertTrue { tcf2Strategy.isProvided }
    }

    @Test
    fun testIsProvided_True_ConsentStringNotProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn ""
            on { getInt("IABTCF_gdprApplies", -1) } doReturn 0
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertTrue { tcf2Strategy.isProvided }
    }

    @Test
    fun testIsProvided_True_SubjectToGdprNotProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn "fake_consent_string"
            on { getInt("IABTCF_gdprApplies", -1) } doReturn -1
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertTrue { tcf2Strategy.isProvided }
    }

    @Test
    fun testIsProvided_False_ParsedVendorNotProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn "fake_consent_string"
            on { getInt("IABTCF_gdprApplies", -1) } doReturn -1
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertTrue { tcf2Strategy.isProvided }
    }

    @Test
    fun testIsProvided_False_NoKeysProvided() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences> {
            on { getString("IABTCF_TCString", "") } doReturn ""
            on { getInt("IABTCF_gdprApplies", -1) } doReturn -1
        }

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertFalse { tcf2Strategy.isProvided }
    }

    @Test
    fun testVersion() {
        // Given
        val safeSharedPreferences = mock<SafeSharedPreferences>()

        // When
        val tcf2Strategy = Tcf2GdprStrategy(safeSharedPreferences)

        // Then
        assertEquals(2, tcf2Strategy.version)
    }
}
