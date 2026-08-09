package com.jegly.www.network

/** One entry of the `Sec-CH-UA` brand list. */
data class BrandVersion(val brand: String, val majorVersion: String, val fullVersion: String)

/**
 * User-Agent Client Hints to send alongside a spoofed UA string.
 *
 * Changing the UA string alone does not disguise a Chromium browser. Chromium also sends the
 * `Sec-CH-UA*` request headers, built from the real engine, platform, OS version and device model,
 * and any site that reads them sees straight through the spoof — so before this existed, picking
 * "Desktop Firefox" advertised Firefox in one header and "Android 15, Pixel 9, Chrome 1xx" in the
 * next three.
 *
 * Honest limits. WebView exposes no way to suppress client hints entirely, so the non-Chromium
 * templates below cannot be made truly silent the way real Firefox or Safari are — the best
 * available is to stop the *true* device and engine from being reported, which is what the
 * placeholder brand plus a matching platform does. And low-entropy hints are still sent on every
 * request, so this reduces the mismatch, it does not make the spoof airtight.
 */
data class UaClientHints(
    val brands: List<BrandVersion>,
    val fullVersion: String,
    val platform: String,
    val platformVersion: String,
    val architecture: String,
    val model: String,
    val mobile: Boolean,
    /** 64 for desktop templates; 0 means "don't report", which is what phones send. */
    val bitness: Int
)

/**
 * "Not.A/Brand" is Chromium's own GREASE entry — a deliberately meaningless brand it includes to
 * stop sites hard-coding the list. Used alone for the non-Chromium templates: it is the closest
 * thing to "no brand" that the header format can express.
 */
private val GREASE = BrandVersion("Not.A/Brand", "99", "99.0.0.0")

enum class UserAgentTemplate(
    val key: String,
    val displayName: String,
    val uaString: String,
    /** Null for [DEFAULT] only — nothing is spoofed there, so WebView's own hints stay untouched. */
    val clientHints: UaClientHints? = null
) {
    DEFAULT(
        "default", "Default (WebView)", ""
    ),
    // Mobile
    CHROME_ANDROID(
        "chrome_android", "Chrome Android",
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36",
        UaClientHints(
            brands = listOf(
                BrandVersion("Chromium", "136", "136.0.0.0"),
                BrandVersion("Google Chrome", "136", "136.0.0.0"),
                GREASE
            ),
            fullVersion = "136.0.0.0",
            platform = "Android",
            platformVersion = "15.0.0",
            architecture = "",
            model = "Pixel 9",
            mobile = true,
            bitness = 0
        )
    ),
    FIREFOX_ANDROID(
        "firefox_android", "Firefox Android",
        "Mozilla/5.0 (Android 15; Mobile; rv:127.0) Gecko/127.0 Firefox/127.0",
        // Firefox sends no client hints at all. Nothing here can reproduce that, so this reports
        // only the platform the UA string already claims and no engine or device.
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "Android",
            platformVersion = "15.0.0",
            architecture = "",
            model = "",
            mobile = true,
            bitness = 0
        )
    ),
    SAMSUNG_INTERNET(
        "samsung_internet", "Samsung Internet",
        "Mozilla/5.0 (Linux; Android 15; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/27.0 Chrome/130.0.0.0 Mobile Safari/537.36",
        UaClientHints(
            brands = listOf(
                BrandVersion("Chromium", "130", "130.0.0.0"),
                BrandVersion("Samsung Internet", "27", "27.0.0.0"),
                GREASE
            ),
            fullVersion = "130.0.0.0",
            platform = "Android",
            platformVersion = "15.0.0",
            architecture = "",
            model = "SM-S928B",
            mobile = true,
            bitness = 0
        )
    ),
    SAFARI_IOS(
        "safari_ios", "Safari iOS",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
        // Safari sends no client hints either — same reasoning as Firefox above.
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "iOS",
            platformVersion = "18.0.0",
            architecture = "",
            model = "",
            mobile = true,
            bitness = 0
        )
    ),
    // Desktop
    DESKTOP_CHROME(
        "desktop_chrome", "Desktop Chrome",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36",
        UaClientHints(
            brands = listOf(
                BrandVersion("Chromium", "136", "136.0.0.0"),
                BrandVersion("Google Chrome", "136", "136.0.0.0"),
                GREASE
            ),
            fullVersion = "136.0.0.0",
            platform = "Windows",
            platformVersion = "15.0.0",
            architecture = "x86",
            model = "",
            mobile = false,
            bitness = 64
        )
    ),
    DESKTOP_FIREFOX(
        "desktop_firefox", "Desktop Firefox",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0",
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "Windows",
            platformVersion = "15.0.0",
            architecture = "x86",
            model = "",
            mobile = false,
            bitness = 64
        )
    ),
    DESKTOP_SAFARI(
        "desktop_safari", "Desktop Safari",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "macOS",
            platformVersion = "14.7.0",
            architecture = "x86",
            model = "",
            mobile = false,
            bitness = 64
        )
    ),
    DESKTOP_EDGE(
        "desktop_edge", "Desktop Edge",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36 Edg/136.0.0.0",
        UaClientHints(
            brands = listOf(
                BrandVersion("Chromium", "136", "136.0.0.0"),
                BrandVersion("Microsoft Edge", "136", "136.0.0.0"),
                GREASE
            ),
            fullVersion = "136.0.0.0",
            platform = "Windows",
            platformVersion = "15.0.0",
            architecture = "x86",
            model = "",
            mobile = false,
            bitness = 64
        )
    ),
    // Special
    GOOGLEBOT(
        "googlebot", "Googlebot",
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        // A crawler that advertises a phone model is not a crawler. Everything identifying is blank.
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "",
            platformVersion = "",
            architecture = "",
            model = "",
            mobile = false,
            bitness = 0
        )
    ),
    STEALTH(
        "stealth", "Stealth (Minimal)",
        "Mozilla/5.0 (compatible; MSIE 9.0)",
        // The point of this one is minimum surface, so the hints carry nothing either.
        UaClientHints(
            brands = listOf(GREASE),
            fullVersion = "",
            platform = "",
            platformVersion = "",
            architecture = "",
            model = "",
            mobile = false,
            bitness = 0
        )
    );

    companion object {
        fun fromKey(key: String?): UserAgentTemplate =
            entries.find { it.key == key } ?: DEFAULT
    }
}
