package com.pivotallabs.rspec;

import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType;

public class Util {
    public static boolean isRspecFile(PsiFile psiFile) {
        return psiFile != null
                && psiFile.getFileType() == RubyFileType.RUBY
                && psiFile.getName().contains("spec"); // or "shared_example"?
    }

    public static String htmlEscape(String s) {
        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }
}
