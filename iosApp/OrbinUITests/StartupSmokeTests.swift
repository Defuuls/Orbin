import UIKit
import XCTest

/// Launches the installed production app without app-specific test flags or content fixtures.
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
        assertDynamicTypeIsAppliedOnce()
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

        // Reader Home has one subscription scope, including after drawer returns and cold launch.
        let socialTabs = app.buttons.matching(
            NSPredicate(format: "label == %@ OR label == %@", "For You", "Following")
        )
        XCTAssertEqual(socialTabs.count, 0, "Reader Home must not display social discovery/feed selectors")
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

    private func assertDynamicTypeIsAppliedOnce() {
        // UIKit's standard launch override changes both the app category and Compose's native
        // density. A second category multiplier shrinks Small to 0.85 * 0.88 instead of 0.88.
        func headingHeight(at category: UIContentSizeCategory) -> CGFloat {
            app.terminate()
            app.launchArguments = ["-UIPreferredContentSizeCategoryName", category.rawValue]
            app.launch()
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

    private func destinationHeader(_ identifier: String, title: String) -> XCUIElement {
        if identifier == "BOARDS" {
            // Boards uses the Coral wordmark header while the other destinations retain titles.
            return app.descendants(matching: .any).matching(
                NSPredicate(format: "identifier == %@ AND label == %@", "boards-home-header", "Orbin")
            ).firstMatch
        }
        return appBarTitle(title)
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
            waitUntilExists([destinationHeader(identifier, title: title)], description: "The " + title + " header")
            openDrawer()
            XCTAssertTrue(drawerEntry(identifier).isSelected, title + " announces its selected drawer state")
            drawerEntry(identifier).tap()
        }

        openDrawer()
        let settings = drawerEntry("SETTINGS")
        XCTAssertTrue(settings.exists && settings.isHittable)
        settings.tap()
        waitUntilExists([appBarTitle("Settings")], description: "Reader preferences use the Settings app bar")
        XCTAssertFalse(appBarTitle("Settings and privacy").exists, "Settings must not retain the social directory title")
        attachScreenshot(named: "ios-reader-settings")
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
