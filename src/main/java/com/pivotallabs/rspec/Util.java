package com.pivotallabs.rspec;

public class Util {
    public static String htmlEscape(String s) {
        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }
}
