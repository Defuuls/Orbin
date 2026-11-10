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
        let done = labeled("Done")
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
        assertHomeAfterSetup()

        let tabs = ["Home", "Watched", "Boards", "Search", "Saved"].map(navigationControl)
        for (index, tab) in tabs.enumerated() {
            XCTAssertTrue(tab.exists && tab.isHittable, "Primary destination \(index) must be reachable")
            XCTAssertGreaterThanOrEqual(tab.frame.width, 44)
            XCTAssertGreaterThanOrEqual(tab.frame.height, 44)
        }
        let center = tabs[2]
        XCTAssertEqual(center.frame.midX, app.frame.midX, accuracy: 3, "The accessible Boards action occupies the center + position")

        // The center control must navigate, including when a provider's directory is unavailable.
        center.tap()
        let boardsSelected = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in self.navigationControl("Boards").isSelected },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [boardsSelected], timeout: 15), .completed)
        navigationControl("Home").tap()
        assertHomeAfterSetup()

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

    private func assertHomeAfterSetup() {
        let home = navigationControl("Home")
        waitUntilHittable([home, labeled("Done")], description: "Home navigation or release notes")
        dismissReleaseNotesIfPresent()
        waitUntilHittable([home], description: "Home after mandatory board setup")
        XCTAssertTrue(home.isSelected)
        XCTAssertFalse(labeled("Welcome to Orbin").exists, "Persisted setup must not reopen onboarding")
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label == %@", "Start browsing")).firstMatch.exists)

        // A live catalog can load, fail, or still be loading. All three must keep navigation
        // usable; when content is ready its For You tab must expose the selected state.
        let forYou = app.buttons.matching(NSPredicate(format: "label == %@", "For You")).firstMatch
        if forYou.waitForExistence(timeout: 10) {
            XCTAssertTrue(forYou.isSelected, "For You must announce its selected tab state")
            XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label == %@", "Following")).firstMatch.exists)
        }
    }

    private func waitForBoardDirectory(_ board: XCUIElement) {
        let retry = labeled("Retry")
        let done = labeled("Done")
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

    private func navigationControl(_ label: String) -> XCUIElement {
        let candidates = app.buttons.matching(NSPredicate(format: "label == %@", label))
        // A header action may share a tab's name. Require a real bottom button, not a text leaf.
        return candidates.allElementsBoundByIndex.first {
            $0.isHittable && $0.frame.minY > app.frame.height / 2 && $0.frame.height <= 120
        } ?? candidates.firstMatch
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
        let done = labeled("Done")
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
