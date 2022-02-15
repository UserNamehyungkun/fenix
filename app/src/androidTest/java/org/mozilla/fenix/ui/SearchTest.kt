/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.net.toUri
import androidx.test.uiautomator.UiSelector
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.customannotations.SmokeTest
import org.mozilla.fenix.helpers.FeatureSettingsHelper
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.helpers.SearchDispatcher
import org.mozilla.fenix.ui.robots.homeScreen
import org.mozilla.fenix.ui.robots.mDevice
import org.mozilla.fenix.ui.robots.navigationToolbar
import org.mozilla.fenix.ui.robots.searchSettingsScreen

/**
 *  Tests for verifying the search fragment
 *
 *  Including:
 * - Verify the toolbar, awesomebar, and shortcut bar are displayed
 * - Select shortcut button
 * - Select scan button
 *
 */

class SearchTest {
    private val featureSettingsHelper = FeatureSettingsHelper()
    private lateinit var mockWebServer: MockWebServer

    @get:Rule
    val activityTestRule = AndroidComposeTestRule(
        HomeActivityTestRule(),
        { it.activity }
    )

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply {
            dispatcher = SearchDispatcher()
            start()
        }
        featureSettingsHelper.setJumpBackCFREnabled(false)
    }

    @After
    fun tearDown() {
        featureSettingsHelper.resetAllFeatureFlags()
    }

    @Test
    fun searchScreenItemsTest() {
        homeScreen {
        }.openSearch {
            verifySearchView()
            verifyBrowserToolbar()
            verifyScanButton()
            verifySearchEngineButton()
        }
    }

    @SmokeTest
    @Ignore("This test cannot run on virtual devices due to camera permissions being required")
    @Test
    fun scanButtonTest() {
        homeScreen {
        }.openSearch {
            clickScanButton()
            clickDenyPermission()
            clickScanButton()
            clickAllowPermission()
        }
    }

    @Test
    fun shortcutButtonTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSearchSubMenu {
            enableShowSearchShortcuts()
        }.goBack {
        }.goBack {
        }.openSearch {
            verifySearchBarEmpty()
            clickSearchEngineButton(activityTestRule, "DuckDuckGo")
            typeSearch("mozilla")
            verifySearchEngineResults(activityTestRule, "DuckDuckGo", 4)
            clickSearchEngineResult(activityTestRule, "DuckDuckGo")
            verifySearchEngineURL("DuckDuckGo")
        }
    }

    @Test
    fun shortcutSearchEngineSettingsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSearchSubMenu {
            enableShowSearchShortcuts()
        }.goBack {
        }.goBack {
        }.openSearch {
            scrollToSearchEngineSettings(activityTestRule)
            clickSearchEngineSettings(activityTestRule)
            verifySearchSettings()
        }
    }

    @Test
    fun clearSearchTest() {
        homeScreen {
        }.openSearch {
            typeSearch("test")
            clickClearButton()
            verifySearchBarEmpty()
        }
    }

    @SmokeTest
    @Test
    fun testSearchGroupShowsInRecentlyVisited() {
       // val searchPage = mockWebServer.url("pages/searchPage.html").toString().toUri()
        val searchEngine = object {
            var title = "TestSearchEngine"
            var url = "http://localhost:${mockWebServer.port}/searchResults.html?search=%s"
        }

        // Adds our custom search page as default search engine
        searchSettingsScreen{
            addCustomSearchEngine(searchEngine.title, searchEngine.url)
            setCustomEngineAsDefault(searchEngine.title)
        }

        // Performs a search and opens 2 dummy search results links to create a search group
//        navigationToolbar {
//        }.enterURLAndEnterToBrowser(searchPage) {
//            mDevice.findObject(UiSelector().resourceId("searchBox")).text = "testapp"
//            mDevice.findObject(UiSelector().resourceId("submit")).click()
        homeScreen {
        }.openSearch {
        }.submitQuery("testapp") {
            longClickMatchingText("downloads")
            clickContextOpenLinkInNewTab()
            longClickMatchingText("permissions")
            clickContextOpenLinkInNewTab()
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            verifyRecentlyVisitedItemDisplayed("3 sites")
        }
    }
}
