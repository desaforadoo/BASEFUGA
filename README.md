# Registro – app Android para registrar ventas en Google Sheets

Formulario rápido (VENTA / COMPRA / GASTO): producto, cantidad, precio total, pago
(efectivo / transferencia / por pagar), cliente, observaciones y fecha-hora automática.
Guarda en el teléfono (funciona sin internet) y envía a tu Google Sheet; lo pendiente se
reintenta solo al abrir la app o al tocar "⏳ N pendientes".

Para ir rápido: los productos y clientes más usados aparecen como botones, y al elegir un
producto ya usado el precio se calcula solo (último precio unitario × cantidad).

## 1. Preparar Google Sheets (5 min)
1. Crea una hoja de cálculo en Google Sheets.
2. `Extensiones > Apps Script`, borra el contenido y pega `apps-script/Code.gs`.
3. `Configuración del proyecto (⚙) > Propiedades del script > Agregar`: `TOKEN` = una clave a elección.
4. `Implementar > Nueva implementación > Aplicación web`: ejecutar como **Yo**, acceso **Cualquier persona**. Autoriza permisos y copia la URL (termina en `/exec`).

## 2. Obtener el APK
- GitHub: pestaña **Actions > Build APK > Run workflow**, descarga el artefacto `registro-debug-apk`, o
- Android Studio: abre esta carpeta y `Run`.

Instala el APK en el teléfono (permite "instalar apps desconocidas").

## 3. Configurar la app
Al abrir por primera vez pega la URL y la clave, y toca **Guardar y probar**. Los registros llegan a la pestaña `Registros` (se crea sola).
