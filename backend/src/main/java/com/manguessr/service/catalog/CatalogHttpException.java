package com.manguessr.service.catalog;

/** Echec d'appel a une source externe, apres epuisement des tentatives. */
public class CatalogHttpException extends RuntimeException {

    private final int statusCode;

    public CatalogHttpException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public CatalogHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
