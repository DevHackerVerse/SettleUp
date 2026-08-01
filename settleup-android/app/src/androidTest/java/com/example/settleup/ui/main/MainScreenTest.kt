package com.example.settleup.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.settleup.android.ui.main.MainScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.example.settleup.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(FAKE_DATA) }
  }

  @Test
  fun firstItem_exists() {
    FAKE_DATA.forEach { composeTestRule.onNodeWithText("Hello $it!").assertExists() }
  }
}

private val FAKE_DATA = listOf("Sample1", "Sample2", "Sample3")
