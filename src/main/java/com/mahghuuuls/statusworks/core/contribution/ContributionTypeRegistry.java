package com.mahghuuuls.statusworks.core.contribution;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.contribution.ContributionType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Addon contribution types by id. Types register during startup and freeze with the definitions. */
public final class ContributionTypeRegistry {

    private static final Pattern ID = Pattern.compile("\\S+");

    private final Map<String, ContributionType> types = new LinkedHashMap<>();
    private final Map<String, String> origins = new LinkedHashMap<>();
    private boolean frozen;

    public synchronized RegistrationResult register(@Nullable ContributionType type, String origin) {
        if (frozen) {
            return RegistrationResult.refused("definitions are frozen; contribution type from " + origin
                    + " was registered after game startup");
        }
        if (type == null || type.id() == null || !ID.matcher(type.id()).matches()) {
            return RegistrationResult.refused("contribution type from " + origin + " has no valid id");
        }
        if (types.containsKey(type.id())) {
            return RegistrationResult.refused("contribution type " + type.id() + " is already registered by "
                    + origins.get(type.id()));
        }
        types.put(type.id(), type);
        origins.put(type.id(), origin);
        return RegistrationResult.accepted();
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean isKnown(String id) {
        return types.containsKey(id);
    }

    @Nullable
    public synchronized ContributionType type(String id) {
        return types.get(id);
    }

    public synchronized Collection<String> ids() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(types.keySet()));
    }

    @Nullable
    public synchronized String origin(String id) {
        return origins.get(id);
    }
}
