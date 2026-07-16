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
            url: "https://github.com/fgardila/colombian-id-reader/releases/download/v0.2.0/SharedLogic.xcframework.zip",
            checksum: "723672e8d79df12ea5a9093f98549f086272a97bec3f4d250036485c911efd4f"
        )
    ]
)
