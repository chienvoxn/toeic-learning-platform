package com.toeic.backend.identity.application;

import java.util.Locale;

/** Validate lệnh theo allowlist; không thay entity bằng payload tùy ý. */
public record RoleEdit(boolean namePresent,String name,boolean descriptionPresent,String description,
                       boolean displayNamePresent,String displayName) {
    public RoleEdit(boolean namePresent,String name,boolean descriptionPresent,String description) {
        this(namePresent,name,descriptionPresent,description,false,null);
    }
    public RoleEdit {
        if(!namePresent && !descriptionPresent && !displayNamePresent) throw RbacException.invalid();
        if(namePresent) {
            if(name==null || name.length()>50) throw RbacException.invalid();
            name=name.strip().toUpperCase(Locale.ROOT);
            if(!name.matches("[A-Z][A-Z0-9_]{1,49}") || name.startsWith("ROLE_")) throw RbacException.invalid();
        }
        if(descriptionPresent && description!=null) {
            if(description.length()>1000) throw RbacException.invalid();
            description=description.strip();
        }
        if(displayNamePresent && displayName!=null) {
            if(displayName.isBlank() || displayName.length()>150)throw RbacException.invalid();
            displayName=displayName.strip();
        }
    }
}
