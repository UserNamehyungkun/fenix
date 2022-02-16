/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.icons.generator.DefaultIconGenerator
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.feature.search.ext.createSearchEngine
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.customannotations.SmokeTest
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.helpers.FeatureSettingsHelper
import org.mozilla.fenix.helpers.HomeActivityTestRule
import org.mozilla.fenix.helpers.SearchDispatcher
import org.mozilla.fenix.helpers.TestHelper.appContext
import org.mozilla.fenix.helpers.TestHelper.setCustomSearchEngine
import org.mozilla.fenix.ui.robots.homeScreen
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
    @get:Rule
    val activityTestRule = AndroidComposeTestRule(
        HomeActivityTestRule(),
        { it.activity }
    )

    private val featureSettingsHelper = FeatureSettingsHelper()
    lateinit var searchMockServer: MockWebServer
//    private fun searchMockServer() = MockWebServer().apply {
//        dispatcher = SearchDispatcher()
//    }

//    private val customSearchEngine = createSearchEngine(
//        name = "TestSearchEngine",
//        url = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}",
//        icon = DefaultIconGenerator().generate(appContext, IconRequest("http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}")).bitmap
//    )

    @Before
    fun setUp() {
        searchMockServer = MockWebServer().apply {
            dispatcher = SearchDispatcher()
            start()
        }
        featureSettingsHelper.setJumpBackCFREnabled(false)
    }

    @After
    fun tearDown() {
        searchMockServer.shutdown()
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
    fun testSearchGroupInRecentlyVisited() {
        val firstPage = searchMockServer.url("generic1.html").toString()
        val secondPage = searchMockServer.url("generic2.html").toString()
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.openSearch {
        }.submitQuery("test") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInNewTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInNewTab()
        }.goToHomescreen {
            verifyJumpBackInSectionIsDisplayed()
            verifyCurrentSearchGroupIsDisplayed(true,"test",3)
            verifyRecentlyVisitedSearchGroupDisplayed(false, "test", 3)
        }.openTabDrawer {
        }.openTabFromGroup(firstPage) {
        }.openTabDrawer {
        }.openTabFromGroup(secondPage) {
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            verifyRecentlyVisitedSearchGroupDisplayed(true,"test", 3)
        }
    }

    @SmokeTest
    @Test
    fun noCurrentSearchGroupFromPrivateBrowsingTest() {
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.openSearch {
        }.submitQuery("test") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInPrivateTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInPrivateTab()
        }.goToHomescreen {
            verifyCurrentSearchGroupIsDisplayed(false, "test", 3)
        }.openThreeDotMenu {
        }.openHistory {
            verifyHistoryItemExists(false, "3 sites")
        }
    }

    @SmokeTest
    @Test
    fun noRecentlyVisitedSearchGroupInPrivateBrowsingTest() {
        val firstPage = searchMockServer.url("generic1.html").toString()
        val secondPage = searchMockServer.url("generic2.html").toString()
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.togglePrivateBrowsingMode()
        homeScreen {
        }.openSearch {
        }.submitQuery("test") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInPrivateTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInPrivateTab()
        }.openTabDrawer {
        }.openTab(firstPage) {
        }.openTabDrawer {
        }.openTab(secondPage) {
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            homeScreen {
            }.togglePrivateBrowsingMode()
            verifyRecentlyVisitedSearchGroupDisplayed(false, "test", 3)
        }
    }
}
