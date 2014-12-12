package com.github.xian.rspec_awesome;

class Let {
    private String type;
    private final String name;
    private final String value;
    private final int offset;

    public Let(String type, String name, String value, int offset) {
        this.type = type;
        this.name = name;
        this.value = value;
        this.offset = offset;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public int getOffset() {
        return offset;
    }
}
