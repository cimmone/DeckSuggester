package com.decksuggester.cards;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Document(collection = "cards")
public class Card {

    @Id
    private String mongoId;

    @Field("id")
    private String scryfallId;

    @Field("oracle_id")
    private String oracleId;

    private String name;

    private Double cmc;

    private List<String> colors;

    @Field("color_identity")
    private List<String> colorIdentity;

    @Field("type_line")
    private String typeLine;

    private String power;

    private String toughness;

    @Field("released_at")
    private String releasedAt;

    /** Scryfall layout string (e.g. {@code "transform"}, {@code "flip"}). */
    private String layout;

    /** Per-format legality strings keyed by format slug. */
    private Map<String, String> legalities;

    @Field("image_uris")
    private Map<String, String> imageUris;

    @Field("card_faces")
    private List<CardFace> cardFaces;

    public String getMongoId() {
        return mongoId;
    }

    public String getScryfallId() {
        return scryfallId;
    }

    public String getOracleId() {
        return oracleId;
    }

    public String getName() {
        return name;
    }

    public Double getCmc() {
        return cmc;
    }

    public List<String> getColors() {
        return colors == null ? Collections.emptyList() : colors;
    }

    public List<String> getColorIdentity() {
        return colorIdentity == null ? Collections.emptyList() : colorIdentity;
    }

    public String getTypeLine() {
        return typeLine;
    }

    public String getPower() {
        return power;
    }

    public String getToughness() {
        return toughness;
    }

    public String getReleasedAt() {
        return releasedAt;
    }

    public String getLayout() {
        return layout;
    }

    public Map<String, String> getLegalities() {
        return legalities == null ? Collections.emptyMap() : legalities;
    }

    /** {@code true} when the card is legal in the given format. */
    public boolean isLegal(String format) {
        String value = getLegalities().get(format);
        return "legal".equalsIgnoreCase(value);
    }

    public String getImageUrl() {
        if (imageUris != null && imageUris.get("normal") != null) {
            return imageUris.get("normal");
        }
        if (cardFaces != null) {
            return cardFaces.stream()
                    .map(CardFace::imageUris)
                    .filter(images -> images != null && images.get("normal") != null)
                    .map(images -> images.get("normal"))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** Back-face image URL for double-faced cards, or {@code null} otherwise. */
    public String getBackImageUrl() {
        if (cardFaces == null || cardFaces.size() < 2) {
            return null;
        }
        return cardFaces.stream()
                .skip(1)
                .map(CardFace::imageUris)
                .filter(images -> images != null && images.get("normal") != null)
                .map(images -> images.get("normal"))
                .findFirst()
                .orElse(null);
    }

    public record CardFace(@Field("image_uris") Map<String, String> imageUris) {
    }

}
