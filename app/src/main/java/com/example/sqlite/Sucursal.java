package com.example.sqlite;

// Modelo de la tabla SUCURSAL: un punto de la comercializadora con sus coordenadas en el mapa.
public class Sucursal {
    public long idSucursal;
    public String nombre;
    public String direccion;   // opcional, "" si no tiene
    public double latitud;
    public double longitud;
}
