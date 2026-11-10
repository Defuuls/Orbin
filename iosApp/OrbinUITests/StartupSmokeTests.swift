import XCTest

/// Launches the installed production application without test-only launch flags or content fixtures.
final class StartupSmokeTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
    }

    func testFirstLaunchShowsOnboardingAndTheEmptyTikTokHome() {
        app.launch()

        let startBrowsing = labeled("Start browsing")
        let done = labeled("Done")
        waitUntilHittable([startBrowsing, done], description: "Onboarding or this release's notes")
        dismissReleaseNotesIfPresent()
        waitUntilHittable([startBrowsing], description: "Start browsing with no boards selected")

        let welcome = labeled("Welcome to Orbin")
        XCTAssertTrue(welcome.exists, "The native scene must display the shared onboarding UI")
        XCTAssertGreaterThan(welcome.frame.width, 0)
        XCTAssertGreaterThan(welcome.frame.height, 0)
        XCTAssertTrue(labeled("You can follow boards later").exists, "No board may be selected in this smoke test")
        attachScreenshot(named: "ios-onboarding")

        // Do not tap a board: the resulting Home contains no user-generated images or video.
        startBrowsing.tap()
        let forYou = labeled("For You")
        waitUntilHittable([forYou, done], description: "TikTok Home or release notes")
        dismissReleaseNotesIfPresent()
        if startBrowsing.exists && startBrowsing.isHittable {
            // A notes dialog can appear while first-run persistence is loading.
            startBrowsing.tap()
        }
        waitUntilHittable([forYou], description: "TikTok For You feed")
        XCTAssertTrue(labeled("Following").exists)
        XCTAssertTrue(labeled("Nothing here yet").waitForExistence(timeout: 15))

        let tabs = ["Home", "Watched", "Boards", "Search", "Saved"].map(navigationControl)
        for (index, tab) in tabs.enumerated() {
            XCTAssertTrue(tab.exists && tab.isHittable, "Primary destination \(index) must be reachable")
            XCTAssertGreaterThanOrEqual(tab.frame.width, 44)
            XCTAssertGreaterThanOrEqual(tab.frame.height, 44)
        }
        let center = tabs[2]
        XCTAssertEqual(center.frame.midX, app.frame.midX, accuracy: 3, "The accessible Boards action occupies the center + position")
        attachScreenshot(named: "ios-empty-tiktok-home")

        // The center control must navigate, including when a provider's directory is unavailable.
        center.tap()
        let leftHome = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: forYou)
        XCTAssertEqual(XCTWaiter.wait(for: [leftHome], timeout: 15), .completed)
        navigationControl("Home").tap()
        waitUntilHittable([forYou], description: "Home after using the center Boards action")
    }

    override func tearDownWithError() throws {
        if let run = testRun, run.hasSucceeded == false, app != nil {
            attachScreenshot(named: "ios-startup-failure")
        }
        app?.terminate()
        app = nil
    }

    private func labeled(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
    }

    private func navigationControl(_ label: String) -> XCUIElement {
        let candidates = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label))
        // A page title may share a tab's name. Select the real bottom control, not its ancestor.
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
