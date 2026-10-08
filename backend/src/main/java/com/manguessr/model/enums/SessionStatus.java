package com.manguessr.model.enums;

/** Cycle de vie d'une partie ou d'une manche. */
public enum SessionStatus {
    /** Manche ou partie en cours. */
    IN_PROGRESS,
    /** Bonne reponse trouvee. */
    SOLVED,
    /** Essais epuises sans trouver. */
    FAILED,
    /** Manche passee volontairement : rapporte zero. */
    SKIPPED,
    /** Toutes les manches sont terminees. */
    FINISHED
}
