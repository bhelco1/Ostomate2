// Renders the Google Play feature graphic (1024x500, 24-bit PNG, no alpha): the app icon's
// glyph centered on brand blue. Geometry is copied from the adaptive icon source
// (androidApp/src/main/res/drawable/ic_launcher_foreground.xml + ic_launcher_background.xml),
// so the graphic stays in step with the launcher icon. Drawn rather than composited from the
// iOS 1024 PNG because that PNG's background is not #2563EB and showed as a visible square.
//
// Usage: swift scripts/feature_graphic.swift fastlane/metadata/android/en-US/images/featureGraphic.png
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let width = 1024
let height = 500
let outPath = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "featureGraphic.png"

guard let srgb = CGColorSpace(name: CGColorSpace.sRGB),
      let ctx = CGContext(
          data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
          space: srgb, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue,
      )
else { fatalError("could not create bitmap context") }

ctx.setFillColor(CGColor(srgbRed: 0x25 / 255.0, green: 0x63 / 255.0, blue: 0xEB / 255.0, alpha: 1))
ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))

// Map the icon's 108x108 viewport onto the full height, centered, with y pointing down like
// the vector source.
let scale = CGFloat(height) / 108.0
ctx.translateBy(x: (CGFloat(width) - 108 * scale) / 2, y: CGFloat(height))
ctx.scaleBy(x: scale, y: -scale)

// Ring: "M74.92,52.17 A21,21 0 1 1 55.83,33.08" is a 280-degree arc of radius 21 around
// (54,54), from -5 degrees sweeping to 275 degrees, gap at the top right.
let center = CGPoint(x: 54, y: 54)
let radius: CGFloat = 21
let start = -5.0 * Double.pi / 180
let sweep = 280.0 * Double.pi / 180
let steps = 560
let ring = CGMutablePath()
for i in 0...steps {
    let a = start + sweep * Double(i) / Double(steps)
    let p = CGPoint(x: center.x + radius * CGFloat(cos(a)), y: center.y + radius * CGFloat(sin(a)))
    if i == 0 { ring.move(to: p) } else { ring.addLine(to: p) }
}
ctx.setStrokeColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
ctx.setLineWidth(9)
ctx.setLineCap(.round)
ctx.setLineJoin(.round)
ctx.addPath(ring)
ctx.strokePath()

// Dot: "M63.85,39.15 a5,5 0 1 1 10,0 a5,5 0 1 1 -10,0" is a circle of radius 5 at (68.85,39.15).
ctx.setFillColor(CGColor(srgbRed: 1, green: 1, blue: 1, alpha: 1))
ctx.fillEllipse(in: CGRect(x: 63.85, y: 34.15, width: 10, height: 10))

guard let image = ctx.makeImage(),
      let dest = CGImageDestinationCreateWithURL(
          URL(fileURLWithPath: outPath) as CFURL, UTType.png.identifier as CFString, 1, nil,
      )
else { fatalError("could not create image destination") }
CGImageDestinationAddImage(dest, image, nil)
guard CGImageDestinationFinalize(dest) else { fatalError("could not write \(outPath)") }
print("wrote \(outPath) (\(width)x\(height))")
