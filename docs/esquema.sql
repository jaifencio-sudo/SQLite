-- Esquema v8 (v7 + tabla SUCURSAL) extraído de la app ejecutada en Android. Solo estructura, sin datos.
PRAGMA foreign_keys=ON;
BEGIN;
CREATE TABLE CATEGORIA (id_categoria INTEGER PRIMARY KEY AUTOINCREMENT, nombre_categoria TEXT NOT NULL UNIQUE COLLATE NOCASE, descripcion TEXT, activo INTEGER NOT NULL DEFAULT 1);

CREATE TABLE MOVIMIENTO (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT, id_producto INTEGER NOT NULL REFERENCES PRODUCTO(id_producto), id_usuario INTEGER NOT NULL REFERENCES USUARIO(id_usuario), id_proveedor INTEGER REFERENCES PROVEEDOR(id_proveedor), tipo TEXT NOT NULL CHECK(tipo IN('ENTRADA','SALIDA','INICIAL')), cantidad INTEGER NOT NULL CHECK(typeof(cantidad)='integer' AND cantidad>0), fecha TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), motivo TEXT NOT NULL, CHECK(tipo='ENTRADA' OR id_proveedor IS NULL));

CREATE TABLE PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT, sku_codigo TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK(length(trim(sku_codigo))>0), nombre TEXT NOT NULL CHECK(length(trim(nombre))>0), descripcion TEXT NOT NULL DEFAULT '', id_categoria INTEGER NOT NULL REFERENCES CATEGORIA(id_categoria), stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(typeof(stock_actual)='integer' AND stock_actual>=0), stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(typeof(stock_minimo)='integer' AND stock_minimo>=0), precio_kg INTEGER NOT NULL DEFAULT 0 CHECK(precio_kg>=0), activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN(0,1)), codigo_barras TEXT);

CREATE TABLE PROVEEDOR (id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT, rut_empresa TEXT NOT NULL UNIQUE, razon_social TEXT NOT NULL, numero_contacto TEXT NOT NULL, direccion TEXT);

CREATE TABLE ROL (id_rol INTEGER PRIMARY KEY AUTOINCREMENT, nombre_rol TEXT NOT NULL UNIQUE, descripcion TEXT);

CREATE TABLE USUARIO (id_usuario INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, nombre_completo TEXT NOT NULL, id_rol INTEGER NOT NULL, activo INTEGER NOT NULL DEFAULT 1, FOREIGN KEY (id_rol) REFERENCES ROL(id_rol));

CREATE TABLE EMPRESA (id_empresa INTEGER PRIMARY KEY CHECK(id_empresa=1), nombre TEXT NOT NULL CHECK(length(trim(nombre))>0), latitud REAL NOT NULL CHECK(latitud BETWEEN -90 AND 90), longitud REAL NOT NULL CHECK(longitud BETWEEN -180 AND 180));

CREATE TABLE SUCURSAL (id_sucursal INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK(length(trim(nombre))>0), direccion TEXT NOT NULL DEFAULT '', latitud REAL NOT NULL CHECK(latitud BETWEEN -90 AND 90), longitud REAL NOT NULL CHECK(longitud BETWEEN -180 AND 180));

CREATE TABLE persona (id INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, apellido TEXT NOT NULL, edad INTEGER NOT NULL);

CREATE INDEX idx_movimiento_producto ON MOVIMIENTO(id_producto,id_movimiento);

CREATE INDEX idx_producto_categoria ON PRODUCTO(id_categoria);

CREATE UNIQUE INDEX idx_producto_barras ON PRODUCTO(codigo_barras);
PRAGMA user_version=8;
COMMIT;
