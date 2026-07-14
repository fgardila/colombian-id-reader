// swift-tools-version:5.9
import PackageDescription

// The url/checksum pair is rewritten by .github/workflows/release.yml:
// the XCFramework zip is built once, its checksum committed, and the
// version tag created on that commit, so the Package.swift resolved at
// any tag always matches the release asset of that same tag.
let package = Package(
    name: "ColombianIdReader",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "SharedLogic", targets: ["SharedLogic"])
    ],
    targets: [
        .binaryTarget(
            name: "SharedLogic",
            url: "https://github.com/fgardila/colombian-id-reader/releases/download/v0.1.0/SharedLogic.xcframework.zip",
            checksum: "eb9e05b58c276a54f5e773f7489e91fb1e6f984b3bed9885caf933112b2bcbdc"
        )
    ]
)
