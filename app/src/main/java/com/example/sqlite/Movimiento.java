package com.example.sqlite;

// Fila del historial de MOVIMIENTO, lista para mostrar en pantalla y para armar informes PDF.
public class Movimiento {
    public String fecha;
    public String sku;
    public String producto;
    public String tipo;      // ENTRADA | SALIDA | INICIAL
    public String usuario;
    public String motivo;
    public String proveedor; // "—" cuando no aplica
    public double cantidad;  // kg
}
