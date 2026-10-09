package cl.basefuga.registro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Record(
    val id: String,
    val tipo: String,
    val fecha: String,
    val producto: String,
    val cantidad: Int,
    val precio: Long,
    val cliente: String,
    val pago: String,
    val obs: String,
    val synced: Boolean = false,
)

/** Guarda registros y ajustes en el teléfono y los envía a Google Sheets (vía Apps Script). */
class Repo(context: Context) {
    private val prefs = context.getSharedPreferences("registro", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    val records = mutableStateListOf<Record>()
    var url by mutableStateOf(prefs.getString("url", "") ?: "")
        private set
    var token by mutableStateOf(prefs.getString("token", "") ?: "")
        private set
    var lastPago by mutableStateOf(prefs.getString("pago", "Efectivo") ?: "Efectivo")
        private set
    var lastTipo by mutableStateOf(prefs.getString("tipo", "VENTA") ?: "VENTA")
        private set

    init {
        val arr = JSONArray(prefs.getString("records", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            records += Record(
                o.getString("id"), o.getString("tipo"), o.getString("fecha"),
                o.getString("producto"), o.getInt("cantidad"), o.getLong("precio"),
                o.getString("cliente"), o.getString("pago"), o.getString("obs"),
                o.getBoolean("synced"),
            )
        }
    }

    val pending get() = records.count { !it.synced }
    val configured get() = url.isNotBlank()

    fun saveSettings(url: String, token: String) {
        this.url = url.trim()
        this.token = token.trim()
        prefs.edit().putString("url", this.url).putString("token", this.token).apply()
    }

    fun add(r: Record) {
        records.add(0, r)
        lastPago = r.pago
        lastTipo = r.tipo
        // Conserva los últimos 300 registros (los pendientes nunca se descartan).
        while (records.size > 300) {
            val idx = records.indexOfLast { it.synced }
            if (idx < 0) break
            records.removeAt(idx)
        }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        records.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("tipo", it.tipo); put("fecha", it.fecha)
                put("producto", it.producto); put("cantidad", it.cantidad); put("precio", it.precio)
                put("cliente", it.cliente); put("pago", it.pago); put("obs", it.obs)
                put("synced", it.synced)
            })
        }
        prefs.edit().putString("records", arr.toString())
            .putString("pago", lastPago).putString("tipo", lastTipo).apply()
    }

    /** Envía los pendientes (el más antiguo primero). Devuelve null si todo OK, o el mensaje de error. */
    suspend fun sync(): String? = mutex.withLock {
        if (!configured) return "Falta configurar la URL"
        for (r in records.filter { !it.synced }.reversed()) {
            val err = withContext(Dispatchers.IO) { post(toJson(r)) }
            if (err != null) return err
            val i = records.indexOfFirst { it.id == r.id }
            if (i >= 0) records[i] = records[i].copy(synced = true)
            persist()
        }
        null
    }

    suspend fun ping(): String? = withContext(Dispatchers.IO) {
        post(JSONObject().put("token", token).put("ping", true))
    }

    private fun toJson(r: Record) = JSONObject().apply {
        put("token", token); put("id", r.id); put("tipo", r.tipo); put("fecha", r.fecha)
        put("producto", r.producto); put("cantidad", r.cantidad); put("precio", r.precio)
        put("cliente", r.cliente); put("pago", r.pago); put("obs", r.obs)
    }

    private fun post(body: JSONObject): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        // Apps Script responde con un 302 que HttpURLConnection sigue solo.
        val text = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrNull()
        when {
            json?.optBoolean("ok") == true -> null
            json != null -> json.optString("error", "Error del servidor")
            else -> "Respuesta inválida (¿URL correcta y publicada para 'Cualquier persona'?)"
        }
    } catch (e: Exception) {
        "Sin conexión: ${e.message ?: e.javaClass.simpleName}"
    }
}
