package com.crazystudio.sportrecorder.domain.diet

import com.crazystudio.sportrecorder.domain.model.FastingWindow
import com.crazystudio.sportrecorder.ui.diet.select.FastingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultFastingWindowsConsistencyTest {
    @Test fun uiDefaults_matchDomainCatalog_inOrder() {
        val fromUi = FastingItem.defaultFastingItems.map { FastingWindow(it.fastingHours, it.eatingHours) }
        assertEquals(DefaultFastingWindows.all, fromUi)
    }
}
