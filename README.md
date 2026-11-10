# Commit Leander

Cambios que hice:

- Las tarjetas de "Productos" y "Stock bajo" ahora son botones y abren el inventario filtrado por stock normal y stock bajo.
- Separé "Entradas y salidas" en dos botones: "Entradas" y "Salidas", cada uno con su historial.
- Agregué los informes PDF (entradas, salidas, stock normal, stock bajo e inventario completo). Se guardan en Descargas/InformesAlmacen.
- Archivos nuevos: InformePdf.java, HistorialMovimientosActivity.java y Movimiento.java.
- También toqué MenuActivity, InventarioActivity, DBHelper, los layouts y el manifest.

# commit Esteban

Cambios que hice:

- Implementé el registro de Ventas tratándolas como "Salidas" de stock en el sistema.
- Agregué transacciones seguras en la base de datos (SQLite). Ahora, al registrar una venta, el descuento del stock y el historial se guardan juntos; si algo falla, no se guarda nada para evitar errores en el inventario.
- Puse un bloqueo de seguridad directo en SQL para evitar el stock negativo. Si se intenta vender más cantidad de la que hay en bodega, la venta se bloquea y lanza el error de "Stock insuficiente".
- Ajusté el formulario para que, al elegir registrar una salida o venta, se bloquee el campo de "Proveedor" (porque no aplica) y se pida un "Motivo" (ej. "Venta").
- Archivos que toqué: DBHelper.java (para toda la lógica y seguridad de la base de datos) y MovimientosActivity.java (para el formulario e interfaz).

# Commit Catalina

Cambios que hice:

- Agregué el escáner de código de barras (EscanerActivity) con la cámara de Google Code Scanner. Busca el producto en la base de datos, muestra su stock y estado, y permite asociar el código a un producto o crear uno nuevo con ese código.
- Agregué el campo "Código de barras" al formulario de productos, con botón para escanearlo. Un código repetido se rechaza con un aviso.
- Agregué la ubicación de la empresa en el mapa (UbicacionActivity): ver la empresa en Google Maps, mi ubicación con la distancia en km y cómo llegar. El ADMIN puede editar las coordenadas.
- Hice la pantalla "Códigos de barras" (CodigosBarrasActivity): crear, leer, actualizar y eliminar el código de cada producto, con búsqueda y filtros.
- Hice la pantalla "Sucursales" (SucursalesActivity): crear, editar y eliminar sucursales con sus coordenadas (solo ADMIN). Todos pueden verlas en el mapa y pedir la ruta.
- Subí la base de datos a la versión 8 con la tabla SUCURSAL, y agregué datos de prueba y tests.
- Archivos nuevos: CodigosBarrasActivity.java, SucursalesActivity.java, Sucursal.java y los layouts de ambas pantallas.
- También toqué MenuActivity, DBHelper, el manifest, docs/datos_demo.sql y docs/esquema.sql.
- Pendiente: cambiar las coordenadas de ejemplo de la empresa (están en Santiago centro) y probar en un teléfono o emulador con Google Play Services.

# Commit Jaime

Cambios que hice:

- Me encargué de la gestión de productos: **agregar producto**, **modificar producto** y **consultar productos**.
- **Agregar producto:** formulario de producto nuevo (ProductoActivity) con SKU, nombre, descripción, categoría, stock inicial, stock mínimo, precio por kg y código de barras opcional (se puede escanear). Valida los campos obligatorios y que los números no sean negativos. La categoría se elige de un desplegable con las categorías activas de la base de datos. Se guarda con `insertarProducto` en DBHelper, y el SKU y el código de barras repetidos se rechazan con un aviso.
- **Modificar producto:** al tocar un producto de la lista se abre el mismo formulario en modo edición, con los datos ya cargados. Se guarda con `actualizarProducto`. El stock queda bloqueado en esta pantalla porque solo cambia mediante movimientos (entradas y salidas), así el inventario siempre queda consistente.
- **Consultar productos:** el listado del inventario (InventarioActivity) con búsqueda por nombre o SKU (`listarProductos`) y la consulta de un producto por su id (`obtenerProducto`) para cargar sus datos. Muestra el stock en kg y avisa cuando está en "STOCK BAJO".
- Los errores se controlan con try/catch y mensajes en pantalla (datos incompletos, números inválidos, SKU o código de barras duplicado, producto que ya no existe).
- Archivos que toqué: ProductoActivity.java, InventarioActivity.java, Producto.java, DBHelper.java y los layouts activity_producto.xml y activity_inventario.xml.
