package com.example.sheetsexport.web.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * D'où viennent les données à exporter. Deux stratégies, choisies par le champ {@code type} du JSON :
 *
 * <ul>
 *   <li>{@code "inline"} ({@link InlineSource}) — les lignes sont fournies par le front
 *       (lecture du DOM). C'est le mode historique du POC.</li>
 *   <li>{@code "dataset"} ({@link DatasetSource}) — le front n'envoie qu'un identifiant de jeu de
 *       données + des filtres ; le serveur va chercher les lignes lui-même (ici : source statique,
 *       demain : base de données).</li>
 * </ul>
 *
 * Le serveur reste ainsi la source de vérité pour le mode {@code dataset} : le client ne peut ni
 * falsifier les valeurs, ni exfiltrer des colonnes qu'il ne voit pas.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = InlineSource.class, name = "inline"),
        @JsonSubTypes.Type(value = DatasetSource.class, name = "dataset")
})
public sealed interface ExportSource permits InlineSource, DatasetSource {
}
