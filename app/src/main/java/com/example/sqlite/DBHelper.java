package com.example.sqlite;

// Importa las clases necesarias para gestionar SQLite
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;

public class DBHelper extends SQLiteOpenHelper {

    // Se conserva el archivo compartido; v6 normaliza el inventario, v7 agrega código de barras y ubicación de la empresa y v8 agrega las sucursales.
    private static final String DB_NAME = "BD_EJEMPLO.db";
    private static final int DB_VERSION = 8;

    public static final String[] CATEGORIAS = {
            "Frutos secos", "Semillas", "Frutas deshidratadas", "Mezclas", "Otros"
    };

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    DBHelper(Context context, String testDatabase) {
        super(context, testDatabase, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // Activa las llaves foráneas (USUARIO -> ROL)
    }

    //  Se ejecuta UNA SOLA VEZ al crear la base de datos por primera vez
    @Override
    public void onCreate(SQLiteDatabase db) {
        crearTablaPersona(db);
        crearTablasBodega(db);
        sembrarDatos(db);
        crearTablaCategoria(db);
        sembrarCategorias(db);
        crearTablaProveedor(db);
        insertarProveedoresDeEjemplo(db);
        migrarInventario(db);
        migrarV7(db);   // RF-20 (código de barras) y RF-24 (ubicación de la empresa)
        migrarV8(db);   // sucursales con coordenadas (CRUD del mapa)
    }

    //  Al subir de versión NO se borra nada: la tabla "persona" y sus datos quedan intactos
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            crearTablasBodega(db);
            sembrarDatos(db);
        }
        // v3: módulo Categorías (RF-09 / RF-10).
        if (oldVersion < 3) {
            crearTablaCategoria(db);
            sembrarCategorias(db);
        }
        // v4: módulo Proveedores (soporte a compras / entradas).
        if (oldVersion < 4) {   // usa el número de versión que corresponda
            // No borrar proveedores existentes durante una actualización.
            crearTablaProveedor(db);
            insertarProveedoresDeEjemplo(db);
        }
        if (oldVersion < 5) repararTablaProveedor(db);
        if (oldVersion < 6) migrarInventario(db);
        if (oldVersion < 7) migrarV7(db);
        if (oldVersion < 8) migrarV8(db);
    }


    private void repararTablaProveedor(SQLiteDatabase db) {
        Set<String> cols = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(PROVEEDOR)", null)) {
            while (c.moveToNext()) cols.add(c.getString(1).toLowerCase(Locale.ROOT));
        }
        if (cols.isEmpty()) {            // la tabla no existe
            crearTablaProveedor(db);
            insertarProveedoresDeEjemplo(db);
            return;
        }
        if (cols.contains("id_proveedor") && cols.contains("rut_empresa")
                && cols.contains("razon_social") && cols.contains("numero_contacto")
                && cols.contains("direccion")) {
            return;                      // esquema correcto, nada que hacer
        }

        // Esquema distinto: se crea la tabla nueva, se copia lo que coincida y se reemplaza.
        String rut = cols.contains("rut_empresa") ? "rut_empresa" : cols.contains("rut") ? "rut" : null;
        String razon = cols.contains("razon_social") ? "razon_social"
                : cols.contains("nombre") ? "nombre" : null;
        String tel = cols.contains("numero_contacto") ? "numero_contacto"
                : cols.contains("telefono") ? "telefono"
                  : cols.contains("contacto") ? "contacto" : null;
        String dir = cols.contains("direccion") ? "direccion" : null;

        if (rut == null || razon == null) {
            throw new IllegalStateException("Esquema de proveedor desconocido; se cancela la migración para conservar los datos.");
        }
        db.execSQL("ALTER TABLE PROVEEDOR RENAME TO PROVEEDOR_OLD");
        crearTablaProveedor(db);
        String id = cols.contains("id_proveedor") ? "id_proveedor" : cols.contains("id") ? "id" : null;
        // Sin IGNORE: cualquier fila inválida revierte la migración, en vez de perderse.
        db.execSQL("INSERT INTO PROVEEDOR (" + (id == null ? "" : "id_proveedor,") +
                "rut_empresa, razon_social, numero_contacto, direccion) " +
                "SELECT " + (id == null ? "" : id + ",") + rut + ", " + razon + ", " +
                (tel != null ? "COALESCE(" + tel + ", '')" : "''") + ", " +
                (dir != null ? dir : "NULL") + " FROM PROVEEDOR_OLD");
        db.execSQL("DROP TABLE PROVEEDOR_OLD");
    }

    // ---------------------------------------------------------------- Esquema

    // Tabla original del proyecto (se conserva tal cual)
    private void crearTablaPersona(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS persona (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT NOT NULL, " +
                "apellido TEXT NOT NULL, " +
                "edad INTEGER NOT NULL)");
    }

    private void crearTablasBodega(SQLiteDatabase db) {
        // RF-27: nombre_rol único (UK)
        db.execSQL("CREATE TABLE IF NOT EXISTS ROL (" +
                "id_rol INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre_rol TEXT NOT NULL UNIQUE, " +
                "descripcion TEXT)");

        // RF-01 / RF-27: username único (UK), password_hash, activo (1 = activo)
        db.execSQL("CREATE TABLE IF NOT EXISTS USUARIO (" +
                "id_usuario INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL UNIQUE, " +
                "password_hash TEXT NOT NULL, " +
                "nombre_completo TEXT NOT NULL, " +
                "id_rol INTEGER NOT NULL, " +
                "activo INTEGER NOT NULL DEFAULT 1, " +
                "FOREIGN KEY (id_rol) REFERENCES ROL(id_rol))");

        // Inventario de la bodega (stock en kg, precio CLP por kg)
        db.execSQL("CREATE TABLE IF NOT EXISTS PRODUCTO (" +
                "id_producto INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT NOT NULL UNIQUE, " +
                "categoria TEXT NOT NULL, " +
                "stock REAL NOT NULL DEFAULT 0, " +
                "stock_minimo REAL NOT NULL DEFAULT 0, " +
                "precio_kg INTEGER NOT NULL DEFAULT 0)");
    }

    // RF-09: nombre_categoria obligatorio y único (UK, sin distinguir mayúsculas), activo = 1 por defecto
    private void crearTablaCategoria(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS CATEGORIA (" +
                "id_categoria INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre_categoria TEXT NOT NULL UNIQUE COLLATE NOCASE, " +
                "descripcion TEXT, " +
                "activo INTEGER NOT NULL DEFAULT 1)");
    }

    // Proveedores: rut_empresa único (UK); razón social y número de contacto obligatorios
    private void crearTablaProveedor(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS PROVEEDOR (" +
                "id_proveedor INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "rut_empresa TEXT NOT NULL UNIQUE, " +
                "razon_social TEXT NOT NULL, " +
                "numero_contacto TEXT NOT NULL, " +
                "direccion TEXT)");
    }

    // Carga las categorías que ya usaba el inventario para que el catálogo no parta vacío
    private void sembrarCategorias(SQLiteDatabase db) {
        for (String nombre : CATEGORIAS) {
            db.execSQL("INSERT OR IGNORE INTO CATEGORIA (nombre_categoria, activo) VALUES (?, 1)",
                    new Object[]{nombre});
        }
    }

    // Datos iniciales: 2 roles, 1 usuario administrador y algunos productos de ejemplo
    private void sembrarDatos(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                new Object[]{"ADMIN", "Administra usuarios, roles e inventario"});
        db.execSQL("INSERT OR IGNORE INTO ROL (nombre_rol, descripcion) VALUES (?, ?)",
                new Object[]{"BODEGUERO", "Gestiona el inventario de la bodega"});

        // Usuario inicial: admin / admin123  (cámbiela después de entrar)
        db.execSQL("INSERT OR IGNORE INTO USUARIO (username, password_hash, nombre_completo, id_rol, activo) " +
                        "SELECT ?, ?, ?, id_rol, 1 FROM ROL WHERE nombre_rol = 'ADMIN'",
                new Object[]{"admin", hash("admin", "admin123"), "Administrador"});

        Object[][] productos = {
                {"Almendras", "Frutos secos", 25.0, 5.0, 14500},
                {"Nueces mariposa", "Frutos secos", 18.5, 5.0, 12900},
                {"Maní salado", "Frutos secos", 3.0, 8.0, 5200},
                {"Pistachos", "Frutos secos", 9.0, 3.0, 19800},
                {"Castañas de cajú", "Frutos secos", 12.0, 4.0, 17500},
                {"Avellanas", "Frutos secos", 7.5, 3.0, 15900},
                {"Pasas rubias", "Frutas deshidratadas", 15.0, 5.0, 4900},
                {"Semillas de zapallo", "Semillas", 6.0, 2.0, 8900},
                {"Mix tropical", "Mezclas", 10.0, 4.0, 9900},
        };
        for (Object[] p : productos) {
            db.execSQL("INSERT OR IGNORE INTO PRODUCTO (nombre, categoria, stock, stock_minimo, precio_kg) " +
                    "VALUES (?, ?, ?, ?, ?)", p);
        }
    }
    private void insertarProveedoresDeEjemplo(SQLiteDatabase db) {
        String[][] datos = {
                {"76123456-7", "Frutos Secos San Marcos", "+56 9 1234 5678", "Santiago Centro, Santiago"},
                {"77888999-K", "Lácteos del Sur Ltda.", "+56 2 2345 6789", "Maipú, Santiago"},
                {"96555444-3", "Abarrotes Don Pedro S.A.", "+56 9 8765 4321", "Providencia, Santiago"}
        };
        for (String[] d : datos) {
            ContentValues v = new ContentValues();
            v.put("rut_empresa", d[0]);
            v.put("razon_social", d[1]);
            v.put("numero_contacto", d[2]);
            v.put("direccion", d[3]);
            db.insertWithOnConflict("PROVEEDOR", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }


    // ---------------------------------------------------------------- Seguridad

    // Hash SHA-256 de (username en minúsculas + ":" + contraseña). Nunca se guarda la clave en texto plano.
    public static String hash(String username, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((username.trim().toLowerCase(Locale.ROOT) + ":" + password)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // RF-01: solo ingresan usuarios activos (activo = 1) con credenciales correctas.
    // Devuelve el usuario con su rol, o null si no coincide.
    public Usuario autenticar(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT u.id_usuario, u.username, u.nombre_completo, u.id_rol, r.nombre_rol, u.activo " +
                "FROM USUARIO u JOIN ROL r ON r.id_rol = u.id_rol " +
                "WHERE u.username = ? AND u.password_hash = ? AND u.activo = 1";
        try (Cursor c = db.rawQuery(sql, new String[]{username.trim(), hash(username, password)})) {
            return c.moveToFirst() ? leerUsuario(c) : null;
        }
    }

    // Para detectar si una sesión guardada pertenece a un usuario que fue desactivado o eliminado
    public boolean usuarioActivo(long idUsuario) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT 1 FROM USUARIO WHERE id_usuario = ? AND activo = 1",
                new String[]{String.valueOf(idUsuario)})) {
            return c.moveToFirst();
        }
    }

    private Usuario leerUsuario(Cursor c) {
        Usuario u = new Usuario();
        u.idUsuario = c.getLong(0);
        u.username = c.getString(1);
        u.nombreCompleto = c.getString(2);
        u.idRol = c.getLong(3);
        u.nombreRol = c.getString(4);
        u.activo = c.getInt(5) == 1;
        return u;
    }

    // ---------------------------------------------------------------- Roles (RF-27)

    public List<Rol> listarRoles() {
        List<Rol> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT id_rol, nombre_rol, descripcion FROM ROL ORDER BY nombre_rol", null)) {
            while (c.moveToNext()) {
                Rol r = new Rol();
                r.idRol = c.getLong(0);
                r.nombreRol = c.getString(1);
                r.descripcion = c.getString(2);
                lista.add(r);
            }
        }
        return lista;
    }

    // Lanza SQLiteConstraintException si el nombre del rol ya existe (UK)
    public void insertarRol(String nombreRol, String descripcion) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("nombre_rol", nombreRol);
        v.put("descripcion", descripcion);
        getWritableDatabase().insertOrThrow("ROL", null, v);
    }

    public void actualizarRol(long idRol, String nombreRol, String descripcion) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("nombre_rol", nombreRol);
        v.put("descripcion", descripcion);
        getWritableDatabase().updateWithOnConflict("ROL", v, "id_rol = ?",
                new String[]{String.valueOf(idRol)}, SQLiteDatabase.CONFLICT_ABORT);
    }

    // ---------------------------------------------------------------- Usuarios (RF-27)

    public List<Usuario> listarUsuarios() {
        List<Usuario> lista = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT u.id_usuario, u.username, u.nombre_completo, u.id_rol, r.nombre_rol, u.activo " +
                "FROM USUARIO u JOIN ROL r ON r.id_rol = u.id_rol ORDER BY u.username";
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) lista.add(leerUsuario(c));
        }
        return lista;
    }

    // Lanza SQLiteConstraintException si el username ya existe (UK)
    public void insertarUsuario(String username, String password, String nombreCompleto,
                                long idRol, boolean activo) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("username", username);
        v.put("password_hash", hash(username, password));
        v.put("nombre_completo", nombreCompleto);
        v.put("id_rol", idRol);
        v.put("activo", activo ? 1 : 0);
        getWritableDatabase().insertOrThrow("USUARIO", null, v);
    }

    // nuevaPassword puede ser null/vacía para dejar la contraseña actual
    public void actualizarUsuario(long idUsuario, String username, String nombreCompleto,
                                  long idRol, boolean activo, String nuevaPassword) {
        ContentValues v = new ContentValues();
        v.put("nombre_completo", nombreCompleto);
        v.put("id_rol", idRol);
        v.put("activo", activo ? 1 : 0);
        if (nuevaPassword != null && !nuevaPassword.isEmpty()) {
            v.put("password_hash", hash(username, nuevaPassword));
        }
        getWritableDatabase().update("USUARIO", v, "id_usuario = ?", new String[]{String.valueOf(idUsuario)});
    }

    // ---------------------------------------------------------------- Categorías (RF-09 / RF-10)

    // soloActivas = true -> solo las habilitadas (para asignarlas a productos, RF-10)
    public List<Categoria> listarCategorias(boolean soloActivas) {
        List<Categoria> lista = new ArrayList<>();
        String sql = "SELECT id_categoria, nombre_categoria, descripcion, activo FROM CATEGORIA" +
                (soloActivas ? " WHERE activo = 1" : "") + " ORDER BY nombre_categoria";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Categoria cat = new Categoria();
                cat.idCategoria = c.getLong(0);
                cat.nombreCategoria = c.getString(1);
                cat.descripcion = c.getString(2);
                cat.activo = c.getInt(3) == 1;
                lista.add(cat);
            }
        }
        return lista;
    }

    // Solo los nombres de categorías activas, para el desplegable de productos
    public List<String> listarNombresCategoriasActivas() {
        List<String> nombres = new ArrayList<>();
        for (Categoria c : listarCategorias(true)) nombres.add(c.nombreCategoria);
        return nombres;
    }

    // Lanza SQLiteConstraintException si el nombre ya existe (UK). activo = 1 por defecto.
    public void insertarCategoria(String nombreCategoria, String descripcion) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("nombre_categoria", nombreCategoria);
        v.put("descripcion", descripcion);
        v.put("activo", 1);
        getWritableDatabase().insertOrThrow("CATEGORIA", null, v);
    }
    public void actualizarCategoria(long idCategoria, String nombreCategoria, String descripcion,
                                    boolean activo) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("nombre_categoria", nombreCategoria);
        v.put("descripcion", descripcion);
        v.put("activo", activo ? 1 : 0);
        getWritableDatabase().updateWithOnConflict("CATEGORIA", v, "id_categoria = ?",
                new String[]{String.valueOf(idCategoria)}, SQLiteDatabase.CONFLICT_ABORT);
    }

    // ---------------------------------------------------------------- Proveedores

    // También sirve para el selector de proveedor al registrar movimientos de tipo entrada
    public List<Proveedor> listarProveedores() {
        List<Proveedor> lista = new ArrayList<>();
        String sql = "SELECT id_proveedor, rut_empresa, razon_social, numero_contacto, direccion " +
                "FROM PROVEEDOR ORDER BY razon_social";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                Proveedor p = new Proveedor();
                p.idProveedor = c.getLong(0);
                p.rutEmpresa = c.getString(1);
                p.razonSocial = c.getString(2);
                p.numeroContacto = c.getString(3);
                p.direccion = c.getString(4);
                lista.add(p);
            }
        }
        return lista;
    }

    // Lanza SQLiteConstraintException si el rut_empresa ya existe (UK)
    public void insertarProveedor(String rutEmpresa, String razonSocial, String numeroContacto,
                                  String direccion) throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("rut_empresa", rutEmpresa);
        v.put("razon_social", razonSocial);
        v.put("numero_contacto", numeroContacto);
        v.put("direccion", (direccion == null || direccion.isEmpty()) ? null : direccion);
        getWritableDatabase().insertOrThrow("PROVEEDOR", null, v);
    }
    public boolean actualizarProveedor(long idProveedor, String rutEmpresa, String razonSocial,
                                       String numeroContacto, String direccion)
            throws SQLiteConstraintException {
        ContentValues v = new ContentValues();
        v.put("rut_empresa", rutEmpresa);
        v.put("razon_social", razonSocial);
        v.put("numero_contacto", numeroContacto);
        v.put("direccion", (direccion == null || direccion.isEmpty()) ? null : direccion);
        return getWritableDatabase().updateWithOnConflict("PROVEEDOR", v, "id_proveedor = ?",
                new String[]{String.valueOf(idProveedor)}, SQLiteDatabase.CONFLICT_ABORT) > 0;
    }

    // Últimas entradas recibidas de un proveedor (texto listo para mostrar)
    public List<String> listarEntradasDeProveedor(long idProveedor, int limite) {
        List<String> lista = new ArrayList<>();
        String sql = "SELECT m.fecha, p.nombre, m.cantidad FROM MOVIMIENTO m " +
                "JOIN PRODUCTO p ON p.id_producto = m.id_producto " +
                "WHERE m.id_proveedor = ? AND m.tipo = 'ENTRADA' " +
                "ORDER BY m.id_movimiento DESC LIMIT " + limite;
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(idProveedor)})) {
            while (c.moveToNext()) {
                lista.add(c.getString(0) + "  •  " + c.getString(1) + "  •  "
                        + Producto.formatoKg(c.getLong(2) / 1000.0) + " kg");
            }
        }
        return lista;
    }

    // v6: stock entero en gramos; la interfaz mantiene kg, sin perder los decimales heredados.
    private void migrarInventario(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO CATEGORIA(nombre_categoria,activo) SELECT DISTINCT categoria,1 FROM PRODUCTO");
        db.execSQL("ALTER TABLE PRODUCTO RENAME TO PRODUCTO_V5");
        db.execSQL("CREATE TABLE PRODUCTO (id_producto INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sku_codigo TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK(length(trim(sku_codigo))>0), " +
                "nombre TEXT NOT NULL CHECK(length(trim(nombre))>0), descripcion TEXT NOT NULL DEFAULT '', " +
                "id_categoria INTEGER NOT NULL REFERENCES CATEGORIA(id_categoria), " +
                "stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(typeof(stock_actual)='integer' AND stock_actual>=0), " +
                "stock_minimo INTEGER NOT NULL DEFAULT 0 CHECK(typeof(stock_minimo)='integer' AND stock_minimo>=0), " +
                "precio_kg INTEGER NOT NULL DEFAULT 0 CHECK(precio_kg>=0), activo INTEGER NOT NULL DEFAULT 1 CHECK(activo IN(0,1)))");
        db.execSQL("INSERT INTO PRODUCTO(id_producto,sku_codigo,nombre,id_categoria,stock_actual,stock_minimo,precio_kg) " +
                "SELECT p.id_producto, printf('LEG-%06d',p.id_producto),p.nombre,c.id_categoria," +
                "CAST(round(p.stock*1000) AS INTEGER),CAST(round(p.stock_minimo*1000) AS INTEGER),p.precio_kg " +
                "FROM PRODUCTO_V5 p JOIN CATEGORIA c ON c.nombre_categoria=p.categoria COLLATE NOCASE");
        db.execSQL("DROP TABLE PRODUCTO_V5");
        db.execSQL("CREATE INDEX idx_producto_categoria ON PRODUCTO(id_categoria)");
        db.execSQL("CREATE TABLE MOVIMIENTO (id_movimiento INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "id_producto INTEGER NOT NULL REFERENCES PRODUCTO(id_producto), " +
                "id_usuario INTEGER NOT NULL REFERENCES USUARIO(id_usuario), " +
                "id_proveedor INTEGER REFERENCES PROVEEDOR(id_proveedor), " +
                "tipo TEXT NOT NULL CHECK(tipo IN('ENTRADA','SALIDA','INICIAL')), " +
                "cantidad INTEGER NOT NULL CHECK(typeof(cantidad)='integer' AND cantidad>0), " +
                "fecha TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')), motivo TEXT NOT NULL, " +
                "CHECK(tipo='ENTRADA' OR id_proveedor IS NULL))");
        db.execSQL("CREATE INDEX idx_movimiento_producto ON MOVIMIENTO(id_producto,id_movimiento)");
    }

    // v7: RF-20 agrega PRODUCTO.codigo_barras (único) y RF-24 crea EMPRESA con su ubicación fija.
    private void migrarV7(SQLiteDatabase db) {
        boolean existe = false;
        try (Cursor c = db.rawQuery("PRAGMA table_info(PRODUCTO)", null)) {
            while (c.moveToNext()) if ("codigo_barras".equalsIgnoreCase(c.getString(1))) existe = true;
        }
        if (!existe) db.execSQL("ALTER TABLE PRODUCTO ADD COLUMN codigo_barras TEXT");
        // Índice UNIQUE: un código no puede pertenecer a dos productos. Los NULL no cuentan como repetidos.
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_producto_barras ON PRODUCTO(codigo_barras)");
        // Una sola fila (id_empresa = 1) con las coordenadas; los CHECK impiden coordenadas imposibles.
        db.execSQL("CREATE TABLE IF NOT EXISTS EMPRESA (id_empresa INTEGER PRIMARY KEY CHECK(id_empresa=1), " +
                "nombre TEXT NOT NULL CHECK(length(trim(nombre))>0), " +
                "latitud REAL NOT NULL CHECK(latitud BETWEEN -90 AND 90), " +
                "longitud REAL NOT NULL CHECK(longitud BETWEEN -180 AND 180))");
        // Coordenadas de ejemplo (Santiago centro): se cambian desde la pantalla Ubicación (ADMIN).
        db.execSQL("INSERT OR IGNORE INTO EMPRESA(id_empresa,nombre,latitud,longitud) VALUES (1,?,?,?)",
                new Object[]{"Comercializadora Almacén El Ángel", -33.4489, -70.6693});
    }

    // v8: tabla SUCURSAL para el CRUD del mapa. Se crea con una "Casa matriz" copiada de EMPRESA.
    private void migrarV8(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS SUCURSAL (id_sucursal INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nombre TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK(length(trim(nombre))>0), " +
                "direccion TEXT NOT NULL DEFAULT '', " +
                "latitud REAL NOT NULL CHECK(latitud BETWEEN -90 AND 90), " +
                "longitud REAL NOT NULL CHECK(longitud BETWEEN -180 AND 180))");
        db.execSQL("INSERT OR IGNORE INTO SUCURSAL(nombre,direccion,latitud,longitud) " +
                "SELECT 'Casa matriz','',latitud,longitud FROM EMPRESA WHERE id_empresa=1");
    }

    // ---------------------------------------------------------------- Código de barras (RF-20)

    public static final int BARRAS_OK = 1, BARRAS_SKU_INEXISTENTE = 0, BARRAS_DUPLICADO = -1;

    // Busca por código de barras exacto (consulta parametrizada). Incluye productos deshabilitados
    // para que el escáner pueda avisar que están deshabilitados. null si no existe.
    public Producto buscarProductoPorBarras(String barras) {
        if (barras == null || barras.trim().isEmpty()) return null;
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_PRODUCTO + " WHERE p.codigo_barras=?",
                new String[]{barras.trim()})) {
            return c.moveToFirst() ? leerProducto(c) : null;
        }
    }

    // Asocia un código de barras a un producto ACTIVO identificado por SKU.
    // BARRAS_OK / BARRAS_SKU_INEXISTENTE (no existe o está deshabilitado) / BARRAS_DUPLICADO (ya es de otro).
    public int asignarBarrasPorSku(String sku, String barras) {
        String s = sku == null ? "" : sku.trim();
        String codigo = barras == null ? "" : barras.trim();
        if (s.isEmpty() || codigo.isEmpty()) throw new IllegalArgumentException("Indique SKU y código de barras.");
        ContentValues v = new ContentValues();
        v.put("codigo_barras", codigo);
        try {
            return getWritableDatabase().update("PRODUCTO", v, "sku_codigo=? AND activo=1",
                    new String[]{s}) > 0 ? BARRAS_OK : BARRAS_SKU_INEXISTENTE;
        } catch (SQLiteConstraintException e) {   // choca con idx_producto_barras
            return BARRAS_DUPLICADO;
        }
    }

    // ---- CRUD de códigos de barras (pantalla "Códigos de barras") ----

    // READ: productos activos con su código. estado: "TODOS", "CON" (solo con código) o "SIN" (solo sin código).
    // El texto busca por nombre, SKU o código. Todo con parámetros (?), nunca concatenado.
    public List<Producto> listarProductosConBarras(String texto, String estado) {
        List<Producto> lista = new ArrayList<>();
        String like = "%" + (texto == null ? "" : texto.trim()) + "%";
        String cond = "CON".equals(estado) ? " AND p.codigo_barras IS NOT NULL"
                : "SIN".equals(estado) ? " AND p.codigo_barras IS NULL" : "";
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_PRODUCTO +
                        " WHERE p.activo=1 AND (p.nombre LIKE ? OR p.sku_codigo LIKE ? OR IFNULL(p.codigo_barras,'') LIKE ?)"
                        + cond + " ORDER BY p.nombre",
                new String[]{like, like, like})) {
            while (c.moveToNext()) lista.add(leerProducto(c));
        }
        return lista;
    }

    // CREATE / UPDATE: asigna o cambia el código de un producto activo (por id).
    // BARRAS_OK / BARRAS_SKU_INEXISTENTE (producto inexistente o deshabilitado) / BARRAS_DUPLICADO (ya es de otro).
    public int actualizarBarras(long idProducto, String barras) {
        String codigo = barras == null ? "" : barras.trim();
        if (codigo.isEmpty() || codigo.length() > 64)
            throw new IllegalArgumentException("El código debe tener entre 1 y 64 caracteres.");
        ContentValues v = new ContentValues();
        v.put("codigo_barras", codigo);
        try {
            return getWritableDatabase().update("PRODUCTO", v, "id_producto=? AND activo=1",
                    new String[]{String.valueOf(idProducto)}) > 0 ? BARRAS_OK : BARRAS_SKU_INEXISTENTE;
        } catch (SQLiteConstraintException e) {   // choca con idx_producto_barras
            return BARRAS_DUPLICADO;
        }
    }

    // DELETE: quita el código (queda NULL). El producto y su stock no se tocan. true si tenía código.
    public boolean quitarBarras(long idProducto) {
        ContentValues v = new ContentValues();
        v.putNull("codigo_barras");
        return getWritableDatabase().update("PRODUCTO", v,
                "id_producto=? AND activo=1 AND codigo_barras IS NOT NULL",
                new String[]{String.valueOf(idProducto)}) == 1;
    }

    // ---------------------------------------------------------------- Empresa / ubicación (RF-24)

    // null si la ubicación no está configurada
    public Empresa obtenerEmpresa() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT nombre,latitud,longitud FROM EMPRESA WHERE id_empresa=1", null)) {
            if (!c.moveToFirst()) return null;
            Empresa e = new Empresa();
            e.nombre = c.getString(0); e.latitud = c.getDouble(1); e.longitud = c.getDouble(2);
            return e;
        }
    }

    public void actualizarEmpresa(String nombre, double latitud, double longitud) {
        if (nombre == null || nombre.trim().isEmpty() || !Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180)
            throw new IllegalArgumentException("Nombre obligatorio, latitud entre -90 y 90, longitud entre -180 y 180.");
        ContentValues v = new ContentValues();
        v.put("id_empresa", 1); v.put("nombre", nombre.trim());
        v.put("latitud", latitud); v.put("longitud", longitud);
        getWritableDatabase().insertWithOnConflict("EMPRESA", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ---------------------------------------------------------------- Sucursales (CRUD del mapa)

    private static final String SELECT_SUCURSAL =
            "SELECT id_sucursal,nombre,direccion,latitud,longitud FROM SUCURSAL";

    private Sucursal leerSucursal(Cursor c) {
        Sucursal s = new Sucursal();
        s.idSucursal = c.getLong(0); s.nombre = c.getString(1); s.direccion = c.getString(2);
        s.latitud = c.getDouble(3); s.longitud = c.getDouble(4);
        return s;
    }

    private ContentValues valoresSucursal(String nombre, String direccion, double latitud, double longitud) {
        if (nombre == null || nombre.trim().isEmpty() || !Double.isFinite(latitud) || !Double.isFinite(longitud)
                || latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180)
            throw new IllegalArgumentException("Nombre obligatorio, latitud entre -90 y 90, longitud entre -180 y 180.");
        ContentValues v = new ContentValues();
        v.put("nombre", nombre.trim());
        v.put("direccion", direccion == null ? "" : direccion.trim());
        v.put("latitud", latitud); v.put("longitud", longitud);
        return v;
    }

    // READ
    public List<Sucursal> listarSucursales() {
        List<Sucursal> lista = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_SUCURSAL + " ORDER BY nombre", null)) {
            while (c.moveToNext()) lista.add(leerSucursal(c));
        }
        return lista;
    }

    public Sucursal obtenerSucursal(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_SUCURSAL + " WHERE id_sucursal=?",
                new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? leerSucursal(c) : null;
        }
    }

    // CREATE: devuelve el id nuevo. Lanza SQLiteConstraintException si el nombre ya existe (UNIQUE).
    public long insertarSucursal(String nombre, String direccion, double latitud, double longitud) {
        return getWritableDatabase().insertOrThrow("SUCURSAL", null,
                valoresSucursal(nombre, direccion, latitud, longitud));
    }

    // UPDATE: true si la sucursal existía.
    public boolean actualizarSucursal(long id, String nombre, String direccion, double latitud, double longitud) {
        return getWritableDatabase().update("SUCURSAL", valoresSucursal(nombre, direccion, latitud, longitud),
                "id_sucursal=?", new String[]{String.valueOf(id)}) == 1;
    }

    // DELETE: borra la fila. true si existía.
    public boolean eliminarSucursal(long id) {
        return getWritableDatabase().delete("SUCURSAL", "id_sucursal=?", new String[]{String.valueOf(id)}) == 1;
    }

    private static final String SELECT_PRODUCTO = "SELECT p.id_producto,p.nombre,c.nombre_categoria," +
            "p.stock_actual/1000.0,p.stock_minimo/1000.0,p.precio_kg,p.sku_codigo,p.descripcion,p.id_categoria,p.activo,p.codigo_barras " +
            "FROM PRODUCTO p JOIN CATEGORIA c ON c.id_categoria=p.id_categoria";

    public List<Producto> listarProductos(String filtro) {
        List<Producto> lista = new ArrayList<>();
        String like = "%" + (filtro == null ? "" : filtro.trim()) + "%";
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_PRODUCTO +
                        " WHERE p.activo=1 AND (p.nombre LIKE ? OR p.sku_codigo LIKE ?) ORDER BY p.nombre",
                new String[]{like,like})) {
            while(c.moveToNext()) lista.add(leerProducto(c));
        }
        return lista;
    }
    public Producto obtenerProducto(long id) {
        try(Cursor c=getReadableDatabase().rawQuery(SELECT_PRODUCTO+" WHERE p.id_producto=?",new String[]{""+id})) {
            return c.moveToFirst()?leerProducto(c):null;
        }
    }
    private Producto leerProducto(Cursor c) {
        Producto p=new Producto();
        p.idProducto=c.getLong(0); p.nombre=c.getString(1); p.categoria=c.getString(2);
        p.stock=c.getDouble(3); p.stockMinimo=c.getDouble(4); p.precioKg=c.getInt(5);
        p.sku=c.getString(6); p.descripcion=c.getString(7); p.idCategoria=c.getLong(8); p.activo=c.getInt(9)==1; p.codigoBarras=c.getString(10);
        return p;
    }
    public int contarProductos() { return contar("SELECT COUNT(*) FROM PRODUCTO WHERE activo=1"); }
    public int contarStockBajo() { return contar("SELECT COUNT(*) FROM PRODUCTO WHERE activo=1 AND stock_actual<=stock_minimo"); }
    private int contar(String sql) {
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)) { c.moveToFirst(); return c.getInt(0); }
    }
    public static long gramos(double kg) {
        if(!Double.isFinite(kg) || kg<0 || kg>1_000_000_000 || Math.abs(kg*1000-Math.rint(kg*1000))>0.0001)
            throw new IllegalArgumentException("Use kilos positivos con hasta tres decimales (gramos).");
        return Math.round(kg*1000);
    }
    private ContentValues valores(Producto p) {
        if(p.sku==null || p.sku.trim().isEmpty() || p.nombre==null || p.nombre.trim().isEmpty())
            throw new IllegalArgumentException("SKU y nombre son obligatorios.");
        ContentValues v=new ContentValues();
        v.put("sku_codigo",p.sku.trim()); v.put("nombre",p.nombre.trim());
        v.put("descripcion",p.descripcion==null?"":p.descripcion.trim());
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id_categoria FROM CATEGORIA WHERE nombre_categoria=? COLLATE NOCASE AND activo=1",new String[]{p.categoria})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Seleccione una categoría activa.");
            v.put("id_categoria",c.getLong(0));
        }
        v.put("stock_minimo",gramos(p.stockMinimo)); v.put("precio_kg",p.precioKg);
        // RF-20: código de barras opcional; vacío = sin código (NULL). El índice UNIQUE evita repetidos.
        String cb = p.codigoBarras == null ? "" : p.codigoBarras.trim();
        if (cb.isEmpty()) v.putNull("codigo_barras"); else v.put("codigo_barras", cb);
        return v;
    }
    public void insertarProducto(Producto p) {
        ContentValues v=valores(p); v.put("stock_actual",gramos(p.stock));
        p.idProducto=getWritableDatabase().insertOrThrow("PRODUCTO",null,v);
    }
    public void actualizarProducto(Producto p) {
        // El stock se modifica exclusivamente mediante movimientos.
        if(getWritableDatabase().updateWithOnConflict("PRODUCTO",valores(p),"id_producto=? AND activo=1",
                new String[]{""+p.idProducto},SQLiteDatabase.CONFLICT_ABORT)!=1)
            throw new IllegalArgumentException("Producto inactivo o inexistente.");
    }
    public boolean eliminarProducto(long id) {
        ContentValues v=new ContentValues(); v.put("activo",0);
        return getWritableDatabase().update("PRODUCTO",v,"id_producto=? AND activo=1",new String[]{""+id})==1;
    }
    public void registrarMovimiento(long producto,long usuario,Long proveedor,String tipo,double kg,String motivo) {
        long cantidad=gramos(kg);
        if(cantidad==0 || !("ENTRADA".equals(tipo)||"SALIDA".equals(tipo)) || motivo==null || motivo.trim().isEmpty())
            throw new IllegalArgumentException("Indique tipo, cantidad mayor a cero y motivo.");
        if(proveedor!=null && !"ENTRADA".equals(tipo)) throw new IllegalArgumentException("Proveedor solo en entradas.");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            if(!usuarioActivo(usuario)) throw new IllegalArgumentException("Usuario inactivo.");
            long delta="ENTRADA".equals(tipo)?cantidad:-cantidad;
            android.database.sqlite.SQLiteStatement st=db.compileStatement(
                    "UPDATE PRODUCTO SET stock_actual=stock_actual+? WHERE id_producto=? AND activo=1 AND stock_actual+? BETWEEN 0 AND 1000000000000");
            try { st.bindLong(1,delta); st.bindLong(2,producto); st.bindLong(3,delta);
                if(st.executeUpdateDelete()!=1) throw new IllegalArgumentException("Stock insuficiente, fuera de rango o producto inactivo.");
            } finally { st.close(); }
            ContentValues v=new ContentValues(); v.put("id_producto",producto); v.put("id_usuario",usuario);
            if(proveedor!=null) v.put("id_proveedor",proveedor);
            v.put("tipo",tipo); v.put("cantidad",cantidad); v.put("motivo",motivo.trim());
            db.insertOrThrow("MOVIMIENTO",null,v); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public List<String> historialMovimientos() {
        List<String> lista=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT m.fecha,p.sku_codigo,p.nombre,m.tipo,m.cantidad,u.username,m.motivo,COALESCE(v.razon_social,'—') " +
                "FROM MOVIMIENTO m JOIN PRODUCTO p ON p.id_producto=m.id_producto JOIN USUARIO u ON u.id_usuario=m.id_usuario " +
                "LEFT JOIN PROVEEDOR v ON v.id_proveedor=m.id_proveedor ORDER BY m.id_movimiento DESC",null)) {
            while(c.moveToNext()) lista.add(c.getString(3)+" · "+Producto.formatoKg(c.getLong(4)/1000.0)+" kg · "+c.getString(1)+"\n"+
                    c.getString(2)+"\n"+c.getString(0)+" · "+c.getString(5)+"\n"+c.getString(6)+" · "+c.getString(7));
        } return lista;
    }

    // Productos activos filtrados por estado de stock: "TODOS", "NORMAL" o "BAJO".
    // Mantiene el buscador por nombre o SKU.
    public List<Producto> listarProductosStock(String filtro, String estado) {
        List<Producto> lista = new ArrayList<>();
        String like = "%" + (filtro == null ? "" : filtro.trim()) + "%";
        String cond = "";
        if ("NORMAL".equals(estado)) cond = " AND p.stock_actual > p.stock_minimo";
        else if ("BAJO".equals(estado)) cond = " AND p.stock_actual <= p.stock_minimo";
        try (Cursor c = getReadableDatabase().rawQuery(SELECT_PRODUCTO +
                " WHERE p.activo=1 AND (p.nombre LIKE ? OR p.sku_codigo LIKE ?)" + cond + " ORDER BY p.nombre",
                new String[]{like, like})) {
            while (c.moveToNext()) lista.add(leerProducto(c));
        }
        return lista;
    }

    // Historial de movimientos filtrado por tipo (null = todos) con datos listos para informe.
    public List<Movimiento> listarMovimientos(String tipo) {
        List<Movimiento> lista = new ArrayList<>();
        String sql = "SELECT m.fecha,p.sku_codigo,p.nombre,m.tipo,m.cantidad,u.username,m.motivo," +
                "COALESCE(v.razon_social,'—') FROM MOVIMIENTO m " +
                "JOIN PRODUCTO p ON p.id_producto=m.id_producto " +
                "JOIN USUARIO u ON u.id_usuario=m.id_usuario " +
                "LEFT JOIN PROVEEDOR v ON v.id_proveedor=m.id_proveedor " +
                (tipo == null ? "" : "WHERE m.tipo = ? ") +
                "ORDER BY m.id_movimiento DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, tipo == null ? null : new String[]{tipo})) {
            while (c.moveToNext()) {
                Movimiento m = new Movimiento();
                m.fecha = c.getString(0);
                m.sku = c.getString(1);
                m.producto = c.getString(2);
                m.tipo = c.getString(3);
                m.cantidad = c.getLong(4) / 1000.0;
                m.usuario = c.getString(5);
                m.motivo = c.getString(6);
                m.proveedor = c.getString(7);
                lista.add(m);
            }
        }
        return lista;
    }

}
