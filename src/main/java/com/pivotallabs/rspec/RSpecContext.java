package com.pivotallabs.rspec;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

class RspecContext {
    private final List<Part> descriptions = new ArrayList<Part>();
    private final TreeMap<String,Let> lets = new TreeMap<String, Let>();
    private final List<BeforeOrAfter> beforeOrAfters = new ArrayList<BeforeOrAfter>();

    public RspecContext(List<RspecContextFrame> rspecContextFrames) {
        for (RspecContextFrame rspecContextFrame : Lists.reverse(rspecContextFrames)) {
            Part description = rspecContextFrame.getDescription();
            if (description != null) {
                descriptions.add(description);
            }
            lets.putAll(rspecContextFrame.getLets());
            beforeOrAfters.addAll(rspecContextFrame.getBeforeOrAfters());
        }
    }

    public Collection<Let> getLets() {
        return lets.values();
    }

    public List<Part> getDescriptions() {
        return descriptions;
    }

    public List<BeforeOrAfter> getBeforeOrAfters() {
        return beforeOrAfters;
    }

    public Let getLet(String name) {
        return lets.get(name);
    }
}
