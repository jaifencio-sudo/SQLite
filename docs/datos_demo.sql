-- Datos de demostración (RF-20). Ejecutar en Android Studio:
-- View > Tool Windows > App Inspection > Database Inspector > BD_EJEMPLO.db > pestaña de consultas (con la app corriendo).
-- Stock y mínimo se guardan en GRAMOS (8000 = 8 kg). Se puede ejecutar más de una vez sin duplicar nada.

-- A) Códigos de barras para los 9 productos que la app crea sola al instalarse
UPDATE PRODUCTO SET codigo_barras='7801234000018' WHERE nombre='Almendras';
UPDATE PRODUCTO SET codigo_barras='7801234000025' WHERE nombre='Nueces mariposa';
UPDATE PRODUCTO SET codigo_barras='7801234000032' WHERE nombre='Maní salado';
UPDATE PRODUCTO SET codigo_barras='7801234000049' WHERE nombre='Pistachos';
UPDATE PRODUCTO SET codigo_barras='7801234000056' WHERE nombre='Castañas de cajú';
UPDATE PRODUCTO SET codigo_barras='7801234000063' WHERE nombre='Avellanas';
UPDATE PRODUCTO SET codigo_barras='7801234000070' WHERE nombre='Pasas rubias';
UPDATE PRODUCTO SET codigo_barras='7801234000087' WHERE nombre='Semillas de zapallo';
UPDATE PRODUCTO SET codigo_barras='7801234000094' WHERE nombre='Mix tropical';

-- B) Tres productos nuevos ya con su código (se enlazan a la categoría por nombre)
INSERT OR IGNORE INTO PRODUCTO(sku_codigo,nombre,descripcion,id_categoria,stock_actual,stock_minimo,precio_kg,codigo_barras)
SELECT 'DEM-001','Chía','Producto de demostración',id_categoria,8000,2000,6500,'7801234000100'
FROM CATEGORIA WHERE nombre_categoria='Semillas';
INSERT OR IGNORE INTO PRODUCTO(sku_codigo,nombre,descripcion,id_categoria,stock_actual,stock_minimo,precio_kg,codigo_barras)
SELECT 'DEM-002','Arándanos deshidratados','Producto de demostración',id_categoria,5000,1500,12900,'7801234000117'
FROM CATEGORIA WHERE nombre_categoria='Frutas deshidratadas';
INSERT OR IGNORE INTO PRODUCTO(sku_codigo,nombre,descripcion,id_categoria,stock_actual,stock_minimo,precio_kg,codigo_barras)
SELECT 'DEM-003','Mix energético','Producto de demostración',id_categoria,10000,3000,10500,'7801234000124'
FROM CATEGORIA WHERE nombre_categoria='Mezclas';

-- B2) Sucursales de demostración para el CRUD del mapa (la "Casa matriz" la crea la app sola)
INSERT OR IGNORE INTO SUCURSAL(nombre,direccion,latitud,longitud) VALUES ('Sucursal La Serena','Centro de La Serena',-29.9027,-71.2519);
INSERT OR IGNORE INTO SUCURSAL(nombre,direccion,latitud,longitud) VALUES ('Sucursal Ovalle','Centro de Ovalle',-30.6015,-71.2003);

-- C) Comprobar
SELECT sku_codigo, nombre, stock_actual/1000.0 AS stock_kg, codigo_barras FROM PRODUCTO ORDER BY id_producto;
SELECT id_sucursal, nombre, latitud, longitud FROM SUCURSAL ORDER BY id_sucursal;

-- D) (Opcional) quitar el código de un producto para repetir la prueba 'no registrado'
-- UPDATE PRODUCTO SET codigo_barras=NULL WHERE codigo_barras='7801234000100';
