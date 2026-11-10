package com.example.sqlite;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Pantalla de solo lectura para "Entradas" o "Salidas": muestra el historial del tipo
 * recibido por extra y permite descargar el informe PDF correspondiente.
 * El registro de nuevos movimientos queda a cargo del módulo de ventas.
 */
public class HistorialMovimientosActivity extends ProtegidaActivity {

    public static final String EXTRA_TIPO = "tipo_movimiento"; // ENTRADA | SALIDA

    private String tipo;
    private LinearLayout contenedor;
    private TextView resumen;
    private List<Movimiento> movimientos;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        tipo = getIntent().getStringExtra(EXTRA_TIPO);
        if (!"ENTRADA".equals(tipo) && !"SALIDA".equals(tipo)) tipo = "ENTRADA";
        boolean esEntrada = "ENTRADA".equals(tipo);

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 48, 24, 32);
        panel.setBackgroundColor(Color.rgb(46, 28, 21));
        scroll.addView(panel);

        TextView titulo = new TextView(this);
        titulo.setText(esEntrada ? "Entradas" : "Salidas");
        titulo.setTextSize(24);
        titulo.setTextColor(Color.rgb(212, 178, 140));
        panel.addView(titulo);

        TextView ayuda = new TextView(this);
        ayuda.setText(esEntrada ? "Historial de entradas de inventario"
                : "Historial de salidas de inventario");
        ayuda.setTextColor(Color.WHITE);
        panel.addView(ayuda);

        resumen = new TextView(this);
        resumen.setTextColor(Color.rgb(212, 178, 140));
        resumen.setPadding(0, 12, 0, 12);
        panel.addView(resumen);

        Button pdf = new Button(this);
        pdf.setText("Descargar informe PDF");
        panel.addView(pdf);
        pdf.setOnClickListener(v -> {
            if (movimientos == null) cargar();
            InformePdf.informeMovimientos(this, movimientos, tipo, sesion.getNombre());
        });

        Button volver = new Button(this);
        volver.setText("Volver");
        volver.setOnClickListener(v -> finish());
        panel.addView(volver);

        contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(0, 12, 0, 0);
        panel.addView(contenedor);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar();
    }

    private void cargar() {
        contenedor.removeAllViews();
        try (DBHelper db = new DBHelper(this)) {
            movimientos = db.listarMovimientos(tipo);
        }

        double total = 0;
        for (Movimiento m : movimientos) total += m.cantidad;
        resumen.setText("Movimientos: " + movimientos.size()
                + "   •   Total: " + Producto.formatoKg(total) + " kg");

        if (movimientos.isEmpty()) {
            addTexto("Aún no hay movimientos de este tipo.");
            return;
        }
        for (Movimiento m : movimientos) {
            addTexto(Producto.formatoKg(m.cantidad) + " kg  •  " + m.tipo + "\n"
                    + m.sku + " · " + m.producto + "\n"
                    + fechaCorta(m.fecha) + " · " + m.usuario + "\n"
                    + m.motivo + " · " + m.proveedor);
        }
    }

    private void addTexto(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setPadding(0, 20, 0, 20);
        contenedor.addView(t);
    }

    private static String fechaCorta(String iso) {
        if (iso == null || iso.length() < 16) return iso == null ? "" : iso;
        try {
            String d = iso.substring(0, 10);
            String h = iso.substring(11, 16);
            return d.substring(8, 10) + "/" + d.substring(5, 7) + "/" + d.substring(0, 4) + " " + h;
        } catch (Exception e) {
            return iso;
        }
    }
}
