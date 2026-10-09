/**
 * Recibe los registros de la app y los agrega como filas a la hoja "Registros".
 * Configura la clave en: Configuración del proyecto > Propiedades del script > TOKEN
 */
const SHEET_NAME = 'Registros';
const HEADERS = ['ID', 'Fecha y hora', 'Tipo', 'Producto', 'Cantidad', 'Precio total', 'Cliente', 'Pago', 'Observaciones'];

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    const d = JSON.parse(e.postData.contents);
    const token = PropertiesService.getScriptProperties().getProperty('TOKEN');
    if (token && d.token !== token) return out({ ok: false, error: 'Clave (token) incorrecta' });
    if (d.ping) return out({ ok: true });

    lock.waitLock(20000);
    const sh = getSheet_();
    // Evita duplicados si la app reintenta un envío ya recibido.
    if (sh.getLastRow() > 1 &&
        sh.getRange(2, 1, sh.getLastRow() - 1, 1).createTextFinder(d.id).matchEntireCell(true).findNext()) {
      return out({ ok: true, dup: true });
    }
    sh.appendRow([
      d.id, new Date(d.fecha.replace(' ', 'T')), d.tipo, d.producto,
      d.cantidad, d.precio, d.cliente, d.pago, d.obs,
    ]);
    const r = sh.getLastRow();
    sh.getRange(r, 2).setNumberFormat('dd-mm-yyyy hh:mm');
    sh.getRange(r, 6).setNumberFormat('$#,##0');
    return out({ ok: true });
  } catch (err) {
    return out({ ok: false, error: String(err) });
  } finally {
    try { lock.releaseLock(); } catch (_) {}
  }
}

function doGet() { return out({ ok: true, info: 'Registro activo' }); }

function getSheet_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) {
    sh = ss.insertSheet(SHEET_NAME);
    sh.appendRow(HEADERS);
    sh.setFrozenRows(1);
    sh.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
  }
  return sh;
}

function out(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
