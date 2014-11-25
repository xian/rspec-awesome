package com.pivotallabs.rspec;

class Part {
    private final String text;
    private final int offset;
    private final String type;

    public Part(String text, int offset, String type) {
        this.text = text;
        this.offset = offset;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public int getOffset() {
        return offset;
    }

    public String getType() {
        return type;
    }
}
