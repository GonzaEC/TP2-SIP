package ar.edu.sip;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Representa un resultado individual del scraping de MercadoLibre. */
public class ProductResult {
  private String titulo;
  private Long precio;
  private String link;

  @JsonProperty("tienda_oficial")
  private String tiendaOficial;

  @JsonProperty("envio_gratis")
  private boolean envioGratis;

  @JsonProperty("cuotas_sin_interes")
  private String cuotasSinInteres;

  public String getTitulo() {
    return titulo;
  }

  public void setTitulo(String titulo) {
    this.titulo = titulo;
  }

  public Long getPrecio() {
    return precio;
  }

  public void setPrecio(Long precio) {
    this.precio = precio;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getTiendaOficial() {
    return tiendaOficial;
  }

  public void setTiendaOficial(String tiendaOficial) {
    this.tiendaOficial = tiendaOficial;
  }

  public boolean isEnvioGratis() {
    return envioGratis;
  }

  public void setEnvioGratis(boolean envioGratis) {
    this.envioGratis = envioGratis;
  }

  public String getCuotasSinInteres() {
    return cuotasSinInteres;
  }

  public void setCuotasSinInteres(String cuotasSinInteres) {
    this.cuotasSinInteres = cuotasSinInteres;
  }
}
