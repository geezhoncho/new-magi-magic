package com.yourname.magi.compat.irons;

import com.yourname.magi.compat.CompatManager;
import com.yourname.magi.compat.CompatModule;

/** Placeholder: Phase 3 - mana, spell registry. Only instantiated by CompatManager when the mod is present. */
public class IronsCompat implements CompatModule {
    @Override
    public String modId() {
        return CompatManager.IRONS_SPELLBOOKS;
    }
}
