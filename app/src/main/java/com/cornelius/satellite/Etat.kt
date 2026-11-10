package com.cornelius.satellite

/** Ce que l'écran montre de l'état du satellite. */
data class Etat(
    val phase: Phase = Phase.ARRET,
    /** Nom du satellite dans l'administration de Cornelius. */
    val nom: String = "",
    /** Une conversation est ouverte. */
    val conversation: Boolean = false,
    /** Micro coupé par la personne (bouton de vie privée). */
    val microCoupe: Boolean = false,
    /** Dernier score du mot d'appel, de 0 à 1. */
    val score: Float = 0f,
    /** Le mot d'appel vient d'être entendu. */
    val motEntendu: Boolean = false,
    /** Phrase d'explication de l'état. */
    val message: String = "",
    /** Dernière erreur, à montrer en clair. */
    val erreur: String? = null,
)

enum class Phase {
    /** L'écoute est arrêtée. */
    ARRET,
    /** Le téléphone attend le clic « Associer » dans l'administration. */
    APPAIRAGE,
    /** Le serveur a vu le téléphone et attend l'association. */
    EN_ATTENTE_ASSOCIATION,
    /** Connexion en cours. */
    CONNEXION,
    /** Connecté : le téléphone écoute. */
    CONNECTE,
    /** Quelque chose empêche d'avancer. */
    ERREUR,
}
