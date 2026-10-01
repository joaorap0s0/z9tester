package com.z9tether.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val candidates = mutableListOf<Pair<Int, String>>()
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val lp = cm.getLinkProperties(network) ?: continue
            val score = when {
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK) -> 100
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 80
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 70
                else -> 10
            }
            for (link in lp.linkAddresses) {
                val a = link.address
                if (a is Inet4Address && !a.isLoopbackAddress && isPrivate(a)) candidates += score to a.hostAddress
            }
        }
        candidates.maxByOrNull { it.first }?.second?.let { return it }
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()?.firstOrNull { !it.isLoopbackAddress && isPrivate(it) }?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun isPrivate(a: InetAddress): Boolean {
        val b = a.address.map { it.toInt() and 255 }
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)
    }
}
