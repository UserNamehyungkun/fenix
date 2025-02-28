/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home

import androidx.navigation.NavController
import androidx.navigation.NavDirections
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.search.RegionState
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ReaderState
import mozilla.components.browser.state.state.SearchState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.rule.MainCoroutineRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.BrowserDirection
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.browser.BrowserFragmentDirections
import org.mozilla.fenix.browser.browsingmode.BrowsingMode
import org.mozilla.fenix.components.Analytics
import org.mozilla.fenix.components.TabCollectionStorage
import org.mozilla.fenix.components.metrics.Event
import org.mozilla.fenix.components.metrics.Event.PerformedSearch.EngineSource
import org.mozilla.fenix.components.metrics.MetricController
import org.mozilla.fenix.components.tips.Tip
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings
import org.mozilla.fenix.home.recentbookmarks.RecentBookmark
import org.mozilla.fenix.home.recenttabs.RecentTab
import org.mozilla.fenix.home.sessioncontrol.DefaultSessionControlController
import org.mozilla.fenix.settings.SupportUtils
import org.mozilla.fenix.utils.Settings
import mozilla.components.feature.tab.collections.Tab as ComponentTab

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSessionControlControllerTest {

    @get:Rule
    val coroutinesTestRule = MainCoroutineRule()

    private val activity: HomeActivity = mockk(relaxed = true)
    private val fragmentStore: HomeFragmentStore = mockk(relaxed = true)
    private val navController: NavController = mockk(relaxed = true)
    private val metrics: MetricController = mockk(relaxed = true)
    private val engine: Engine = mockk(relaxed = true)
    private val tabCollectionStorage: TabCollectionStorage = mockk(relaxed = true)
    private val tabsUseCases: TabsUseCases = mockk(relaxed = true)
    private val reloadUrlUseCase: SessionUseCases = mockk(relaxed = true)
    private val selectTabUseCase: TabsUseCases = mockk(relaxed = true)
    private val settings: Settings = mockk(relaxed = true)
    private val analytics: Analytics = mockk(relaxed = true)
    private val scope = TestCoroutineScope()
    private val searchEngine = SearchEngine(
        id = "test",
        name = "Test Engine",
        icon = mockk(relaxed = true),
        type = SearchEngine.Type.BUNDLED,
        resultUrls = listOf("https://example.org/?q={searchTerms}")
    )

    private val googleSearchEngine = SearchEngine(
        id = "googleTest",
        name = "Google Test Engine",
        icon = mockk(relaxed = true),
        type = SearchEngine.Type.BUNDLED,
        resultUrls = listOf("https://www.google.com/?q={searchTerms}"),
        suggestUrl = "https://www.google.com/"
    )

    private val duckDuckGoSearchEngine = SearchEngine(
        id = "ddgTest",
        name = "DuckDuckGo Test Engine",
        icon = mockk(relaxed = true),
        type = SearchEngine.Type.BUNDLED,
        resultUrls = listOf("https://duckduckgo.com/?q=%7BsearchTerms%7D&t=fpas"),
        suggestUrl = "https://ac.duckduckgo.com/ac/?q=%7BsearchTerms%7D&type=list"
    )

    private lateinit var store: BrowserStore
    private val homeFragmentState: HomeFragmentState = mockk(relaxed = true)

    @Before
    fun setup() {
        store = BrowserStore(
            BrowserState(
                search = SearchState(
                    regionSearchEngines = listOf(searchEngine)
                )
            )
        )

        every { fragmentStore.state } returns HomeFragmentState(
            collections = emptyList(),
            expandedCollections = emptySet(),
            mode = Mode.Normal,
            topSites = emptyList(),
            showCollectionPlaceholder = true,
            showSetAsDefaultBrowserCard = true,
            recentTabs = emptyList(),
            recentBookmarks = emptyList()
        )

        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.homeFragment
        }
        every { activity.components.settings } returns settings
        every { activity.settings() } returns settings
        every { activity.components.analytics } returns analytics
        every { analytics.metrics } returns metrics
    }

    @After
    fun cleanUp() {
        scope.cleanupTestCoroutines()
    }

    @Test
    fun handleCollectionAddTabTapped() {
        val collection = mockk<TabCollection> {
            every { id } returns 12L
        }
        createController().handleCollectionAddTabTapped(collection)

        verify { metrics.track(Event.CollectionAddTabPressed) }
        verify {
            navController.navigate(
                match<NavDirections> {
                    it.actionId == R.id.action_global_collectionCreationFragment
                },
                null
            )
        }
    }

    @Test
    fun handleCustomizeHomeTapped() {
        createController().handleCustomizeHomeTapped()
        verify { metrics.track(Event.HomeScreenCustomizedHomeClicked) }

        verify {
            navController.navigate(
                match<NavDirections> {
                    it.actionId == R.id.action_global_homeSettingsFragment
                },
                null
            )
        }
    }

    @Test
    @Ignore("Until the feature is enabled again")
    fun handleShowOnboardingDialog() {
        createController().handleShowOnboardingDialog()

        verify {
            navController.navigate(
                match<NavDirections> {
                    it.actionId == R.id.action_global_home_onboarding_dialog
                },
                null
            )
        }
    }

    @Test
    fun `handleCollectionOpenTabClicked onFailure`() {
        val tab = mockk<ComponentTab> {
            every { url } returns "https://mozilla.org"
            every { restore(activity, engine, restoreSessionId = false) } returns null
        }
        createController().handleCollectionOpenTabClicked(tab)

        verify { metrics.track(Event.CollectionTabRestored) }
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = "https://mozilla.org",
                newTab = true,
                from = BrowserDirection.FromHome
            )
        }
    }

    @Test
    fun `handleCollectionOpenTabClicked with existing selected tab`() {
        val recoverableTab = RecoverableTab(
            engineSessionState = null,
            state = TabState(
                id = "test",
                parentId = null,
                url = "https://www.mozilla.org",
                title = "Mozilla",
                contextId = null,
                readerState = ReaderState(),
                lastAccess = 0,
                private = false
            )
        )

        val tab = mockk<ComponentTab> {
            every { restore(activity, engine, restoreSessionId = false) } returns recoverableTab
        }

        val restoredTab = createTab(id = recoverableTab.state.id, url = recoverableTab.state.url)
        val otherTab = createTab(id = "otherTab", url = "https://mozilla.org")
        store.dispatch(TabListAction.AddTabAction(otherTab)).joinBlocking()
        store.dispatch(TabListAction.SelectTabAction(otherTab.id)).joinBlocking()
        store.dispatch(TabListAction.AddTabAction(restoredTab)).joinBlocking()

        createController().handleCollectionOpenTabClicked(tab)
        verify { metrics.track(Event.CollectionTabRestored) }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
        verify { selectTabUseCase.selectTab.invoke(restoredTab.id) }
        verify { reloadUrlUseCase.reload.invoke(restoredTab.id) }
    }

    @Test
    fun `handleCollectionOpenTabClicked without existing selected tab`() {
        val recoverableTab = RecoverableTab(
            engineSessionState = null,
            state = TabState(
                id = "test",
                parentId = null,
                url = "https://www.mozilla.org",
                title = "Mozilla",
                contextId = null,
                readerState = ReaderState(),
                lastAccess = 0,
                private = false
            )
        )

        val tab = mockk<ComponentTab> {
            every { restore(activity, engine, restoreSessionId = false) } returns recoverableTab
        }

        val restoredTab = createTab(id = recoverableTab.state.id, url = recoverableTab.state.url)
        store.dispatch(TabListAction.AddTabAction(restoredTab)).joinBlocking()

        createController().handleCollectionOpenTabClicked(tab)
        verify { metrics.track(Event.CollectionTabRestored) }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
        verify { selectTabUseCase.selectTab.invoke(restoredTab.id) }
        verify { reloadUrlUseCase.reload.invoke(restoredTab.id) }
    }

    @Test
    fun handleCollectionOpenTabsTapped() {
        val collection = mockk<TabCollection> {
            every { tabs } returns emptyList()
        }
        createController().handleCollectionOpenTabsTapped(collection)

        verify { metrics.track(Event.CollectionAllTabsRestored) }
    }

    @Test
    fun `handleCollectionRemoveTab one tab`() {
        val expectedCollection = mockk<TabCollection> {
            every { tabs } returns listOf(mockk())
            every { title } returns "Collection"
        }
        val tab = mockk<ComponentTab>()
        every {
            activity.resources.getString(
                R.string.delete_tab_and_collection_dialog_title,
                "Collection"
            )
        } returns "Delete Collection?"
        every {
            activity.resources.getString(R.string.delete_tab_and_collection_dialog_message)
        } returns "Deleting this tab will delete everything."

        var actualCollection: TabCollection? = null

        createController(
            removeCollectionWithUndo = { collection ->
                actualCollection = collection
            }
        ).handleCollectionRemoveTab(expectedCollection, tab, false)

        verify { metrics.track(Event.CollectionTabRemoved) }

        assertEquals(expectedCollection, actualCollection)
    }

    @Test
    fun `handleCollectionRemoveTab multiple tabs`() {
        val collection: TabCollection = mockk(relaxed = true)
        val tab: ComponentTab = mockk(relaxed = true)
        createController().handleCollectionRemoveTab(collection, tab, false)
        verify { metrics.track(Event.CollectionTabRemoved) }
    }

    @Test
    fun handleCollectionShareTabsClicked() {
        val collection = mockk<TabCollection> {
            every { tabs } returns emptyList()
            every { title } returns ""
        }
        createController().handleCollectionShareTabsClicked(collection)

        verify { metrics.track(Event.CollectionShared) }
        verify {
            navController.navigate(
                match<NavDirections> { it.actionId == R.id.action_global_shareFragment },
                null
            )
        }
    }

    @Test
    fun handleDeleteCollectionTapped() {
        val expectedCollection = mockk<TabCollection> {
            every { title } returns "Collection"
        }
        every {
            activity.resources.getString(R.string.tab_collection_dialog_message, "Collection")
        } returns "Are you sure you want to delete Collection?"

        var actualCollection: TabCollection? = null

        createController(
            removeCollectionWithUndo = { collection ->
                actualCollection = collection
            }
        ).handleDeleteCollectionTapped(expectedCollection)

        assertEquals(expectedCollection, actualCollection)
    }

    @Test
    fun handlePrivateBrowsingLearnMoreClicked() {
        createController().handlePrivateBrowsingLearnMoreClicked()
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = SupportUtils.getGenericSumoURLForTopic
                (SupportUtils.SumoTopic.PRIVATE_BROWSING_MYTHS),
                newTab = true,
                from = BrowserDirection.FromHome
            )
        }
    }

    @Test
    fun handleRenameCollectionTapped() {
        val collection = mockk<TabCollection> {
            every { id } returns 3L
        }
        createController().handleRenameCollectionTapped(collection)

        verify { metrics.track(Event.CollectionRenamePressed) }
        verify {
            navController.navigate(
                match<NavDirections> { it.actionId == R.id.action_global_collectionCreationFragment },
                null
            )
        }
    }

    @Test
    fun handleSelectDefaultTopSite() {
        val topSite = TopSite.Default(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenDefault) }
        verify {
            tabsUseCases.addTab.invoke(
                url = topSite.url,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectNonDefaultTopSite() {
        val topSite = TopSite.Frecent(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify {
            tabsUseCases.addTab.invoke(
                url = topSite.url,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGoogleDefaultTopSiteUS() {
        val topSite = TopSite.Default(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("US", "US"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenDefault) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                url = SupportUtils.GOOGLE_US_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGoogleDefaultTopSiteXX() {
        val topSite = TopSite.Default(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("DE", "FR"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenDefault) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                SupportUtils.GOOGLE_XX_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGoogleDefaultTopSite_EventPerformedSearchTopSite() {
        val topSite = TopSite.Default(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val engineSource = EngineSource.Default(googleSearchEngine, false)
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(googleSearchEngine)

        try {
            mockkStatic("mozilla.components.browser.state.state.SearchStateKt")

            every { any<SearchState>().selectedOrDefaultSearchEngine } returns googleSearchEngine

            controller.handleSelectTopSite(topSite)

            verify {
                metrics.track(
                    Event.PerformedSearch(
                        Event.PerformedSearch.EventSource.TopSite(
                            engineSource
                        )
                    )
                )
                metrics.track(Event.TopSiteOpenGoogle)
                metrics.track(Event.TopSiteOpenDefault)
            }
        } finally {
            unmockkStatic("mozilla.components.browser.state.state.SearchStateKt")
        }
    }

    @Test
    fun handleSelectDuckDuckGoTopSite_EventPerformedSearchTopSite() {
        val topSite = TopSite.Pinned(
            id = 1L,
            title = "DuckDuckGo",
            url = "https://duckduckgo.com",
            createdAt = 0
        )
        val engineSource = EngineSource.Shortcut(duckDuckGoSearchEngine, false)
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(googleSearchEngine, duckDuckGoSearchEngine)

        try {
            mockkStatic("mozilla.components.browser.state.state.SearchStateKt")

            every { any<SearchState>().selectedOrDefaultSearchEngine } returns googleSearchEngine

            controller.handleSelectTopSite(topSite)

            verify {
                metrics.track(
                    Event.PerformedSearch(
                        Event.PerformedSearch.EventSource.TopSite(
                            engineSource
                        )
                    )
                )

                metrics.track(Event.TopSiteOpenPinned)
            }
        } finally {
            unmockkStatic("mozilla.components.browser.state.state.SearchStateKt")
        }
    }

    @Test
    fun handleSelectGooglePinnedTopSiteUS() {
        val topSite = TopSite.Pinned(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("US", "US"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenPinned) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                SupportUtils.GOOGLE_US_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGooglePinnedTopSiteXX() {
        val topSite = TopSite.Pinned(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("DE", "FR"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenPinned) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                SupportUtils.GOOGLE_XX_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGoogleFrecentTopSiteUS() {
        val topSite = TopSite.Frecent(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("US", "US"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenFrecent) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                SupportUtils.GOOGLE_US_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectGoogleFrecentTopSiteXX() {
        val topSite = TopSite.Frecent(
            id = 1L,
            title = "Google",
            url = SupportUtils.GOOGLE_URL,
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        store.dispatch(SearchAction.SetRegionAction(RegionState("DE", "FR"))).joinBlocking()

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenFrecent) }
        verify { metrics.track(Event.TopSiteOpenGoogle) }
        verify {
            tabsUseCases.addTab.invoke(
                SupportUtils.GOOGLE_XX_URL,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleSelectProvidedTopSite() {
        val topSite = TopSite.Provided(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            clickUrl = "",
            imageUrl = "",
            impressionUrl = "",
            createdAt = 0
        )
        val controller = spyk(createController())

        every { controller.getAvailableSearchEngines() } returns listOf(searchEngine)

        controller.handleSelectTopSite(topSite)

        verify { metrics.track(Event.TopSiteOpenInNewTab) }
        verify { metrics.track(Event.TopSiteOpenProvided) }
        verify {
            tabsUseCases.addTab.invoke(
                url = topSite.url,
                selectTab = true,
                startLoading = true
            )
        }
        verify { activity.openToBrowser(BrowserDirection.FromHome) }
    }

    @Test
    fun handleStartBrowsingClicked() {
        var hideOnboardingInvoked = false
        createController(hideOnboarding = { hideOnboardingInvoked = true }).handleStartBrowsingClicked()

        assertTrue(hideOnboardingInvoked)
    }

    @Test
    fun handleReadPrivacyNoticeClicked() {
        createController().handleReadPrivacyNoticeClicked()
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = SupportUtils.getMozillaPageUrl(SupportUtils.MozillaPage.PRIVATE_NOTICE),
                newTab = true,
                from = BrowserDirection.FromHome
            )
        }
    }

    @Test
    fun handleToggleCollectionExpanded() {
        val collection = mockk<TabCollection>()
        createController().handleToggleCollectionExpanded(collection, true)
        verify { fragmentStore.dispatch(HomeFragmentAction.CollectionExpanded(collection, true)) }
    }

    @Test
    fun handleCloseTip() {
        val tip = mockk<Tip>()
        createController().handleCloseTip(tip)
        verify { fragmentStore.dispatch(HomeFragmentAction.RemoveTip(tip)) }
    }

    @Test
    fun handleCreateCollection() {
        createController().handleCreateCollection()

        verify {
            navController.navigate(
                match<NavDirections> { it.actionId == R.id.action_global_tabsTrayFragment },
                null
            )
        }
    }

    @Test
    fun handlePasteAndGo() {
        createController().handlePasteAndGo("text")

        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = "text",
                newTab = true,
                from = BrowserDirection.FromHome,
                engine = searchEngine
            )
            metrics.track(any<Event.PerformedSearch>())
        }

        createController().handlePasteAndGo("https://mozilla.org")
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = "https://mozilla.org",
                newTab = true,
                from = BrowserDirection.FromHome,
                engine = searchEngine
            )
            metrics.track(any<Event.EnteredUrl>())
        }
    }

    @Test
    fun handlePaste() {
        createController().handlePaste("text")

        verify {
            navController.navigate(
                match<NavDirections> { it.actionId == R.id.action_global_search_dialog },
                null
            )
        }
    }

    @Test
    fun handleRemoveCollectionsPlaceholder() {
        createController().handleRemoveCollectionsPlaceholder()

        verify {
            settings.showCollectionsPlaceholderOnHome = false
            fragmentStore.dispatch(HomeFragmentAction.RemoveCollectionsPlaceholder)
        }
    }

    @Test
    @Ignore("Can't instantiate proxy for class kotlin.Function0")
    fun handleMenuOpenedWhileSearchShowing() {
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.searchDialogFragment
        }

        createController().handleMenuOpened()

        verify {
            navController.navigateUp()
        }
    }

    @Test
    fun handleMenuOpenedWhileSearchNotShowing() {
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.homeFragment
        }

        createController().handleMenuOpened()

        verify(exactly = 0) {
            navController.navigateUp()
        }
    }

    @Test
    fun `WHEN private mode button is selected from home THEN handle mode change`() {
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.homeFragment
        }

        every { settings.incrementNumTimesPrivateModeOpened() } just Runs

        val newMode = BrowsingMode.Private
        val hasBeenOnboarded = true

        createController().handlePrivateModeButtonClicked(newMode, hasBeenOnboarded)

        verify {
            settings.incrementNumTimesPrivateModeOpened()
            HomeFragmentAction.ModeChange(Mode.fromBrowsingMode(newMode))
        }
    }

    @Test
    fun `WHEN private mode is selected on home from behind search THEN handle mode change`() {
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.searchDialogFragment
        }

        every { settings.incrementNumTimesPrivateModeOpened() } just Runs

        val url = "https://mozilla.org"
        val tab = createTab(
            id = "otherTab",
            url = url,
            private = false,
            engineSession = mockk(relaxed = true)
        )
        store.dispatch(TabListAction.AddTabAction(tab, select = true)).joinBlocking()

        val newMode = BrowsingMode.Private
        val hasBeenOnboarded = true

        createController().handlePrivateModeButtonClicked(newMode, hasBeenOnboarded)

        verify {
            settings.incrementNumTimesPrivateModeOpened()
            HomeFragmentAction.ModeChange(Mode.fromBrowsingMode(newMode))
            navController.navigate(
                BrowserFragmentDirections.actionGlobalSearchDialog(
                    sessionId = null
                )
            )
        }
    }

    @Test
    fun `WHEN private mode is deselected on home from behind search THEN handle mode change`() {
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.searchDialogFragment
        }

        val url = "https://mozilla.org"
        val tab = createTab(
            id = "otherTab",
            url = url,
            private = true,
            engineSession = mockk(relaxed = true)
        )
        store.dispatch(TabListAction.AddTabAction(tab, select = true)).joinBlocking()

        val newMode = BrowsingMode.Normal
        val hasBeenOnboarded = true

        createController().handlePrivateModeButtonClicked(newMode, hasBeenOnboarded)

        verify(exactly = 0) {
            settings.incrementNumTimesPrivateModeOpened()
        }
        verify {
            HomeFragmentAction.ModeChange(Mode.fromBrowsingMode(newMode))
            navController.navigate(
                BrowserFragmentDirections.actionGlobalSearchDialog(
                    sessionId = null
                )
            )
        }
    }

    @Test
    fun `WHEN handleReportSessionMetrics is called AND there are zero recent tabs THEN report Event#RecentTabsSectionIsNotVisible`() {
        every { homeFragmentState.recentTabs } returns emptyList()
        createController().handleReportSessionMetrics(homeFragmentState)
        verify(exactly = 0) {
            metrics.track(Event.RecentTabsSectionIsVisible)
        }
        verify {
            metrics.track(Event.RecentTabsSectionIsNotVisible)
        }
    }

    @Test
    fun `WHEN handleReportSessionMetrics is called AND there is at least one recent tab THEN report Event#RecentTabsSectionIsVisible`() {
        val recentTab: RecentTab = mockk(relaxed = true)
        every { homeFragmentState.recentTabs } returns listOf(recentTab)
        createController().handleReportSessionMetrics(homeFragmentState)
        verify(exactly = 0) {
            metrics.track(Event.RecentTabsSectionIsNotVisible)
        }
        verify {
            metrics.track(Event.RecentTabsSectionIsVisible)
        }
    }

    @Test
    fun `WHEN handleReportSessionMetrics is called AND there are zero recent bookmarks THEN report Event#RecentBookmarkCount(0)`() {
        every { homeFragmentState.recentBookmarks } returns emptyList()
        every { homeFragmentState.recentTabs } returns emptyList()
        createController().handleReportSessionMetrics(homeFragmentState)
        verify {
            metrics.track(Event.RecentBookmarkCount(0))
        }
    }

    @Test
    fun `WHEN handleReportSessionMetrics is called AND there is at least one recent bookmark THEN report Event#RecentBookmarkCount(1)`() {
        val recentBookmark: RecentBookmark = mockk(relaxed = true)
        every { homeFragmentState.recentBookmarks } returns listOf(recentBookmark)
        every { homeFragmentState.recentTabs } returns emptyList()
        createController().handleReportSessionMetrics(homeFragmentState)
        verify {
            metrics.track(Event.RecentBookmarkCount(1))
        }
    }

    @Test
    fun `WHEN handleTopSiteSettingsClicked is called THEN navigate to the HomeSettingsFragment AND report the interaction`() {
        createController().handleTopSiteSettingsClicked()

        verify {
            metrics.track(Event.TopSiteContileSettings)
        }
        verify {
            navController.navigate(
                match<NavDirections> {
                    it.actionId == R.id.action_global_homeSettingsFragment
                },
                null
            )
        }
    }

    @Test
    fun `WHEN handleSponsorPrivacyClicked is called THEN navigate to the privacy webpage AND report the interaction`() {
        createController().handleSponsorPrivacyClicked()

        verify {
            metrics.track(Event.TopSiteContilePrivacy)
        }
        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SPONSOR_PRIVACY),
                newTab = true,
                from = BrowserDirection.FromHome
            )
        }
    }

    @Test
    fun `WHEN handleOpenInPrivateTabClicked is called with a TopSite#Provided site THEN Event#TopSiteOpenContileInPrivateTab is reported`() {
        val topSite = TopSite.Provided(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            clickUrl = "",
            imageUrl = "",
            impressionUrl = "",
            createdAt = 0
        )
        createController().handleOpenInPrivateTabClicked(topSite)

        verify {
            metrics.track(Event.TopSiteOpenContileInPrivateTab)
        }
    }

    @Test
    fun `WHEN handleOpenInPrivateTabClicked is called with a Default, Pinned, or Frecent top site THEN TopSiteOpenInPrivateTab event is reported`() {
        val controller = createController()
        val topSite1 = TopSite.Default(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            createdAt = 0
        )
        val topSite2 = TopSite.Pinned(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            createdAt = 0
        )
        val topSite3 = TopSite.Frecent(
            id = 1L,
            title = "Mozilla",
            url = "mozilla.org",
            createdAt = 0
        )
        controller.handleOpenInPrivateTabClicked(topSite1)
        controller.handleOpenInPrivateTabClicked(topSite2)
        controller.handleOpenInPrivateTabClicked(topSite3)

        verify(exactly = 3) {
            metrics.track(Event.TopSiteOpenInPrivateTab)
        }
    }

    private fun createController(
        hideOnboarding: () -> Unit = { },
        registerCollectionStorageObserver: () -> Unit = { },
        showTabTray: () -> Unit = { },
        removeCollectionWithUndo: (tabCollection: TabCollection) -> Unit = { }
    ): DefaultSessionControlController {
        return DefaultSessionControlController(
            activity = activity,
            settings = settings,
            engine = engine,
            metrics = metrics,
            store = store,
            tabCollectionStorage = tabCollectionStorage,
            addTabUseCase = tabsUseCases.addTab,
            restoreUseCase = mockk(relaxed = true),
            reloadUrlUseCase = reloadUrlUseCase.reload,
            selectTabUseCase = selectTabUseCase.selectTab,
            fragmentStore = fragmentStore,
            navController = navController,
            viewLifecycleScope = scope,
            hideOnboarding = hideOnboarding,
            registerCollectionStorageObserver = registerCollectionStorageObserver,
            removeCollectionWithUndo = removeCollectionWithUndo,
            showTabTray = showTabTray
        )
    }
}
