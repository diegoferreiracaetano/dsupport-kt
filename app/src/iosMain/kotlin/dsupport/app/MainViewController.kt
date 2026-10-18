package dsupport.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

private const val DEFAULT_SERVER = "http://localhost:8080"

fun MainViewController(): UIViewController {
    val configured = NSBundle.mainBundle.objectForInfoDictionaryKey("DSupportServer") as? String
    return ComposeUIViewController { App(configured?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER) }
}
