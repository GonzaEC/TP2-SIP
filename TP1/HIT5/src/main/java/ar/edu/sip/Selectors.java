package ar.edu.sip;

import org.openqa.selenium.By;

/**
 * Módulo de selectores centralizado para MercadoLibre.
 */
public class Selectors {

    private Selectors() {}

    // Búsqueda
    public static final By INPUT_BUSQUEDA = By.name("as_word");

    // Contenedores
    public static final By CONTENEDOR_RESULTADOS = By.cssSelector("li.ui-search-layout__item, .ui-search-result");

    // Campos de producto
    public static final By PRODUCT_LINK = By.cssSelector("a.poly-component__title, .ui-search-item__title, .ui-search-link__title-card, .ui-search-item__group__element.ui-search-link");
    public static final By PRODUCT_PRICE = By.cssSelector(".andes-money-amount__fraction");
    public static final By PRODUCT_OFFICIAL_STORE = By.cssSelector(".ui-search-item__group__element--official-store-badge, .poly-component__seller");
    public static final By PRODUCT_SHIPPING = By.cssSelector(".ui-search-item__shipping--free, .poly-component__shipping");
    public static final By PRODUCT_INSTALLMENTS = By.cssSelector(".ui-search-item__group__element--installments, .poly-component__installments");

    // Filtros y UI
    public static final By DROPDOWN_ORDEN = By.cssSelector("button.andes-dropdown__trigger, .ui-search-sort-filter button");
    public static final By LISTBOX_ORDEN = By.cssSelector("ul[role='listbox']");
    public static final By BANNER_COOKIES = By.cssSelector("button[data-testid='action:understood-button'], button[data-testid='cookie-consent-accept-btn']");

    public static By filtroPorTexto(String texto) {
        return By.xpath("//aside//a[normalize-space()='" + texto + "' or .//span[normalize-space()='" + texto + "']]");
    }

    public static By opcionOrden(String texto) {
        return By.xpath("//li[@role='option'][.//span[contains(normalize-space(),'" + texto + "')] or contains(normalize-space(),'" + texto + "')]");
    }
}
