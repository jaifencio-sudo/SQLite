package com.example.sqlite;

import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.ArrayList;

/** Registro de entradas y salidas de productos con historial de inventario. */
public class MovimientosActivity extends ProtegidaActivity {
    // Extras opcionales: los envía EscanerActivity para dejar elegidos el producto y el tipo (CU-07)
    public static final String EXTRA_SKU = "sku";
    public static final String EXTRA_TIPO = "tipo";
    private Spinner producto, tipo, proveedor;
    private EditText cantidad, motivo;
    private LinearLayout historial;
    private List<Producto> productos;
    private List<Proveedor> proveedores;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24,48,24,32); panel.setBackgroundColor(Color.rgb(46,28,21)); scroll.addView(panel);
        TextView titulo=new TextView(this); titulo.setText("Ventas y Movimientos de Inventario");
        titulo.setTextSize(24); titulo.setTextColor(Color.rgb(212,178,140)); panel.addView(titulo);
        TextView ayuda=new TextView(this); ayuda.setText("Use SALIDA para ventas/retiros y ENTRADA para ingresos. Precisión de 1 g."); ayuda.setTextColor(Color.WHITE); panel.addView(ayuda);
        try(DBHelper db=new DBHelper(this)) { productos=db.listarProductos(""); proveedores=db.listarProveedores(); }
        List<String> nombres=new ArrayList<>(); for(Producto p:productos) nombres.add(p.sku+" · "+p.nombre);
        producto=selector(panel,nombres);
        tipo=selector(panel,java.util.Arrays.asList("ENTRADA","SALIDA"));
        // Por defecto SALIDA (Ventas/Retiros); si viene del escáner, se respeta el tipo y el producto pedidos
        tipo.setSelection("ENTRADA".equals(getIntent().getStringExtra(EXTRA_TIPO)) ? 0 : 1);
        String skuPedido = getIntent().getStringExtra(EXTRA_SKU);
        if (skuPedido != null) {
            for (int i = 0; i < productos.size(); i++) {
                if (skuPedido.equalsIgnoreCase(productos.get(i).sku)) { producto.setSelection(i); break; }
            }
        }
        
        // Habilitar o deshabilitar el proveedor dependiendo del tipo de movimiento
        tipo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (proveedor == null || motivo == null) return; // la pantalla aún se está construyendo
                if (position == 1) { // SALIDA (Ventas/Retiros)
                    proveedor.setSelection(0); // Sin proveedor
                    proveedor.setEnabled(false);
                    proveedor.setAlpha(0.5f);
                    motivo.setHint("Motivo (Ej: Venta #123, Retiro por merma)");
                } else { // ENTRADA
                    proveedor.setEnabled(true);
                    proveedor.setAlpha(1.0f);
                    motivo.setHint("Motivo del movimiento (Ej: Compra a proveedor)");
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {}
        });
        List<String> empresas=new ArrayList<>(); empresas.add("Sin proveedor");
        for(Proveedor p:proveedores) empresas.add(p.razonSocial); proveedor=selector(panel,empresas);
        cantidad=campo(panel,"Cantidad en kg",8194); motivo=campo(panel,"Motivo del movimiento",1);
        Button guardar=new Button(this); guardar.setText("Registrar movimiento"); panel.addView(guardar);
        guardar.setOnClickListener(v->{
            try(DBHelper db=new DBHelper(this)) {
                if(productos.isEmpty()) throw new IllegalArgumentException("Registre un producto primero.");
                db.registrarMovimiento(productos.get(producto.getSelectedItemPosition()).idProducto,sesion.getIdUsuario(),
                    proveedor.getSelectedItemPosition()==0?null:proveedores.get(proveedor.getSelectedItemPosition()-1).idProveedor,
                    tipo.getSelectedItem().toString(),Double.parseDouble(cantidad.getText().toString().replace(',','.')),motivo.getText().toString());
                cantidad.setText(""); motivo.setText(""); cargar(); Toast.makeText(this,"Movimiento registrado",Toast.LENGTH_SHORT).show();
            } catch(NumberFormatException e) { Toast.makeText(this,"Ingrese una cantidad válida",Toast.LENGTH_LONG).show(); }
              catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        });
        Button volver=new Button(this); volver.setText("Volver"); volver.setOnClickListener(v->finish()); panel.addView(volver);
        historial=new LinearLayout(this); historial.setOrientation(LinearLayout.VERTICAL); panel.addView(historial);
        setContentView(scroll); cargar();
    }
    private Spinner selector(LinearLayout panel,List<String> valores) {
        Spinner s=new Spinner(this); s.setBackgroundColor(Color.rgb(238,224,206));
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,valores);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); s.setAdapter(a); panel.addView(s); return s;
    }
    private EditText campo(LinearLayout panel,String hint,int input) {
        EditText e=new EditText(this); e.setHint(hint); e.setInputType(input); e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.LTGRAY); panel.addView(e); return e;
    }
    private void cargar() {
        historial.removeAllViews();
        try(DBHelper db=new DBHelper(this)) {
            List<String> rows=db.historialMovimientos();
            if(rows.isEmpty()) rows.add("Aún no hay movimientos registrados.");
            for(String row:rows) { TextView t=new TextView(this); t.setText(row); t.setTextColor(Color.WHITE); t.setPadding(0,20,0,20); historial.addView(t); }
        }
    }
}
