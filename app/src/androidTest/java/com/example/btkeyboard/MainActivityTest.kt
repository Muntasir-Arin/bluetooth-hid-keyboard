package com.example.btkeyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun devicesTabVisibleOnLaunch() {
        rule.onNodeWithText("Devices").assertIsDisplayed()
    }

    @Test
    fun touchpadTabCanBeOpenedFromBottomBar() {
        rule.onNodeWithContentDescription("Touchpad").performClick()
        rule.onNodeWithText("Touchpad").assertIsDisplayed()
    }
}
