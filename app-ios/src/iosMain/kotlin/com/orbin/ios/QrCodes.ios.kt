package com.orbin.ios

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
import platform.UIKit.UIAction
import platform.UIKit.UIAdaptivePresentationControllerDelegateProtocol
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UINavigationController
import platform.UIKit.UIPresentationController
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.UIKit.navigationItem
import platform.UIKit.presentationController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun qrCodeImage(payload: String): ImageBitmap? {
    val data = NSString.create(string = payload).dataUsingEncoding(NSUTF8StringEncoding)
    val filter = CIFilter.filterWithName("CIQRCodeGenerator")
    filter?.setValue(data, forKey = "inputMessage")
    // M survives a glare or a scratched screen protector without making the code too dense.
    filter?.setValue("M", forKey = "inputCorrectionLevel")
    val image = filter?.outputImage?.takeIf { data != null } ?: return null
    val cgImage = CIContext.contextWithOptions(null).createCGImage(image, fromRect = image.extent) ?: return null
    return try {
        UIImagePNGRepresentation(UIImage.imageWithCGImage(cgImage))
            ?.toByteArray()
            ?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
    } finally {
        CGImageRelease(cgImage)
    }
}

internal actual fun scanQrCode(
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit,
) {
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> presentScanner(onResult, onFailure)
        AVAuthorizationStatusNotDetermined ->
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (granted) presentScanner(onResult, onFailure) else onFailure(CAMERA_DENIED)
                }
            }
        else -> onFailure(CAMERA_DENIED)
    }
}

private const val CAMERA_DENIED = "Allow camera access for Orbin in Settings to scan QR codes"

/** Keeps the presented scanner's session and delegate alive until it closes. */
private var activeScanner: QrScanner? = null

@OptIn(ExperimentalForeignApi::class)
private fun presentScanner(
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit,
) {
    val presenter = topViewController() ?: return
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, null) }
    val session = AVCaptureSession()
    if (input == null || !session.canAddInput(input)) {
        onFailure("This device has no camera to scan with")
        return
    }
    session.addInput(input)
    val output = AVCaptureMetadataOutput()
    if (!session.canAddOutput(output)) {
        onFailure("This device can't scan QR codes")
        return
    }
    session.addOutput(output)
    val scanner = QrScanner(session, onResult)
    activeScanner = scanner
    output.setMetadataObjectsDelegate(scanner, dispatch_get_main_queue())
    output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

    val content = UIViewController(nibName = null, bundle = null)
    content.title = "Scan QR code"
    val preview = AVCaptureVideoPreviewLayer(session = session)
    preview.videoGravity = AVLayerVideoGravityResizeAspectFill
    preview.frame = UIScreen.mainScreen.bounds
    content.view.layer.addSublayer(preview)
    content.navigationItem.leftBarButtonItem =
        UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel,
            primaryAction = UIAction.actionWithHandler { scanner.close() },
        )
    val navigation = UINavigationController(rootViewController = content)
    navigation.presentationController?.delegate = scanner
    scanner.controller = navigation
    presenter.presentViewController(navigation, animated = true, completion = null)
    // startRunning blocks while the camera spins up, so it stays off the main thread.
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { session.startRunning() }
}

private class QrScanner(
    private val session: AVCaptureSession,
    private val onResult: (String) -> Unit,
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol {
    var controller: UIViewController? = null
    private var done = false

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (done) return
        val text =
            didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstNotNullOfOrNull { it.stringValue }
                ?: return
        close()
        Haptics.success()
        onResult(text)
    }

    /** Swiped down: the sheet is already gone, so only the camera stops. */
    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        stop()
    }

    fun close() {
        controller?.dismissViewControllerAnimated(true, completion = null)
        stop()
    }

    private fun stop() {
        if (done) return
        done = true
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0u)) { session.stopRunning() }
        if (activeScanner === this) activeScanner = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
