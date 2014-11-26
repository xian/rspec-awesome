package com.pivotallabs.rspec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RspecContextFrame {
    private Part description;
    private Map<String, Let> lets;
    private List<BeforeOrAfter> beforeOrAfters;

    public RspecContextFrame() {
        this.description = null;
        this.lets = new HashMap<String, Let>();
        this.beforeOrAfters = new ArrayList<BeforeOrAfter>();
    }

    public Part getDescription() {
        return description;
    }

    public void setDescription(Part description) {
        this.description = description;
    }

    public void putLet(Let let) {
        lets.put(let.getName(), let);
    }

    public Map<String, Let> getLets() {
        return lets;
    }

    public void addBeforeOrAfter(BeforeOrAfter beforeOrAfter) {
        beforeOrAfters.add(beforeOrAfter);
    }

    public List<BeforeOrAfter> getBeforeOrAfters() {
        return beforeOrAfters;
    }
}
