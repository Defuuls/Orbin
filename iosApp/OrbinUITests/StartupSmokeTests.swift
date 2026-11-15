import UIKit
import XCTest

/// The first test launches the installed production app without app-specific test flags or
/// content fixtures, against the real board directory; it skips rather than fails when that
/// directory cannot be reached. The second launches with the test-only bundled board directory
/// (`-OrbinUITestBundledDirectory`, read only by debug builds) and captures the main screens at
/// default and largest Dynamic Type as result-bundle attachments.
///
/// XCTest runs these in name order on one installation: the live test needs a fresh install, so
/// it runs first, and the bundled test accepts either a finished or an unfinished setup.
final class StartupSmokeTests: XCTestCase {
    private static let bundledDirectoryArgument = "-OrbinUITestBundledDirectory"
    private static let bundledDirectoryEnvironment = "ORBIN_UI_TEST_BUNDLED_DIRECTORY"
    private static let liveDirectoryUnavailable =
        "Skipped: the real board directory did not load, so the live first-run check could not run. " +
        "This test depends on the network; testReaderScreensWithBundledBoardDirectory covers the app offline."

    private var app: XCUIApplication!
    private var hasOpenedFeed = false
    private var usesBundledDirectory = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        hasOpenedFeed = false
        usesBundledDirectory = false
    }

    func testFirstLaunchRequiresAPersistedBoardBeforeOpeningHome() throws {
        app.launch()
        advanceToBoardSelection()

        let startBrowsing = app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch
        let welcome = labeled("Welcome to Orbin")
        XCTAssertTrue(welcome.exists, "The native scene must display the shared onboarding UI")
        XCTAssertGreaterThan(welcome.frame.width, 0)
        XCTAssertGreaterThan(welcome.frame.height, 0)
        XCTAssertFalse(startBrowsing.isEnabled, "A fresh installation must not enter Home with zero followed boards")
        XCTAssertTrue(labeled("Follow at least one board to continue").exists)
        assertDynamicTypeIsAppliedOnce()
        attachScreenshot(named: "ios-onboarding-follow-required")

        // /3/ is a SFW board whose metadata appears first in the real 4chan directory.
        // Only board metadata is captured; the live feed is never attached as a screenshot.
        let board = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "/3/", "3DCG")
        ).firstMatch
        try waitForBoardDirectory(board)
        board.tap()
        let savedFollow = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in startBrowsing.exists && startBrowsing.isEnabled && startBrowsing.isHittable },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [savedFollow], timeout: 15), .completed, "Start enables after the followed board is saved")
        XCTAssertTrue(welcome.exists, "Following a board must keep setup open until Start is tapped")
        XCTAssertTrue(labeled("1 following").exists)
        attachScreenshot(named: "ios-onboarding-board-saved")

        hasOpenedFeed = true
        startBrowsing.tap()
        assertHomeAfterSetup(expectsReleaseNotes: true)
        assertLayoutAction("List view")
        layoutButton().tap()
        assertLayoutAction("Card view")
        layoutButton().tap()
        assertLayoutAction("Grid view")

        assertPrimaryDestinations()

        // Cold startup repairs a zero-follow install, so reaching Home again also proves that
        // the selected board survived process termination along with setup completion.
        app.terminate()
        app.launch()
        assertHomeAfterSetup()
        assertLayoutAction("Grid view") // Card remains selected after a cold launch.
        layoutButton().tap()
        assertLayoutAction("List view") // The third action completes Card → Grid.
    }

    /// Every main screen against the bundled board directory and its text-only threads, so the
    /// run is deterministic and its screenshots never show site content. Captured at the default
    /// text size and the largest accessibility size, and kept in the result bundle.
    func testReaderScreensWithBundledBoardDirectory() {
        usesBundledDirectory = true
        launchWithBundledDirectory(textSize: .large)
        completeSetupWithBundledDirectoryIfNeeded()
        captureMainScreens(named: "default-text")

        launchWithBundledDirectory(textSize: .accessibilityExtraExtraExtraLarge)
        openHomeAfterLaunch()
        captureMainScreens(named: "largest-text")
    }

    override func tearDownWithError() throws {
        // Live feeds are never captured; the bundled directory's feed is text-only fixture content.
        if let run = testRun, run.failureCount > 0, app != nil, !hasOpenedFeed || usesBundledDirectory {
            attachScreenshot(named: "ios-onboarding-failure")
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "ios-startup-accessibility-hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
        }
        app?.terminate()
        app = nil
    }

    private func launchWithBundledDirectory(textSize: UIContentSizeCategory) {
        app.terminate()
        // The flag carries a value so UIKit's "-key value" argument pairs stay aligned.
        app.launchArguments = [
            "-UIPreferredContentSizeCategoryName", textSize.rawValue,
            Self.bundledDirectoryArgument, "YES",
        ]
        app.launchEnvironment[Self.bundledDirectoryEnvironment] = "1"
        app.launch()
    }

    /// The live test usually finishes setup first; when it was skipped or stopped early, setup
    /// is completed here by following /3/ from the bundled directory.
    private func completeSetupWithBundledDirectoryIfNeeded() {
        let getStarted = app.buttons.matching(NSPredicate(format: "label == %@", "Get started")).firstMatch
        let startBrowsing = app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch
        let feedTab = navigationEntry("FEED")
        waitUntilExists(
            [getStarted, startBrowsing, releaseNotesDoneButton(), feedTab],
            description: "Onboarding, this release's notes, or Home with the bundled directory"
        )
        dismissReleaseNotesIfPresent()
        if getStarted.exists || startBrowsing.exists {
            advanceToBoardSelection()
            let board = app.descendants(matching: .any).matching(
                NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "/3/", "3DCG")
            ).firstMatch
            waitUntilHittable([board], description: "The bundled directory lists /3/ without the network")
            attachScreenshot(named: "ios-bundled-onboarding-default-text")
            // A follow saved by an earlier, unfinished run already enables Start.
            if !startBrowsing.isEnabled {
                board.tap()
            }
            let savedFollow = XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in startBrowsing.exists && startBrowsing.isEnabled && startBrowsing.isHittable },
                object: nil
            )
            XCTAssertEqual(XCTWaiter.wait(for: [savedFollow], timeout: 15), .completed, "Start enables after /3/ is followed")
            startBrowsing.tap()
        }
        openHomeAfterLaunch()
    }

    /// Home, once any release notes presented over it are dismissed.
    private func openHomeAfterLaunch() {
        let done = releaseNotesDoneButton()
        let feedTab = navigationEntry("FEED")
        waitUntilExists([done, feedTab], description: "Home or this release's notes")
        // The notes can arrive just after Home appears; give them a moment before using the tabs.
        if becomesHittable([done], timeout: 5) {
            done.tap()
        }
        waitUntilHittable([feedTab], description: "The Feed tab with the bundled directory")
    }

    /// Feed, Boards, the /3/ catalog, Library and Settings, each asserted and captured.
    private func captureMainScreens(named textSize: String) {
        tapNavigationEntry("FEED")
        waitUntilExists([appBarTitle("Feed")], description: "The Feed app bar")
        let feedGrid = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "reader-feed-grid")
        ).firstMatch
        waitUntilExists([feedGrid], description: "Home mounts the shared reader catalog")
        waitUntilExists(
            [containing("Bundled fixture: low-poly lighthouse")],
            description: "Home shows the followed /3/ thread from the bundled directory"
        )
        attachScreenshot(named: "ios-bundled-feed-" + textSize)

        tapNavigationEntry("BOARDS")
        waitUntilExists([destinationHeader("BOARDS", title: "Boards")], description: "The Boards header")
        let openBoard = app.descendants(matching: .any).matching(
            NSPredicate(format: "label BEGINSWITH %@ AND label CONTAINS %@", "Open ", "/3/")
        ).firstMatch
        waitUntilExists([openBoard], description: "Boards lists the followed /3/ from the bundled directory")
        attachScreenshot(named: "ios-bundled-boards-" + textSize)
        var swipes = 0
        while !(openBoard.exists && openBoard.isHittable) && swipes < 4 {
            app.swipeUp()
            swipes += 1
        }
        waitUntilHittable([openBoard], description: "The followed /3/ row opens its catalog")
        openBoard.tap()

        let catalogGrid = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "catalog-grid")
        ).firstMatch
        waitUntilExists([catalogGrid], description: "The /3/ catalog mounts its grid")
        waitUntilExists(
            [containing("Retopology workflow thread")],
            description: "The /3/ catalog shows the bundled threads"
        )
        attachScreenshot(named: "ios-bundled-catalog-" + textSize)
        let back = app.buttons.matching(NSPredicate(format: "label == %@", "Back")).firstMatch
        if becomesHittable([back], timeout: 5) {
            back.tap()
        }

        tapNavigationEntry("LIBRARY")
        waitUntilExists([appBarTitle("Library")], description: "The Library app bar")
        attachScreenshot(named: "ios-bundled-library-" + textSize)

        tapNavigationEntry("SETTINGS")
        waitUntilExists([appBarTitle("Settings")], description: "The Settings app bar")
        attachScreenshot(named: "ios-bundled-settings-" + textSize)

        // Leave on Home, as the live test does, so the next cold launch starts from the tabs.
        let settingsBack = app.buttons.matching(NSPredicate(format: "label == %@", "Back")).firstMatch
        waitUntilHittable([settingsBack], description: "Settings uses Back in its app bar")
        settingsBack.tap()
        waitUntilExists([appBarTitle("Library")], description: "Back returns to Library")
        tapNavigationEntry("FEED")
        waitUntilExists([appBarTitle("Feed")], description: "The Feed app bar")
    }

    /// Scrolling can tuck the bottom bar away; scroll back until the tab can be tapped.
    private func tapNavigationEntry(_ destination: String) {
        let entry = navigationEntry(destination)
        var swipes = 0
        while !becomesHittable([entry], timeout: swipes == 0 ? 5 : 2) && swipes < 3 {
            app.swipeDown()
            swipes += 1
        }
        waitUntilHittable([entry], description: "The " + destination + " tab")
        entry.tap()
    }

    private func containing(_ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
    }

    private func assertHomeAfterSetup(expectsReleaseNotes: Bool = false) {
        // A fresh install presents this release's notes over Home. Resolve and dismiss that
        // foreground modal before querying the covered navigation/accessibility tree.
        if expectsReleaseNotes {
            let done = releaseNotesDoneButton()
            XCTAssertTrue(done.waitForExistence(timeout: 15), "This release's notes appear after setup")
            waitUntilHittable([done], description: "The release notes dismissal action")
            done.tap()
        }
        let feedTab = navigationEntry("FEED")
        waitUntilHittable([feedTab], description: "The Feed tab after mandatory board setup")
        XCTAssertTrue(appBarTitle("Feed").exists, "The fixed app bar identifies the Feed destination")
        XCTAssertFalse(labeled("Welcome to Orbin").exists, "Persisted setup must not reopen onboarding")
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch.exists)

        // Home must use the actual reader layouts even when live catalogs are unavailable.
        // This check also runs after cold relaunch so returning users cannot enter the old feed.
        let readerGrid = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "reader-feed-grid")
        ).firstMatch
        XCTAssertTrue(readerGrid.waitForExistence(timeout: 15), "Home must mount the shared reader catalog")
        XCTAssertGreaterThan(readerGrid.frame.width, 0)
        XCTAssertGreaterThan(readerGrid.frame.height, 0)
        let legacyFeed = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@ OR identifier == %@", "feed_pager_pager", "feed_pager_actions")
        )
        XCTAssertEqual(legacyFeed.count, 0, "Home must not render the old full-screen video pager or action column")

        // Reader Home has one subscription scope, including after tab returns and cold launch.
        let socialTabs = app.buttons.matching(
            NSPredicate(format: "label == %@ OR label == %@", "For You", "Following")
        )
        XCTAssertEqual(socialTabs.count, 0, "Reader Home must not display social discovery/feed selectors")
    }

    /// The live directory is the network's to give: when it cannot load, the test is skipped with
    /// the screen and hierarchy attached instead of failing a build for an outage. A directory that
    /// loads is still held to every assertion that follows.
    private func waitForBoardDirectory(_ board: XCUIElement) throws {
        let retry = labeled("Retry")
        let done = releaseNotesDoneButton()
        var retries = 0
        for _ in 0..<4 {
            guard becomesHittable([board, retry, done], timeout: 30) else {
                throw skipForUnavailableDirectory("No board or Retry action appeared within 30 seconds.")
            }
            if board.exists && board.isHittable { return }
            if done.exists && done.isHittable {
                dismissReleaseNotesIfPresent()
            } else if retries == 0 && retry.exists && retry.isHittable {
                retries += 1
                retry.tap()
            } else {
                throw skipForUnavailableDirectory("The directory still offered Retry after one retry.")
            }
        }
        XCTFail("The board directory remained unavailable")
    }

    private func skipForUnavailableDirectory(_ detail: String) -> XCTSkip {
        attachScreenshot(named: "ios-live-directory-unavailable")
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "ios-live-directory-unavailable-hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
        return XCTSkip(Self.liveDirectoryUnavailable + " " + detail)
    }

    private func labeled(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func layoutButton() -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "identifier == %@", "reader-feed-mode")).firstMatch
    }

    private func assertLayoutAction(_ label: String) {
        let control = layoutButton()
        let ready = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                control.exists && control.isHittable && control.label.contains(label)
            },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 15), .completed, "Layout action: " + label)
    }

    private func assertDynamicTypeIsAppliedOnce() {
        // UIKit's standard launch override changes both the app category and Compose's native
        // density. A second category multiplier shrinks Small to 0.85 * 0.88 instead of 0.88.
        func headingHeight(at category: UIContentSizeCategory) -> CGFloat {
            app.terminate()
            app.launchArguments = ["-UIPreferredContentSizeCategoryName", category.rawValue]
            app.launch()
            advanceToBoardSelection()
            let heading = labeled("Welcome to Orbin")
            waitUntilExists([heading], description: "Onboarding at Dynamic Type " + category.rawValue)
            XCTAssertGreaterThan(heading.frame.height, 0)
            return heading.frame.height
        }

        let large = headingHeight(at: .large)
        let small = headingHeight(at: .small)
        XCTAssertEqual(small / large, 0.88, accuracy: 0.06, "Small text must receive one system-size factor")
        let restored = headingHeight(at: .large)
        XCTAssertEqual(restored, large, accuracy: 1, "A cold launch must restore the normal text size")
        let sizing = XCTAttachment(string: "Large: \(large) pt; Small: \(small) pt; restored: \(restored) pt; ratio: \(small / large)")
        sizing.name = "ios-dynamic-type-sizing"
        sizing.lifetime = .keepAlways
        add(sizing)
        app.launchArguments = []
    }

    // The wizard step is not persisted until setup completes. Every cold launch used for
    // Dynamic Type measurements must traverse the splash, appearance, and privacy steps.
    private func advanceToBoardSelection() {
        let getStarted = app.buttons.matching(NSPredicate(format: "label == %@", "Get started")).firstMatch
        let startBrowsing = app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch
        let done = releaseNotesDoneButton()
        waitUntilExists([getStarted, startBrowsing, done], description: "Onboarding or this release's notes")
        dismissReleaseNotesIfPresent()
        waitUntilExists([getStarted, startBrowsing], description: "The setup splash or board selection")
        if getStarted.exists {
            waitUntilHittable([getStarted], description: "The setup welcome Get started button")
            getStarted.tap()
            let chooseTheme = labeled("Choose your theme")
            waitUntilExists([chooseTheme], description: "The appearance setup step")
            let continueButton = app.buttons.matching(NSPredicate(format: "label == %@", "Continue")).firstMatch
            waitUntilHittable([continueButton], description: "The appearance setup Continue button")
            continueButton.tap()
            let privacyStep = labeled("Privacy & content")
            waitUntilExists([privacyStep], description: "The privacy setup step")
            waitUntilHittable([continueButton], description: "The privacy setup Continue button")
            continueButton.tap()
        }
        waitUntilExists([startBrowsing], description: "The disabled Start browsing button")
    }

    private func releaseNotesDoneButton() -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", "Done")).firstMatch
    }

    private func appBarTitle(_ title: String) -> XCUIElement {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@ AND label == %@", "reader-app-bar-title", title)
        ).firstMatch
    }

    private func destinationHeader(_ identifier: String, title: String) -> XCUIElement {
        if identifier == "BOARDS" {
            // Boards uses the Coral wordmark header while the other destinations retain titles.
            return app.descendants(matching: .any).matching(
                NSPredicate(format: "identifier == %@ AND label == %@", "boards-home-header", "Orbin")
            ).firstMatch
        }
        return appBarTitle(title)
    }

    private func navigationEntry(_ destination: String) -> XCUIElement {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "reader-navigation-entry-" + destination)
        ).firstMatch
    }

    private func assertPrimaryDestinations() {
        // The bottom bar is the only primary navigation: Boards, Feed, Library and Settings.
        let entries = ["BOARDS", "FEED", "LIBRARY", "SETTINGS"]
        let destinations = [("BOARDS", "Boards"), ("LIBRARY", "Library"), ("FEED", "Feed")]
        for (identifier, title) in destinations {
            for entryID in entries {
                let entry = navigationEntry(entryID)
                waitUntilHittable([entry], description: "Every primary tab must be reachable")
                XCTAssertGreaterThanOrEqual(entry.frame.width, 44)
                XCTAssertGreaterThanOrEqual(entry.frame.height, 44)
            }
            navigationEntry(identifier).tap()
            waitUntilExists([destinationHeader(identifier, title: title)], description: "The " + title + " header")
            XCTAssertTrue(navigationEntry(identifier).isSelected, title + " announces its selected tab state")
        }

        navigationEntry("LIBRARY").tap()
        waitUntilExists([appBarTitle("Library")], description: "Library opens on its last section")
        let settings = navigationEntry("SETTINGS")
        waitUntilHittable([settings], description: "The Settings tab")
        settings.tap()
        waitUntilExists([appBarTitle("Settings")], description: "Reader preferences use the Settings app bar")
        XCTAssertFalse(appBarTitle("Settings and privacy").exists, "Settings must not retain the social directory title")
        attachScreenshot(named: "ios-reader-settings")
        let back = app.buttons.matching(NSPredicate(format: "label == %@", "Back")).firstMatch
        waitUntilHittable([back], description: "Settings uses Back in its app bar")
        XCTAssertFalse(
            app.buttons.matching(NSPredicate(format: "label == %@", "Menu")).firstMatch.exists,
            "There is no navigation drawer"
        )
        back.tap()
        waitUntilExists([appBarTitle("Library")], description: "Back returns to Library")
        navigationEntry("FEED").tap()
        assertHomeAfterSetup()
    }

    private func waitUntilHittable(_ elements: [XCUIElement], description: String) {
        let visible = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in elements.contains { $0.exists && $0.isHittable } },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 30), .completed, description)
    }

    private func becomesHittable(_ elements: [XCUIElement], timeout: TimeInterval) -> Bool {
        let visible = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in elements.contains { $0.exists && $0.isHittable } },
            object: nil
        )
        return XCTWaiter.wait(for: [visible], timeout: timeout) == .completed
    }

    private func waitUntilExists(_ elements: [XCUIElement], description: String) {
        let visible = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in elements.contains { $0.exists } },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 30), .completed, description)
    }

    private func dismissReleaseNotesIfPresent() {
        let done = releaseNotesDoneButton()
        if done.exists && done.isHittable {
            done.tap()
        }
    }

    private func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
