package com.github.xian.rspec_awesome;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Util {
    public static final Set<String> LET_KEYWORDS
            = new HashSet<String>(Arrays.asList("let", "let!", "subject", "subject!"));
    public static final Set<String> CONTEXT_KEYWORDS
            = new HashSet<String>(Arrays.asList("describe", "context"));
    public static final Set<String> SPEC_KEYWORDS
            = new HashSet<String>(Arrays.asList("it", "feature", "specify", "scenario"));
    public static final Set<String> BEFORE_AFTER_KEYWORDS
            = new HashSet<String>(Arrays.asList("before", "after"));

    public static boolean isRspecFile(PsiFile psiFile) {
        return psiFile != null
                && psiFile.getFileType() == RubyFileType.RUBY
                && psiFile.getName().contains("spec"); // or "shared_example"?
    }

    public static String htmlEscape(String s) {
        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    public static boolean isContextBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return CONTEXT_KEYWORDS.contains(command);
    }

    public static boolean isSpecBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return SPEC_KEYWORDS.contains(command);
    }

    public static boolean isSpecOrContextBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return SPEC_KEYWORDS.contains(command) || CONTEXT_KEYWORDS.contains(command);
    }

    public static boolean isLetBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return LET_KEYWORDS.contains(command);
    }

    public static boolean isBeforeOrAfterBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return BEFORE_AFTER_KEYWORDS.contains(command);
    }

    public static RBlockCall findContainingContext(PsiElement selection) {
        RBlockCall blockCall = PsiTreeUtil.getParentOfType(selection, RBlockCall.class);
        while (blockCall != null && !isContextBlockCall(blockCall)) {
            blockCall = PsiTreeUtil.getParentOfType(blockCall, RBlockCall.class);
        }
        return blockCall;
    }

    public static RBlockCall findContainingSpecOrContext(PsiElement selection) {
        RBlockCall blockCall = PsiTreeUtil.getParentOfType(selection, RBlockCall.class);
        while (blockCall != null && !isSpecOrContextBlockCall(blockCall)) {
            blockCall = PsiTreeUtil.getParentOfType(blockCall, RBlockCall.class);
        }
        return blockCall;
    }
}
