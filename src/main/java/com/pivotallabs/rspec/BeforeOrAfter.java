package com.pivotallabs.rspec;

class BeforeOrAfter {
    private String type;
    private final String text;
    private final int offset;

    public BeforeOrAfter(String type, String text, int offset) {
        this.type = type;
        this.text = text;
        this.offset = offset;
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getOffset() {
        return offset;
    }
}
