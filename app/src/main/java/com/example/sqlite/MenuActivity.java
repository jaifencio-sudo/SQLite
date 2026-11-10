package com.example.sqlite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

// Panel principal: resumen del inventario y accesos según el rol de la sesión
public class MenuActivity extends ProtegidaActivity {

    private TextView lblSaludo, lblRol, lblTotal, lblBajo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        lblSaludo = findViewById(R.id.lblSaludo);
        lblRol = findViewById(R.id.lblRol);
        lblTotal = findViewById(R.id.lblTotal);
        lblBajo = findViewById(R.id.lblBajo);

        MaterialButton btnInventario = findViewById(R.id.btnInventario);
        MaterialButton btnVentas = findViewById(R.id.btnVentas);
        MaterialButton btnNuevo = findViewById(R.id.btnNuevoProducto);
        MaterialButton btnCategorias = findViewById(R.id.btnCategorias);
        MaterialButton btnProveedores = findViewById(R.id.btnProveedores);
        MaterialButton btnUsuarios = findViewById(R.id.btnUsuarios);
        MaterialButton btnEntradas = findViewById(R.id.btnEntradas);
        MaterialButton btnSalidas = findViewById(R.id.btnSalidas);
        MaterialButton btnSalir = findViewById(R.id.btnCerrarSesion);
        View cardProductos = findViewById(R.id.cardProductos);
        View cardStockBajo = findViewById(R.id.cardStockBajo);

        lblSaludo.setText("Hola, " + sesion.getNombre());
        lblRol.setText("Rol: " + sesion.getNombreRol());

        // Solo el ADMIN administra usuarios y roles
        btnUsuarios.setVisibility(sesion.esAdmin() ? View.VISIBLE : View.GONE);

        // Tarjetas de resumen: abren el listado filtrado por estado de stock
        cardProductos.setOnClickListener(v -> abrirInventario(InventarioActivity.MODO_NORMAL));
        cardStockBajo.setOnClickListener(v -> abrirInventario(InventarioActivity.MODO_BAJO));

        // Entradas y salidas: pantallas de solo lectura con informe PDF propio
        btnEntradas.setOnClickListener(v -> abrirHistorial("ENTRADA"));
        btnSalidas.setOnClickListener(v -> abrirHistorial("SALIDA"));

        btnInventario.setOnClickListener(v -> abrirInventario(InventarioActivity.MODO_TODOS));
        btnVentas.setOnClickListener(v -> startActivity(new Intent(this, MovimientosActivity.class)));
        btnNuevo.setOnClickListener(v -> startActivity(new Intent(this, ProductoActivity.class)));
        btnCategorias.setOnClickListener(v -> startActivity(new Intent(this, CategoriasActivity.class)));
        btnProveedores.setOnClickListener(v -> startActivity(new Intent(this, ProveedoresActivity.class)));
        btnUsuarios.setOnClickListener(v -> startActivity(new Intent(this, UsuariosActivity.class)));
        // RF-20: escáner de códigos de barras; RF-24/25/26: ubicación de la empresa y ruta
        findViewById(R.id.btnEscaner).setOnClickListener(v -> startActivity(new Intent(this, EscanerActivity.class)));
        findViewById(R.id.btnUbicacion).setOnClickListener(v -> startActivity(new Intent(this, UbicacionActivity.class)));
        // CRUD de códigos de barras (SQLite + cámara) y de sucursales (SQLite + Google Maps)
        findViewById(R.id.btnCodigosBarras).setOnClickListener(v -> startActivity(new Intent(this, CodigosBarrasActivity.class)));
        findViewById(R.id.btnSucursales).setOnClickListener(v -> startActivity(new Intent(this, SucursalesActivity.class)));
        btnSalir.setOnClickListener(v -> cerrarSesion());
    }

    private void abrirInventario(String modo) {
        Intent i = new Intent(this, InventarioActivity.class);
        i.putExtra(InventarioActivity.EXTRA_MODO, modo);
        startActivity(i);
    }

    private void abrirHistorial(String tipo) {
        Intent i = new Intent(this, HistorialMovimientosActivity.class);
        i.putExtra(HistorialMovimientosActivity.EXTRA_TIPO, tipo);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        DBHelper helper = new DBHelper(this);
        try {
            lblTotal.setText(String.valueOf(helper.contarProductos()));
            lblBajo.setText(String.valueOf(helper.contarStockBajo()));
        } finally {
            helper.close();
        }
    }
}
