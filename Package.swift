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
            url: "https://github.com/fgardila/colombian-id-reader/releases/download/v0.1.1/SharedLogic.xcframework.zip",
            checksum: "122ef97084b48c8df7bb52625c5a96139094a516ec784022c759fe50ec535ddb"
        )
    ]
)
