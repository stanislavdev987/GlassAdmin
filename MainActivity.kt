package com.glassfiles.admin

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════
// Colors
// ═══════════════════════
private val BG = Color(0xFF09090B)
private val Card = Color(0xFF111113)
private val Card2 = Color(0xFF18181B)
private val Border = Color(0xFF27272A)
private val T1 = Color(0xFFE4E4E7)
private val T2 = Color(0xFF71717A)private val T3 = Color(0xFF52525B)
private val Accent = Color(0xFF22C55E)
private val Bl = Color(0xFF3B82F6)
private val Red = Color(0xFFEF4444)
private val Orange = Color(0xFFF59E0B)
private val Purple = Color(0xFFA78BFA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = Card)) {
                AdminApp()
            }
        }
    }
}

// ═══════════════════════
// Prefs
// ═══════════════════════
object AdminPrefs {
    private fun p(c: Context): SharedPreferences = c.getSharedPreferences("admin", Context.MODE_PRIVATE)
    fun getUrl(c: Context) = p(c).getString("url", "") ?: ""
    fun getKey(c: Context) = p(c).getString("key", "") ?: ""
    fun save(c: Context, url: String, key: String) = p(c).edit().putString("url", url.trimEnd('/')).putString("key", key).apply()
    fun isLoggedIn(c: Context) = getUrl(c).isNotBlank() && getKey(c).isNotBlank()
    fun logout(c: Context) = p(c).edit().clear().apply()
}

// ═══════════════════════
// API
// ═══════════════════════
object Api {
    suspend fun get(ctx: Context, path: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${AdminPrefs.getUrl(ctx)}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-Admin-Key", AdminPrefs.getKey(ctx))
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }
    suspend fun post(ctx: Context, path: String, data: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${AdminPrefs.getUrl(ctx)}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Admin-Key", AdminPrefs.getKey(ctx))
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.outputStream.use { it.write(data.toString().toByteArray()) }
            val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText()
            conn.disconnect()
            JSONObject(body)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getStats(ctx: Context) = get(ctx, "/api/admin/stats")
    suspend fun getDevices(ctx: Context): List<Device> {
        val r = get(ctx, "/api/admin/devices") ?: return emptyList()
        val arr = r.optJSONArray("devices") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            Device(
                d.optString("id"),
                d.optString("model"),
                d.optString("version"),
                d.optString("country"),
                d.optString("tier"),
                d.optLong("lastSeen"),
                d.optInt("verifyCount"),
                d.optBoolean("signatureMismatch")
            )
        }
    }

    suspend fun getDevice(ctx: Context, id: String) = get(ctx, "/api/admin/device?id=$id")
    suspend fun kill(ctx: Context, id: String, reason: String) = post(ctx, "/api/admin/kill", JSONObject().put("deviceId", id).put("reason", reason))
    suspend fun unkill(ctx: Context, id: String) = post(ctx, "/api/admin/unkill", JSONObject().put("deviceId", id))
    suspend fun globalKill(ctx: Context, enabled: Boolean, msg: String) = post(ctx, "/api/admin/global-kill", JSONObject().put("enabled", enabled).put("message", msg))
    suspend fun setTier(ctx: Context, id: String, tier: String) = post(ctx, "/api/admin/set-tier", JSONObject().put("deviceId", id).put("tier", tier))
    suspend fun sendMessage(ctx: Context, msg: String?) = post(ctx, "/api/admin/message", JSONObject().put("message", msg ?: JSONObject.NULL))
}

data class Device(
    val id: String,
    val model: String,    val version: String,
    val country: String,
    val tier: String,
    val lastSeen: Long,
    val verifyCount: Int,
    val sigMismatch: Boolean
)

// ═══════════════════════
// App
// ═══════════════════════
@Composable
fun AdminApp() {
    val ctx = LocalContext.current
    var loggedIn by remember { mutableStateOf(AdminPrefs.isLoggedIn(ctx)) }
    if (!loggedIn) LoginScreen { loggedIn = true }
    else DashboardScreen { AdminPrefs.logout(ctx); loggedIn = false }
}

// ═══════════════════════
// Login
// ═══════════════════════
@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(AdminPrefs.getUrl(ctx).ifBlank { "https://api.glassfiles.ru" }) }
    var key by remember { mutableStateOf(AdminPrefs.getKey(ctx)) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(BG).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("GlassFiles", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = T1)
        Text("Admin Panel", fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(32.dp))

        Label("Server URL")
        Input(url, { url = it }, "https://api.glassfiles.ru")
        Spacer(Modifier.height(16.dp))

        Label("Admin Key")
        Input(key, { key = it }, "Enter admin key", password = true)
        Spacer(Modifier.height(24.dp))

        if (error.isNotBlank()) {
            Text(error, color = Red, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (key.length > 3 && !testing) Accent else Border)
                .clickable(enabled = key.length > 3 && !testing) {
                    testing = true
                    error = ""
                    scope.launch {
                        AdminPrefs.save(ctx, url, key)
                        val stats = Api.getStats(ctx)
                        if (stats != null) onLogin()
                        else {
                            error = "Connection failed"
                            testing = false
                        }
                    }
                }.padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
            else Text("Connect", color = if (key.length > 3) Color.Black else T3, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════
// Dashboard
// ═══════════════════════
@Composable
fun DashboardScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var showGlobalKill by remember { mutableStateOf(false) }
    var showMessage by remember { mutableStateOf(false) }
    var showKill by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            stats = Api.getStats(ctx)
            devices = Api.getDevices(ctx)
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    // Auto-refresh
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            stats = Api.getStats(ctx)
            devices = Api.getDevices(ctx)
        }
    }

    if (selectedDevice != null) {
        DeviceDetailScreen(selectedDevice!!, onBack = { selectedDevice = null; refresh() })
        return
    }

    Column(Modifier.fillMaxSize().background(BG)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(Card).padding(top = 48.dp, start = 16.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("GlassFiles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = T1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (stats != null) Accent else Red))
                    Text(if (stats != null) "Connected" else "Offline", fontSize = 12.sp, color = T2)
                }
            }
            IconButton(onClick = { refresh() }) { Icon(Icons.Rounded.Refresh, null, tint = T2) }
            IconButton(onClick = onLogout) { Icon(Icons.Rounded.Logout, null, tint = T2) }
        }

        if (loading && stats == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stats cards
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Devices", "${stats?.optInt("totalDevices") ?: 0}", Modifier.weight(1f))
                        StatCard("Verifies", "${stats?.optInt("totalVerifies") ?: 0}", Modifier.weight(1f))
                    }
                }                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val active = devices.count { System.currentTimeMillis() - it.lastSeen < 86400000 }
                        StatCard("Active 24h", "$active", Modifier.weight(1f))
                        val gk = stats?.optBoolean("globalKill") ?: false
                        StatCard("Kill-Switch", if (gk) "ON" else "OFF", Modifier.weight(1f), if (gk) Red else Accent)
                    }
                }

                // Actions
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Kill-Switch", Icons.Rounded.PowerSettingsNew, Modifier.weight(1f)) { showGlobalKill = true }
                        ActionChip("Message", Icons.Rounded.Mail, Modifier.weight(1f)) { showMessage = true }
                        ActionChip("Kill", Icons.Rounded.Block, Modifier.weight(1f), Red) { showKill = true }
                    }
                }

                // Devices header
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Devices", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = T1, modifier = Modifier.weight(1f))
                        Text(
                            "${devices.size}",
                            fontSize = 13.sp,
                            color = T2,
                            modifier = Modifier.background(Card2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Device list
                items(devices) { d ->
                    DeviceRow(d) { selectedDevice = d.id }
                }
            }
        }
    }

    // Dialogs
    if (showGlobalKill) {
        val gk = stats?.optBoolean("globalKill") ?: false
        var msg by remember { mutableStateOf("") }
        SimpleDialog("Global Kill-Switch", onDismiss = { showGlobalKill = false }) {
            Text(if (gk) "Kill-Switch is currently ACTIVE" else "Kill-Switch is OFF", color = if (gk) Red else Accent, fontSize = 14.sp)
            if (!gk) {
                Spacer(Modifier.height(12.dp))
                Input(msg, { msg = it }, "Reason message")
            }
            Spacer(Modifier.height(16.dp))            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Border).clickable { showGlobalKill = false }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Cancel", color = T2) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (gk) Accent else Red).clickable {
                        scope.launch {
                            Api.globalKill(ctx, !gk, msg.ifBlank { "App disabled" })
                            showGlobalKill = false
                            refresh()
                        }
                    }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { Text(if (gk) "Disable" else "Activate", color = Color.Black, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showMessage) {
        var msg by remember { mutableStateOf("") }
        SimpleDialog("Send Message", onDismiss = { showMessage = false }) {
            Input(msg, { msg = it }, "Message to all users")
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Border).clickable {
                        scope.launch {
                            Api.sendMessage(ctx, null)
                            showMessage = false
                            Toast.makeText(ctx, "Cleared", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Clear", color = T2) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Accent).clickable {
                        scope.launch {
                            Api.sendMessage(ctx, msg)
                            showMessage = false
                            Toast.makeText(ctx, "Sent", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Send", color = Color.Black, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (showKill) {        var devId by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        SimpleDialog("Kill Device", onDismiss = { showKill = false }) {
            Input(devId, { devId = it }, "Device ID")
            Spacer(Modifier.height(8.dp))
            Input(reason, { reason = it }, "Reason")
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Red).clickable {
                    scope.launch {
                        Api.kill(ctx, devId, reason.ifBlank { "Blocked" })
                        showKill = false
                        refresh()
                    }
                }.padding(12.dp),
                contentAlignment = Alignment.Center
            ) { Text("Kill", color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ═══════════════════════
// Device Detail
// ═══════════════════════
@Composable
fun DeviceDetailScreen(deviceId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(deviceId) {
        detail = Api.getDevice(ctx, deviceId)
        loading = false
    }

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(
            Modifier.fillMaxWidth().background(Card).padding(top = 48.dp, start = 4.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = T1) }
            Text("Device", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = T1)
        }

        if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
        else if (detail == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Not found", color = T2) }
        else {
            val d = detail!!
            val killed = d.opt("killed")            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { InfoRow("Device ID", d.optString("id")) }
                item { InfoRow("Model", d.optString("model")) }
                item { InfoRow("Android", "API ${d.optInt("android")}") }
                item { InfoRow("Version", d.optString("version")) }
                item { InfoRow("Tier", d.optString("tier")) }
                item { InfoRow("IP", d.optString("ip")) }
                item { InfoRow("Country", d.optString("country")) }
                item { InfoRow("First Seen", formatDate(d.optLong("firstSeen"))) }
                item { InfoRow("Last Seen", formatDate(d.optLong("lastSeen"))) }
                item { InfoRow("Verifies", "${d.optInt("verifyCount")}") }
                item { InfoRow("Signature", d.optString("signatureHash").take(32) + "...") }

                if (killed != null && killed != false && killed.toString() != "false") {
                    item {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Red.copy(0.1f)).padding(12.dp)) {
                            Text("BLOCKED: $killed", color = Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Actions
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    // Tier selector
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("free", "pro", "beta").forEach { tier ->
                            val sel = d.optString("tier") == tier
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Accent.copy(0.15f) else Card2)
                                    .border(1.dp, if (sel) Accent else Border, RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            Api.setTier(ctx, deviceId, tier)
                                            detail = Api.getDevice(ctx, deviceId)
                                        }
                                    }.padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tier.uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) Accent else T2
                                )                            }
                        }
                    }
                }
                item {
                    val isKilled = killed != null && killed != false && killed.toString() != "false"
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (isKilled) Accent else Red)
                            .clickable {
                                scope.launch {
                                    if (isKilled) Api.unkill(ctx, deviceId) else Api.kill(ctx, deviceId, "Blocked by admin")
                                    detail = Api.getDevice(ctx, deviceId)
                                }
                            }.padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isKilled) "Unblock" else "Kill Device",
                            color = if (isKilled) Color.Black else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════
// Components
// ═══════════════════════
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, valueColor: Color = T1) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, Border, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Text(label, fontSize = 12.sp, color = T2)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, modifier: Modifier, color: Color = T1, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(10.dp)).background(Card2).border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DeviceRow(d: Device, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
            .border(1.dp, Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Card2), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(20.dp), tint = Bl)
        }
        Column(Modifier.weight(1f)) {
            Text(
                d.model.ifBlank { d.id.take(20) },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = T1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("v${d.version}", fontSize = 11.sp, color = T3, fontFamily = FontFamily.Monospace)
                Text(d.country, fontSize = 11.sp, color = T3)
                Text(timeAgo(d.lastSeen), fontSize = 11.sp, color = T3)
            }
        }
        TierBadge(d.tier)
        if (d.sigMismatch) Box(Modifier.background(Orange.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
            Text("SIG", fontSize = 9.sp, color = Orange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TierBadge(tier: String) {
    val (bg, fg) = when (tier) {
        "pro" -> Purple.copy(0.12f) to Purple
        "beta" -> Bl.copy(0.12f) to Bl
        else -> T3.copy(0.15f) to T2
    }
    Box(Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(tier, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, fontFamily = FontFamily.Monospace)
    }
}
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = T2)
        Text(
            value,
            fontSize = 13.sp,
            color = T1,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 200.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, color = T2, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Input(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false) {
    BasicTextField(
        value,
        onChange,
        Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).border(1.dp, Border, RoundedCornerShape(10.dp)).padding(14.dp),
        textStyle = TextStyle(T1, 14.sp, fontFamily = if (password) FontFamily.Monospace else FontFamily.Default),
        cursorBrush = SolidColor(Accent),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isEmpty()) Text(hint, color = T3, fontSize = 14.sp)
            inner()
        }
    )
}

@Composable
private fun SimpleDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        title = { Text(title, fontWeight = FontWeight.Bold, color = T1) },
        text = { Column { content() } },
        confirmButton = {}
    )}

private fun timeAgo(ts: Long): String {
    if (ts == 0L) return "-"
    val d = (System.currentTimeMillis() - ts) / 1000
    return when {
        d < 60 -> "now"
        d < 3600 -> "${d / 60}m"
        d < 86400 -> "${d / 3600}h"
        else -> "${d / 86400}d"
    }
}

private fun formatDate(ts: Long): String {
    if (ts == 0L) return "-"
    return SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(ts))
}