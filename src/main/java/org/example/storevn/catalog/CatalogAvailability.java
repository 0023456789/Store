package org.example.storevn.catalog;

import lombok.Getter;
import lombok.ToString;

/**
 * Records which optional feature groups are available in the admin catalog DB.
 * Populated at startup by {@link CatalogContractChecker}.
 */
@Getter
@ToString
public class CatalogAvailability {

    private final boolean bonusAvailable;
    private final boolean appsAvailable;
    private final boolean voiceAvailable;

    public CatalogAvailability(boolean bonusAvailable, boolean appsAvailable, boolean voiceAvailable) {
        this.bonusAvailable = bonusAvailable;
        this.appsAvailable = appsAvailable;
        this.voiceAvailable = voiceAvailable;
    }
}
