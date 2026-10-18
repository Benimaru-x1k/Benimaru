package com.benimaru.official

import android.widget.LinearLayout
import android.widget.TextView

internal fun MainActivity.showDnsDialog() {
    data class Dns(val name: String, val desc: String, val host: String)
    val providers = listOf(
        Dns("Control D", getString(R.string.dns_blocks_ads), "p2.freedns.controld.com"),
        Dns("Cloudflare", getString(R.string.dns_fastest), "one.one.one.one"),
        Dns("Quad9", getString(R.string.dns_malicious), "dns.quad9.net"),
        Dns("NextDNS", getString(R.string.dns_personalized), "dns.nextdns.io"),
        Dns("Google", getString(R.string.dns_stable), "dns.google")
    )
    val current = findViewById<TextView>(R.id.tvStatusDns).text.toString()

    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sheet = Sheet(this)
        .icon(R.drawable.ic_lock)
        .title(getString(R.string.dlg_private_dns))
        .message(getString(R.string.dlg_private_dns_msg))

    providers.forEach { p ->
        list.addView(optionRow(this, p.name, "${p.desc}  ·  ${p.host}", selected = current.contains(p.host)) {
            sheet.dismiss()
            val cmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier ${p.host}"
            runAdbCommand(cmd, getString(R.string.toast_dns_set, p.name), showAd = true) { fetchSystemStatuses() }
        })
    }
    list.addView(optionRow(this, getString(R.string.w_default), getString(R.string.dns_off_desc), selected = current.contains(getString(R.string.w_default))) {
        sheet.dismiss()
        val cmd = "settings put global private_dns_mode default && settings delete global private_dns_specifier"
        runAdbCommand(cmd, getString(R.string.msg_dns_default), showAd = false) { fetchSystemStatuses() }
    })

    sheet.content(list).button(getString(R.string.btn_cancel), SheetStyle.TEXT).show()
}
