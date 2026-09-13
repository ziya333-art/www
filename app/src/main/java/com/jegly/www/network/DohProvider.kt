package com.jegly.www.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

// Zuzu-aligned DoH providers, mirroring zuzu's no-log set. Reserved slot for DNSwarden
// with custom filters once endpoint is provided.
enum class DohProvider(val key: String, val displayName: String, val dohUrl: String, val bootstrap: List<String>) {
    DNSFORGE(
        key = "dnsforge",
        displayName = "DNSforge Hard",
        dohUrl = "https://hard.dnsforge.de/dns-query",
        bootstrap = listOf("49.12.222.213", "88.198.122.154")
    ),
    PUBLIC_RDNS(
        key = "public-rdns",
        displayName = "Public-RDNS Full",
        dohUrl = "https://full.public-rdns.com/dns-query",
        bootstrap = listOf("37.27.125.218")
    ),
    LEDNS(
        key = "ledns",
        displayName = "ledns",
        dohUrl = "https://ledns.eu/dns-query",
        bootstrap = listOf("51.75.96.82")
    );

    companion object {
        fun fromKey(key: String?): DohProvider =
            values().firstOrNull { it.key == key } ?: DNSFORGE
    }
}

/**
 * SwitchableDohDns — unchanged logic, now over zuzu set.
 */
class SwitchableDohDns(
    bootstrapClient: OkHttpClient,
    private val providerKeyLookup: () -> String?
) : Dns {

    private val resolvers: Map<DohProvider, Dns> = DohProvider.values().associateWith { p ->
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(p.dohUrl.toHttpUrl())
            .bootstrapDnsHosts(p.bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            .post(true)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = DohProvider.fromKey(providerKeyLookup())
        val resolver = resolvers[provider] ?: return Dns.SYSTEM.lookup(hostname)
        return try {
            resolver.lookup(hostname)
        } catch (e: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}
