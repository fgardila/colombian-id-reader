@file:OptIn(ExperimentalForeignApi::class)

package dev.code93.colombian_id_reader.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CAShapeLayer
import platform.QuartzCore.kCAFillRuleEvenOdd
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIView

/** ID-1 card aspect ratio (ISO/IEC 7810): 85.6mm x 54mm. */
private const val CARD_ASPECT = 54.0 / 85.6
private const val WINDOW_WIDTH_FRACTION = 0.85

/**
 * Dark scrim with a transparent card-shaped window as a framing guide
 * (iOS twin of ScannerOverlay). The window is a guide only — frames
 * are analyzed uncropped.
 */
internal class ScannerOverlayView(
    instructionText: String
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    private val scrimLayer = CAShapeLayer()
    private val instructionLabel = UILabel()

    init {
        userInteractionEnabled = false

        scrimLayer.fillRule = kCAFillRuleEvenOdd
        scrimLayer.fillColor = UIColor.blackColor.colorWithAlphaComponent(0.6).CGColor
        layer.addSublayer(scrimLayer)

        instructionLabel.text = instructionText
        instructionLabel.textColor = UIColor.whiteColor
        instructionLabel.textAlignment = NSTextAlignmentCenter
        instructionLabel.numberOfLines = 0
        instructionLabel.font = UIFont.systemFontOfSize(16.0)
        addSubview(instructionLabel)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }

        val windowWidth = width * WINDOW_WIDTH_FRACTION
        val windowHeight = windowWidth * CARD_ASPECT
        val windowRect = CGRectMake(
            (width - windowWidth) / 2.0,
            (height - windowHeight) / 2.0,
            windowWidth,
            windowHeight
        )

        scrimLayer.frame = bounds
        val path = UIBezierPath.bezierPathWithRect(bounds)
        path.appendPath(
            UIBezierPath.bezierPathWithRoundedRect(windowRect, cornerRadius = 16.0)
        )
        scrimLayer.path = path.CGPath

        val windowBottom = (height + windowHeight) / 2.0
        instructionLabel.setFrame(
            CGRectMake(24.0, windowBottom + 24.0, width - 48.0, 64.0)
        )
    }
}
