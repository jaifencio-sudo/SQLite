package com.example.sqlite;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Genera informes PDF (inventario y movimientos) sin librerías externas y los guarda
 * en la carpeta pública Descargas del dispositivo, dentro de "InformesAlmacen".
 * Al terminar ofrece abrir o compartir el archivo.
 */
public final class InformePdf {

    public static final String EMPRESA = "COMERCIALIZADORA ALMACÉN EL ÁNGEL";
    private static final String SUBCARPETA = "InformesAlmacen";

    private static final int PAG_ANCHO = 842; // A4 horizontal, en puntos (72 dpi)
    private static final int PAG_ALTO = 595;
    private static final int MARGEN = 36;

    private static final int CAFE = Color.rgb(46, 28, 21);
    private static final int DORADO = Color.rgb(212, 178, 140);
    private static final int ROJO = Color.rgb(198, 40, 40);
    private static final int GRIS = Color.rgb(110, 105, 100);
    private static final int LINEA = Color.rgb(210, 195, 178);
    private static final int CEBRA = Color.rgb(246, 241, 235);

    private InformePdf() { }

    // ---------------------------------------------------------------- Informes públicos

    /** modo: "TODOS" (inventario completo), "NORMAL" (stock normal) o "BAJO" (stock bajo). */
    public static void informeInventario(Activity act, List<Producto> productos, String modo, String usuario) {
        String titulo;
        if ("NORMAL".equals(modo)) titulo = "Informe de stock en estado normal";
        else if ("BAJO".equals(modo)) titulo = "Informe de stock bajo";
        else titulo = "Informe de inventario completo";

        List<String[]> filas = new ArrayList<>();
        double totalKg = 0;
        int bajos = 0;
        for (Producto p : productos) {
            totalKg += p.stock;
            boolean bajo = p.stockBajo();
            if (bajo) bajos++;
            filas.add(new String[]{
                    p.sku,
                    p.nombre,
                    p.categoria,
                    Producto.formatoKg(p.stock) + " kg",
                    Producto.formatoKg(p.stockMinimo) + " kg",
                    Producto.formatoPrecio(p.precioKg),
                    bajo ? "STOCK BAJO" : "Normal"
            });
        }
        String[] cabeceras = {"SKU", "Producto", "Categoría", "Stock", "Mínimo", "Precio/kg", "Estado"};
        float[] pesos = {1.4f, 2.7f, 2.0f, 1.2f, 1.2f, 1.4f, 1.4f};
        String[] resumen = {
                "Productos listados: " + productos.size(),
                "En stock bajo: " + bajos,
                "Stock total: " + Producto.formatoKg(totalKg) + " kg"
        };
        generar(act, "Inventario", titulo, usuario, cabeceras, pesos, filas, resumen);
    }

    /** tipo: "ENTRADA", "SALIDA" o null para todos los movimientos. */
    public static void informeMovimientos(Activity act, List<Movimiento> movs, String tipo, String usuario) {
        String titulo = "ENTRADA".equals(tipo) ? "Informe de entradas"
                : "SALIDA".equals(tipo) ? "Informe de salidas"
                : "Informe de movimientos";

        List<String[]> filas = new ArrayList<>();
        double totalKg = 0;
        for (Movimiento m : movs) {
            totalKg += m.cantidad;
            filas.add(new String[]{
                    fechaCorta(m.fecha),
                    m.tipo,
                    m.sku,
                    m.producto,
                    Producto.formatoKg(m.cantidad) + " kg",
                    m.usuario,
                    m.motivo,
                    m.proveedor
            });
        }
        String[] cabeceras = {"Fecha", "Tipo", "SKU", "Producto", "Cantidad", "Usuario", "Motivo", "Proveedor"};
        float[] pesos = {1.5f, 1.1f, 1.3f, 2.5f, 1.2f, 1.3f, 2.2f, 1.8f};
        String[] resumen = {
                "Movimientos listados: " + movs.size(),
                "Total movido: " + Producto.formatoKg(totalKg) + " kg"
        };
        String base = tipo == null ? "Movimientos" : "Movimientos_" + tipo;
        generar(act, base, titulo, usuario, cabeceras, pesos, filas, resumen);
    }

    // ---------------------------------------------------------------- Generación

    private static void generar(Activity act, String base, String titulo, String usuario,
                                String[] cabeceras, float[] pesos, List<String[]> filas, String[] resumen) {
        PdfDocument doc = new PdfDocument();
        String marca = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
        String nombreArchivo = base + "_" + marca + ".pdf";
        try {
            dibujar(doc, titulo, usuario, cabeceras, pesos, filas, resumen);
            Uri uri = guardar(act, nombreArchivo, doc);
            mostrarResultado(act, uri, "Descargas/" + SUBCARPETA + "/" + nombreArchivo);
        } catch (Exception e) {
            Toast.makeText(act, "No se pudo generar el informe: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            doc.close();
        }
    }

    private static void dibujar(PdfDocument doc, String titulo, String usuario,
                                String[] cabeceras, float[] pesos, List<String[]> filas, String[] resumen) {
        final int tablaTop = 130;
        final int cabeceraH = 22;
        final int renglonH = 20;
        final int areaFin = PAG_ALTO - MARGEN - 18;

        int filasPorPagina = Math.max(1, (areaFin - (tablaTop + cabeceraH)) / renglonH);
        int totalPaginas = Math.max(1, (int) Math.ceil(filas.size() / (double) filasPorPagina));

        float util = PAG_ANCHO - 2f * MARGEN;
        float suma = 0;
        for (float w : pesos) suma += w;
        float[] ancho = new float[pesos.length];
        float[] x = new float[pesos.length];
        float acc = MARGEN;
        for (int i = 0; i < pesos.length; i++) {
            ancho[i] = util * pesos[i] / suma;
            x[i] = acc;
            acc += ancho[i];
        }

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(new Date());

        for (int pag = 0; pag < totalPaginas; pag++) {
            PdfDocument.Page page = doc.startPage(
                    new PdfDocument.PageInfo.Builder(PAG_ANCHO, PAG_ALTO, pag + 1).create());
            Canvas c = page.getCanvas();

            // Banner superior
            p.setColor(CAFE);
            c.drawRect(MARGEN, MARGEN, PAG_ANCHO - MARGEN, MARGEN + 34, p);
            p.setColor(Color.WHITE);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(13);
            c.drawText(elidir(p, EMPRESA, util - 20), MARGEN + 10, MARGEN + 22, p);

            // Título y subtítulo
            p.setColor(CAFE);
            p.setTextSize(16);
            c.drawText(titulo, MARGEN, MARGEN + 62, p);

            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(9);
            p.setColor(GRIS);
            String sub = "Generado: " + fecha + "   •   Usuario: "
                    + (usuario == null || usuario.isEmpty() ? "—" : usuario);
            c.drawText(elidir(p, sub, util), MARGEN, MARGEN + 78, p);

            // Cabecera de la tabla
            p.setColor(DORADO);
            c.drawRect(MARGEN, tablaTop, PAG_ANCHO - MARGEN, tablaTop + cabeceraH, p);
            p.setColor(CAFE);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextSize(8);
            for (int i = 0; i < cabeceras.length; i++)
                c.drawText(elidir(p, cabeceras[i], ancho[i] - 6), x[i] + 3, tablaTop + 15, p);

            // Filas
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(8);
            int desde = pag * filasPorPagina;
            for (int k = 0; k < filasPorPagina; k++) {
                int idx = desde + k;
                if (idx >= filas.size()) break;
                float y = tablaTop + cabeceraH + (k + 1) * renglonH;
                if (k % 2 == 1) {
                    p.setColor(CEBRA);
                    c.drawRect(MARGEN, y - renglonH + 3, PAG_ANCHO - MARGEN, y + 3, p);
                }
                String[] fila = filas.get(idx);
                for (int i = 0; i < fila.length && i < cabeceras.length; i++) {
                    p.setColor("STOCK BAJO".equals(fila[i]) ? ROJO : GRIS);
                    c.drawText(elidir(p, fila[i], ancho[i] - 6), x[i] + 3, y, p);
                }
            }
            if (filas.isEmpty() && pag == 0) {
                p.setColor(GRIS);
                p.setTextSize(11);
                c.drawText("No hay registros para este informe.", MARGEN, tablaTop + cabeceraH + 30, p);
            }

            // Pie: separador, resumen y paginación
            p.setColor(LINEA);
            p.setStrokeWidth(1);
            c.drawLine(MARGEN, areaFin, PAG_ANCHO - MARGEN, areaFin, p);

            p.setTextSize(8);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < resumen.length; i++) {
                if (i > 0) sb.append("     |     ");
                sb.append(resumen[i]);
            }
            p.setColor(CAFE);
            c.drawText(elidir(p, sb.toString(), util - 90), MARGEN, areaFin + 14, p);

            String pagTxt = "Página " + (pag + 1) + " de " + totalPaginas;
            p.setColor(GRIS);
            c.drawText(pagTxt, PAG_ANCHO - MARGEN - p.measureText(pagTxt), areaFin + 14, p);

            doc.finishPage(page);
        }
    }

    private static Uri guardar(Activity act, String nombreArchivo, PdfDocument doc) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, nombreArchivo);
        v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + SUBCARPETA);
        Uri uri = act.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IllegalStateException("No se pudo crear el archivo en Descargas.");
        try (OutputStream os = act.getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("No se pudo abrir el archivo para escritura.");
            doc.writeTo(os);
        }
        return uri;
    }

    // ---------------------------------------------------------------- Acciones sobre el PDF

    private static void mostrarResultado(Activity act, Uri uri, String ubicacion) {
        if (act.isFinishing()) return;
        new MaterialAlertDialogBuilder(act)
                .setTitle("Informe generado")
                .setMessage("El PDF se guardó en:\n" + ubicacion)
                .setPositiveButton("Abrir", (d, w) -> abrir(act, uri))
                .setNeutralButton("Compartir", (d, w) -> compartir(act, uri))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private static void abrir(Activity act, Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
        try {
            act.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(act, "No hay una app para abrir PDF. Puede compartirlo.", Toast.LENGTH_LONG).show();
        }
    }

    private static void compartir(Activity act, Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        act.startActivity(Intent.createChooser(i, "Compartir informe"));
    }

    // ---------------------------------------------------------------- Utilidades

    private static String elidir(Paint p, String s, float max) {
        if (s == null) return "";
        if (p.measureText(s) <= max) return s;
        String puntos = "...";
        int i = s.length();
        while (i > 0 && p.measureText(s.substring(0, i) + puntos) > max) i--;
        return s.substring(0, Math.max(0, i)) + puntos;
    }

    // "2026-11-04T20:31:12.345Z" -> "04/11/2026 20:31"
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
