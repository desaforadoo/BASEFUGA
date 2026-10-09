package cl.basefuga.registro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val TIPOS = listOf("VENTA", "COMPRA", "GASTO")
private val PAGOS = listOf("Efectivo", "Transferencia", "Por pagar")

class MainActivity : ComponentActivity() {
    private lateinit var repo: Repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Repo(applicationContext)
        lifecycleScope.launch { repo.sync() } // reintenta pendientes al abrir
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var settings by remember { mutableStateOf(!repo.configured) }
                if (settings) SettingsScreen(repo) { settings = false }
                else FormScreen(repo) { settings = true }
            }
        }
    }
}

private fun money(v: Long) = "$" + NumberFormat.getIntegerInstance(Locale("es", "CL")).format(v)

/** Muestra 10000 como 10.000 (el cursor queda siempre al final). */
private object ThousandsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = digits.toLongOrNull()?.let { money(it).drop(1) } ?: digits
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = shown.length
                override fun transformedToOriginal(offset: Int) = digits.length
            },
        )
    }
}

@Composable
private fun FormScreen(repo: Repo, openSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val productFocus = remember { FocusRequester() }

    var tipo by remember { mutableStateOf(repo.lastTipo) }
    var producto by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("1") }
    var precio by remember { mutableStateOf("") }
    var precioManual by remember { mutableStateOf(false) }
    var cliente by remember { mutableStateOf("") }
    var pago by remember { mutableStateOf(repo.lastPago) }
    var obs by remember { mutableStateOf("") }

    // Sugerencias a partir del historial: más usados primero.
    val topProducts = repo.records.map { it.producto }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }
    val topClients = repo.records.map { it.cliente }.filter { it.isNotBlank() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun unitPrice(p: String): Long? = repo.records.firstOrNull { it.producto.equals(p, true) && it.cantidad > 0 }
        ?.let { it.precio / it.cantidad }

    fun autoPrice() {
        if (precioManual) return
        val unit = unitPrice(producto.trim()) ?: return
        precio = (unit * (cantidad.toIntOrNull() ?: 1)).toString()
    }

    fun setQty(n: Int) {
        cantidad = n.coerceAtLeast(1).toString()
        autoPrice()
    }

    val qty = cantidad.toIntOrNull() ?: 0
    val total = precio.toLongOrNull() ?: 0
    val canSave = producto.isNotBlank() && qty > 0 && total >= 0 && precio.isNotEmpty() &&
        (pago != "Por pagar" || cliente.isNotBlank())

    fun save() {
        repo.add(
            Record(
                id = UUID.randomUUID().toString(),
                tipo = tipo,
                fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
                producto = producto.trim(), cantidad = qty, precio = total,
                cliente = cliente.trim(), pago = pago, obs = obs.trim(),
            ),
        )
        producto = ""; cantidad = "1"; precio = ""; precioManual = false; cliente = ""; obs = ""
        scope.launch {
            snack.showSnackbar("Guardado ✓", duration = androidx.compose.material3.SnackbarDuration.Short)
        }
        scope.launch {
            val err = repo.sync()
            if (err != null) snack.showSnackbar("Pendiente de envío: $err")
        }
        productFocus.requestFocus()
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).statusBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Nuevo registro", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (repo.pending > 0) {
                    TextButton(onClick = {
                        scope.launch {
                            snack.showSnackbar(repo.sync()?.let { "Error: $it" } ?: "Todo enviado ✓")
                        }
                    }) { Text("⏳ ${repo.pending} pendientes") }
                }
                TextButton(onClick = openSettings) { Text("⚙") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TIPOS.forEach { t ->
                    FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t) })
                }
            }

            OutlinedTextField(
                value = producto,
                onValueChange = { producto = it; autoPrice() },
                label = { Text("Producto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(productFocus),
            )
            val prodSuggestions = topProducts.filter {
                producto.isBlank() || (it.contains(producto, true) && !it.equals(producto.trim(), true))
            }.take(6)
            ChipRow(prodSuggestions) { producto = it; autoPrice() }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { setQty(qty - 1) }) { Text("−", fontSize = 20.sp) }
                OutlinedTextField(
                    value = cantidad,
                    onValueChange = { v -> cantidad = v.filter(Char::isDigit).take(5); autoPrice() },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { setQty(qty + 1) }) { Text("+", fontSize = 20.sp) }
            }

            OutlinedTextField(
                value = precio,
                onValueChange = { v -> precio = v.filter(Char::isDigit).take(12); precioManual = true },
                label = { Text("Precio total") },
                prefix = { Text("$") },
                singleLine = true,
                visualTransformation = ThousandsTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Pago", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PAGOS.forEach { p ->
                    FilterChip(selected = pago == p, onClick = { pago = p }, label = { Text(p) })
                }
            }

            OutlinedTextField(
                value = cliente,
                onValueChange = { cliente = it },
                label = { Text(if (pago == "Por pagar") "Cliente (obligatorio)" else "Cliente") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            ChipRow(topClients.filter { cliente.isBlank() || (it.contains(cliente, true) && !it.equals(cliente.trim(), true)) }.take(6)) {
                cliente = it
            }

            OutlinedTextField(
                value = obs,
                onValueChange = { obs = it },
                label = { Text("Observaciones") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("GUARDAR  ${if (precio.isNotEmpty()) money(total) else ""}", fontSize = 18.sp) }

            if (repo.records.isNotEmpty()) {
                Text("Últimos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                repo.records.take(5).forEach {
                    Text(
                        "${if (it.synced) "✓" else "⏳"} ${it.tipo.take(1)} · ${it.cantidad} ${it.producto} · ${money(it.precio)} · ${it.pago}" +
                            (if (it.cliente.isNotBlank()) " · ${it.cliente}" else ""),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(items: List<String>, onPick: (String) -> Unit) {
    if (items.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { FilterChip(selected = false, onClick = { onPick(it) }, label = { Text(it, maxLines = 1) }) }
    }
}

@Composable
private fun SettingsScreen(repo: Repo, done: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(repo.url) }
    var token by remember { mutableStateOf(repo.token) }
    var msg by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Conexión con Google Sheets", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pega la URL de la aplicación web de Apps Script (ver README) y la clave que definiste.")
        OutlinedTextField(url, { url = it }, label = { Text("URL (termina en /exec)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("Clave (token)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                repo.saveSettings(url, token)
                msg = "Probando…"
                scope.launch { msg = repo.ping()?.let { "❌ $it" } ?: "✓ Conexión OK" }
            },
            enabled = url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar y probar") }
        if (msg.isNotEmpty()) Text(msg)
        TextButton(onClick = { repo.saveSettings(url, token); done() }, enabled = url.isNotBlank()) { Text("Volver al formulario") }
    }
}
