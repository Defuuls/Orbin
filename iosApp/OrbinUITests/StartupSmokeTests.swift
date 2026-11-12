import XCTest

/// Launches the installed production application without test-only launch flags or content fixtures.
final class StartupSmokeTests: XCTestCase {
    private var app: XCUIApplication!
    private var hasOpenedFeed = false

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        hasOpenedFeed = false
    }

    func testFirstLaunchRequiresAPersistedBoardBeforeOpeningHome() {
        app.launch()

        let startBrowsing = app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch
        let done = releaseNotesDoneButton()
        waitUntilExists([startBrowsing, done], description: "Onboarding or this release's notes")
        dismissReleaseNotesIfPresent()
        waitUntilExists([startBrowsing], description: "The disabled Start browsing button")

        let welcome = labeled("Welcome to Orbin")
        XCTAssertTrue(welcome.exists, "The native scene must display the shared onboarding UI")
        XCTAssertGreaterThan(welcome.frame.width, 0)
        XCTAssertGreaterThan(welcome.frame.height, 0)
        XCTAssertFalse(startBrowsing.isEnabled, "A fresh installation must not enter Home with zero followed boards")
        XCTAssertTrue(labeled("Follow at least one board to continue").exists)
        attachScreenshot(named: "ios-onboarding-follow-required")

        // /3/ is a SFW board whose metadata appears first in the real 4chan directory.
        // Only board metadata is captured; the live feed is never attached as a screenshot.
        let board = app.descendants(matching: .any).matching(
            NSPredicate(format: "label CONTAINS %@ AND label CONTAINS %@", "/3/", "3DCG")
        ).firstMatch
        waitForBoardDirectory(board)
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
        assertFeedTabsChangeSelection()

        assertDrawerDestinations()

        // Cold startup repairs a zero-follow install, so reaching Home again also proves that
        // the selected board survived process termination along with setup completion.
        app.terminate()
        app.launch()
        assertHomeAfterSetup()
    }

    override func tearDownWithError() throws {
        if let run = testRun, run.failureCount > 0, app != nil, !hasOpenedFeed {
            attachScreenshot(named: "ios-onboarding-failure")
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "ios-startup-accessibility-hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
        }
        app?.terminate()
        app = nil
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
        let menu = menuButton()
        waitUntilHittable([menu], description: "Home Menu after mandatory board setup")
        XCTAssertTrue(appBarTitle("Home").exists, "The fixed app bar identifies the Home destination")
        XCTAssertFalse(labeled("Welcome to Orbin").exists, "Persisted setup must not reopen onboarding")
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch.exists)

        // Home must use the actual reader list/grid even when live catalogs are unavailable.
        // This check also runs after cold relaunch so returning users cannot enter the old feed.
        let readerGrid = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "reader-feed-grid")
        ).firstMatch
        XCTAssertTrue(readerGrid.waitForExistence(timeout: 15), "Home must mount the shared reader catalog")
        XCTAssertGreaterThan(readerGrid.frame.width, 0)
        XCTAssertGreaterThan(readerGrid.frame.height, 0)
        let legacyFeed = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@ OR identifier == %@", "tiktok_feed_pager", "tiktok_feed_actions")
        )
        XCTAssertEqual(legacyFeed.count, 0, "Home must not render the old full-screen video pager or action column")

        // Catalog loading, empty results and failures retain the app bar and feed controls.
        let forYou = app.buttons.matching(NSPredicate(format: "label == %@", "For You")).firstMatch
        XCTAssertTrue(forYou.waitForExistence(timeout: 15), "Feed controls must remain available in every loading state")
        XCTAssertTrue(forYou.isSelected, "For You must announce its selected tab state")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == %@", "Following")).firstMatch.exists)
    }

    private func assertFeedTabsChangeSelection() {
        let following = app.buttons.matching(NSPredicate(format: "label == %@", "Following")).firstMatch
        let forYou = app.buttons.matching(NSPredicate(format: "label == %@", "For You")).firstMatch
        following.tap()
        assertFeedSelection(
            selected: "reader-feed-following",
            unselected: "reader-feed-for-you",
            message: "Following must select its board-following feed even while catalogs are unavailable"
        )
        forYou.tap()
        assertFeedSelection(
            selected: "reader-feed-for-you",
            unselected: "reader-feed-following",
            message: "For You must return to its independent discovery feed"
        )
    }

    private func assertFeedSelection(selected: String, unselected: String, message: String) {
        // Read both traits in one snapshot: separate full-tree queries can each take seconds
        // on a busy simulator even after the app has already switched feeds.
        let matchingStates = app.buttons.matching(
            NSPredicate(
                format: "(identifier == %@ AND selected == true) OR (identifier == %@ AND selected == false)",
                selected, unselected
            )
        )
        let selection = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in matchingStates.count == 2 },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [selection], timeout: 15), .completed, message)
    }

    private func waitForBoardDirectory(_ board: XCUIElement) {
        let retry = labeled("Retry")
        let done = releaseNotesDoneButton()
        var retries = 0
        for _ in 0..<4 {
            waitUntilHittable([board, retry, done], description: "The SFW /3/ board or a directory retry action")
            if board.exists && board.isHittable { return }
            if done.exists && done.isHittable {
                dismissReleaseNotesIfPresent()
            } else if retries == 0 && retry.exists && retry.isHittable {
                retries += 1
                retry.tap()
            } else {
                XCTFail("The real board directory must load before first-run setup can complete")
                return
            }
        }
        XCTFail("The board directory remained unavailable")
    }

    private func labeled(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func releaseNotesDoneButton() -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", "Done")).firstMatch
    }

    private func menuButton() -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label == %@", "Menu")).firstMatch
    }

    private func appBarTitle(_ title: String) -> XCUIElement {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@ AND label == %@", "reader-app-bar-title", title)
        ).firstMatch
    }

    private func drawerEntry(_ destination: String) -> XCUIElement {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier == %@", "reader-navigation-entry-" + destination)
        ).firstMatch
    }

    private func openDrawer() {
        let menu = menuButton()
        waitUntilHittable([menu], description: "The app bar Menu button")
        XCTAssertGreaterThanOrEqual(menu.frame.width, 44)
        XCTAssertGreaterThanOrEqual(menu.frame.height, 44)
        menu.tap()
        waitUntilHittable([drawerEntry("FEED")], description: "The navigation drawer")
    }

    private func assertDrawerDestinations() {
        let destinations = [
            ("FEED", "Home"), ("WATCHED", "Watched"), ("BOARDS", "Boards"),
            ("SEARCH", "Search"), ("SAVED", "Saved")
        ]
        for (identifier, title) in destinations {
            openDrawer()
            for (entryID, _) in destinations {
                let entry = drawerEntry(entryID)
                XCTAssertTrue(entry.exists && entry.isHittable, "Every primary drawer destination must be reachable")
                XCTAssertGreaterThanOrEqual(entry.frame.width, 44)
                XCTAssertGreaterThanOrEqual(entry.frame.height, 44)
            }
            drawerEntry(identifier).tap()
            waitUntilExists([appBarTitle(title)], description: "The " + title + " app bar")
            openDrawer()
            XCTAssertTrue(drawerEntry(identifier).isSelected, title + " announces its selected drawer state")
            drawerEntry(identifier).tap()
        }

        openDrawer()
        let settings = drawerEntry("SETTINGS")
        XCTAssertTrue(settings.exists && settings.isHittable)
        settings.tap()
        let back = app.buttons.matching(NSPredicate(format: "label == %@", "Back")).firstMatch
        waitUntilHittable([back], description: "Settings uses Back in its app bar")
        XCTAssertFalse(menuButton().exists, "A child screen must not offer the navigation drawer")
        back.tap()
        waitUntilExists([appBarTitle("Saved")], description: "Back returns to the previous drawer destination")
        openDrawer()
        drawerEntry("FEED").tap()
        assertHomeAfterSetup()
    }

    private func waitUntilHittable(_ elements: [XCUIElement], description: String) {
        let visible = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in elements.contains { $0.exists && $0.isHittable } },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [visible], timeout: 30), .completed, description)
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
