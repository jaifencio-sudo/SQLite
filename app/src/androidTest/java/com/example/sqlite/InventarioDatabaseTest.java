package com.example.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

public class InventarioDatabaseTest {
    Context ctx; DBHelper db; String name;
    @Before public void setup() {
        ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        name="test_inventario_"+System.nanoTime()+".db"; db=new DBHelper(ctx,name);
    }
    @After public void cleanup() { db.close(); ctx.deleteDatabase(name); }
    Producto nuevo(String sku,double stock) {
        Producto p=new Producto(); p.nombre="Prueba"; p.sku=sku; p.descripcion="Descripción";
        p.categoria="Semillas"; p.stock=stock; p.stockMinimo=1; p.precioKg=1000; db.insertarProducto(p); return p;
    }
    interface Action { void run(); }
    void falla(Action a) { try { a.run(); fail("Debía rechazar la operación"); } catch(IllegalArgumentException|android.database.sqlite.SQLiteException expected) {} }
    @Test public void crearBuscarYEditar() {
        Producto p=nuevo("SKU-UNO",2.125); assertEquals(1,db.listarProductos("sku-uno").size());
        p.nombre="Otro"; p.stock=999; db.actualizarProducto(p);
        Producto actual=db.obtenerProducto(p.idProducto); assertEquals("Otro",actual.nombre);
        assertEquals(2.125,actual.stock,0.00001); assertEquals("Descripción",actual.descripcion);
    }
    @Test public void skuUnicoSinDistinguirMayusculas() { nuevo("ABC",0); falla(()->nuevo("abc",1)); }
    @Test public void validaCantidadesYCategoria() {
        falla(()->nuevo("NEG",-1)); falla(()->nuevo("NAN",Double.NaN)); falla(()->nuevo("DEC",0.0001));
        Producto p=nuevo("CAT",0); p.categoria="No existe"; falla(()->db.actualizarProducto(p));
    }
    @Test public void movimientosAtomicosYStockInsuficiente() {
        Producto p=nuevo("MOV",2); long u=db.autenticar("admin","admin123").idUsuario;
        db.registrarMovimiento(p.idProducto,u,null,"ENTRADA",0.125,"Compra");
        db.registrarMovimiento(p.idProducto,u,null,"SALIDA",1,"Venta");
        assertEquals(1.125,db.obtenerProducto(p.idProducto).stock,0.00001);
        falla(()->db.registrarMovimiento(p.idProducto,u,null,"SALIDA",9,"Exceso"));
        assertEquals(2,db.historialMovimientos().size());
        assertEquals(1.125,db.obtenerProducto(p.idProducto).stock,0.00001);
        falla(()->db.registrarMovimiento(p.idProducto,u,999999L,"ENTRADA",1,"Proveedor inválido"));
        assertEquals(1.125,db.obtenerProducto(p.idProducto).stock,0.00001);
    }
    @Test public void bajaLogicaConservaHistorial() {
        Producto p=nuevo("BAJA",2); long u=db.autenticar("admin","admin123").idUsuario;
        db.registrarMovimiento(p.idProducto,u,null,"SALIDA",1,"Venta"); db.eliminarProducto(p.idProducto);
        assertTrue(db.listarProductos("BAJA").isEmpty()); assertFalse(db.obtenerProducto(p.idProducto).activo);
        assertEquals(1,db.historialMovimientos().size());
        falla(()->db.registrarMovimiento(p.idProducto,u,null,"ENTRADA",1,"Inactivo"));
    }
    @Test public void renombrarCategoriaMantieneRelacion() {
        Producto p=nuevo("FK",1); long id=db.obtenerProducto(p.idProducto).idCategoria;
        db.actualizarCategoria(id,"Nombre nuevo","",true); assertEquals("Nombre nuevo",db.obtenerProducto(p.idProducto).categoria);
        try(Cursor c=db.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)) { assertFalse(c.moveToFirst()); }
    }
    @Test public void migrarV5ConservaIdsStockYProveedor() {
        db.close();
        try(SQLiteDatabase old=ctx.openOrCreateDatabase(name,0,null)) {
            old.execSQL("CREATE TABLE CATEGORIA(id_categoria INTEGER PRIMARY KEY,nombre_categoria TEXT UNIQUE COLLATE NOCASE,descripcion TEXT,activo INTEGER)");
            old.execSQL("CREATE TABLE PRODUCTO(id_producto INTEGER PRIMARY KEY,nombre TEXT,categoria TEXT,stock REAL,stock_minimo REAL,precio_kg INTEGER)");
            old.execSQL("CREATE TABLE ROL(id_rol INTEGER PRIMARY KEY,nombre_rol TEXT,descripcion TEXT)");
            old.execSQL("CREATE TABLE USUARIO(id_usuario INTEGER PRIMARY KEY,username TEXT,password_hash TEXT,nombre_completo TEXT,id_rol INTEGER,activo INTEGER)");
            old.execSQL("CREATE TABLE PROVEEDOR(id_proveedor INTEGER PRIMARY KEY,rut_empresa TEXT,razon_social TEXT,numero_contacto TEXT,direccion TEXT)");
            old.execSQL("INSERT INTO PRODUCTO VALUES(42,'Heredado','Especial',18.5,2.125,1234)");
            old.execSQL("INSERT INTO PROVEEDOR VALUES(71,'12345678-9','Proveedor propio','12345678','Casa')"); old.setVersion(5);
        }
        db=new DBHelper(ctx,name); Producto p=db.obtenerProducto(42);
        assertEquals(18.5,p.stock,0.00001); assertEquals(2.125,p.stockMinimo,0.00001); assertEquals("LEG-000042",p.sku);
        assertEquals(71,db.listarProveedores().get(0).idProveedor);
        try(Cursor c=db.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)) { assertFalse(c.moveToFirst()); }
    }
    @Test public void proveedorV4DesconocidoRevierteSinBorrar() {
        db.close();
        try(SQLiteDatabase old=ctx.openOrCreateDatabase(name,0,null)) {
            old.execSQL("CREATE TABLE PROVEEDOR(id INTEGER PRIMARY KEY,otro TEXT)");
            old.execSQL("INSERT INTO PROVEEDOR VALUES(81,'Conservar')"); old.setVersion(4);
        }
        db=new DBHelper(ctx,name);
        try { db.getWritableDatabase(); fail("Debía rechazar esquema desconocido"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("conservar")); }
        db.close();
        try(SQLiteDatabase old=ctx.openOrCreateDatabase(name,0,null);
            Cursor c=old.rawQuery("SELECT otro FROM PROVEEDOR WHERE id=81",null)) {
            assertTrue(c.moveToFirst()); assertEquals("Conservar",c.getString(0)); assertEquals(4,old.getVersion());
        }
    }

    // ---- RF-20: código de barras ----
    @Test public void barrasAsociarBuscarYUnico() {
        Producto a=nuevo("BAR-A",1), b=nuevo("BAR-B",1);
        assertNull(db.buscarProductoPorBarras("7801"));
        assertEquals(DBHelper.BARRAS_OK,db.asignarBarrasPorSku("bar-a"," 7801 "));   // SKU sin distinguir mayúsculas
        assertEquals("BAR-A",db.buscarProductoPorBarras("7801").sku);
        assertEquals(DBHelper.BARRAS_DUPLICADO,db.asignarBarrasPorSku("BAR-B","7801")); // índice UNIQUE
        assertEquals(DBHelper.BARRAS_SKU_INEXISTENTE,db.asignarBarrasPorSku("NO-EXISTE","999"));
        db.eliminarProducto(b.idProducto);
        assertEquals(DBHelper.BARRAS_SKU_INEXISTENTE,db.asignarBarrasPorSku("BAR-B","555")); // deshabilitado
        falla(()->db.asignarBarrasPorSku("BAR-A"," "));
        db.eliminarProducto(a.idProducto);
        assertFalse(db.buscarProductoPorBarras("7801").activo);   // el escáner avisa "deshabilitado"
    }
    @Test public void barrasNoSeBorranAlEditarProducto() {
        Producto p=nuevo("BAR-E",3); db.asignarBarrasPorSku("BAR-E","ABC");
        Producto q=db.obtenerProducto(p.idProducto);        // el formulario carga el producto con su código
        q.nombre="Editado"; db.actualizarProducto(q);
        assertEquals("ABC",db.obtenerProducto(p.idProducto).codigoBarras);
        q.codigoBarras="  "; db.actualizarProducto(q);       // dejar el campo vacío quita el código
        assertNull(db.obtenerProducto(p.idProducto).codigoBarras);
    }
    @Test public void barrasDesdeElFormularioAlCrear() {
        Producto a=new Producto(); a.nombre="Con código"; a.sku="FORM-1"; a.descripcion=""; a.categoria="Semillas";
        a.stock=1; a.stockMinimo=0; a.precioKg=100; a.codigoBarras=" 7800000000017 "; db.insertarProducto(a);
        assertEquals("FORM-1",db.buscarProductoPorBarras("7800000000017").sku);
        Producto b=new Producto(); b.nombre="Repetido"; b.sku="FORM-2"; b.descripcion=""; b.categoria="Semillas";
        b.stock=1; b.stockMinimo=0; b.precioKg=100; b.codigoBarras="7800000000017";
        falla(()->db.insertarProducto(b));                    // UNIQUE
        b.codigoBarras=""; db.insertarProducto(b);            // sin código es válido
        Producto c=new Producto(); c.nombre="Otro sin código"; c.sku="FORM-3"; c.descripcion=""; c.categoria="Semillas";
        c.stock=1; c.stockMinimo=0; c.precioKg=100; db.insertarProducto(c);   // dos NULL no chocan
    }

    // ---- RF-24: ubicación de la empresa ----
    @Test public void empresaUbicacionYValidacion() {
        assertNotNull(db.obtenerEmpresa());
        db.actualizarEmpresa("Bodega",-29.9,-71.25);
        Empresa e=db.obtenerEmpresa(); assertEquals("Bodega",e.nombre); assertEquals(-29.9,e.latitud,0.00001);
        falla(()->db.actualizarEmpresa("X",95,0)); falla(()->db.actualizarEmpresa(" ",0,0));
        falla(()->db.actualizarEmpresa("X",0,Double.NaN));
        assertEquals(-29.9,db.obtenerEmpresa().latitud,0.00001);   // los rechazos no cambian nada
    }
    @Test public void migrarV6AgregaBarrasYEmpresa() {
        db.close();
        try(SQLiteDatabase old=ctx.openOrCreateDatabase(name,0,null)) {
            old.execSQL("CREATE TABLE CATEGORIA(id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,nombre_categoria TEXT NOT NULL UNIQUE COLLATE NOCASE,descripcion TEXT,activo INTEGER NOT NULL DEFAULT 1)");
            old.execSQL("CREATE TABLE PRODUCTO(id_producto INTEGER PRIMARY KEY AUTOINCREMENT,sku_codigo TEXT NOT NULL COLLATE NOCASE UNIQUE,nombre TEXT NOT NULL,descripcion TEXT NOT NULL DEFAULT '',id_categoria INTEGER NOT NULL REFERENCES CATEGORIA(id_categoria),stock_actual INTEGER NOT NULL DEFAULT 0,stock_minimo INTEGER NOT NULL DEFAULT 0,precio_kg INTEGER NOT NULL DEFAULT 0,activo INTEGER NOT NULL DEFAULT 1)");
            old.execSQL("INSERT INTO CATEGORIA(nombre_categoria) VALUES('Semillas')");
            old.execSQL("INSERT INTO PRODUCTO(sku_codigo,nombre,id_categoria,stock_actual) VALUES('V6-1','Previo',1,2500)");
            old.setVersion(6);
        }
        db=new DBHelper(ctx,name);
        Producto p=db.obtenerProducto(1); assertEquals(2.5,p.stock,0.00001); assertNull(p.codigoBarras);
        assertEquals(DBHelper.BARRAS_OK,db.asignarBarrasPorSku("V6-1","123"));
        assertNotNull(db.obtenerEmpresa());
    }

    // ---- CRUD de códigos de barras ----
    @Test public void crudBarrasAsignarCambiarListarYQuitar() {
        Producto a=nuevo("CRUD-A",1), b=nuevo("CRUD-B",1);
        assertEquals(DBHelper.BARRAS_OK,db.actualizarBarras(a.idProducto," 111 "));          // CREATE
        assertEquals("111",db.obtenerProducto(a.idProducto).codigoBarras);
        assertEquals(DBHelper.BARRAS_DUPLICADO,db.actualizarBarras(b.idProducto,"111"));    // UNIQUE
        assertEquals(DBHelper.BARRAS_OK,db.actualizarBarras(a.idProducto,"222"));           // UPDATE
        assertNull(db.buscarProductoPorBarras("111"));
        assertEquals("CRUD-A",db.buscarProductoPorBarras("222").sku);
        assertEquals(1,db.listarProductosConBarras("CRUD","CON").size());                  // READ con filtro
        assertEquals(1,db.listarProductosConBarras("CRUD","SIN").size());
        assertEquals(2,db.listarProductosConBarras("CRUD","TODOS").size());
        assertEquals(1,db.listarProductosConBarras("222","TODOS").size());                 // busca por código
        assertTrue(db.quitarBarras(a.idProducto));                                           // DELETE
        assertFalse(db.quitarBarras(a.idProducto));                                          // ya no tenía
        assertNull(db.obtenerProducto(a.idProducto).codigoBarras);
        assertTrue(db.obtenerProducto(a.idProducto).activo);                                  // el producto sigue activo
        falla(()->db.actualizarBarras(a.idProducto," "));
        db.eliminarProducto(b.idProducto);
        assertEquals(DBHelper.BARRAS_SKU_INEXISTENTE,db.actualizarBarras(b.idProducto,"333")); // deshabilitado
    }

    // ---- CRUD de sucursales (mapa) ----
    @Test public void crudSucursales() {
        int inicial=db.listarSucursales().size();
        assertTrue(inicial>=1);                                                              // "Casa matriz" de la migración v8
        long id=db.insertarSucursal(" La Serena ","Centro",-29.9027,-71.2519);              // CREATE
        Sucursal s=db.obtenerSucursal(id); assertEquals("La Serena",s.nombre); assertEquals(-29.9027,s.latitud,0.00001);
        assertEquals(inicial+1,db.listarSucursales().size());                                // READ
        assertTrue(db.actualizarSucursal(id,"La Serena 2","",-29.95,-71.3));                 // UPDATE
        assertEquals("La Serena 2",db.obtenerSucursal(id).nombre);
        assertFalse(db.actualizarSucursal(999999L,"X","",0,0));
        assertTrue(db.eliminarSucursal(id));                                                 // DELETE
        assertNull(db.obtenerSucursal(id));
        assertFalse(db.eliminarSucursal(id));
    }
    @Test public void sucursalesValidanNombreUnicoYCoordenadas() {
        db.insertarSucursal("Ovalle","",-30.6,-71.2);
        falla(()->db.insertarSucursal("ovalle","",-30.6,-71.2));       // nombre único sin distinguir mayúsculas
        falla(()->db.insertarSucursal(" ","",0,0));
        falla(()->db.insertarSucursal("Fuera","",95,0));
        falla(()->db.insertarSucursal("Fuera","",0,181));
        falla(()->db.insertarSucursal("Fuera","",Double.NaN,0));
    }
}
