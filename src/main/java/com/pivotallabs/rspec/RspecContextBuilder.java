package com.pivotallabs.rspec;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RListOfExpressions;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock;

import java.util.*;

class RspecContextBuilder {
    public static final Key<RspecContextBuilder> USER_DATA_KEY = Key.create(RspecContextBuilder.class.getName() + "$Context");
    public final Set<String> LET_KEYWORDS
            = new HashSet<String>(Arrays.asList("let", "let!", "subject", "subject!"));
    public final Set<String> CONTEXT_KEYWORDS
            = new HashSet<String>(Arrays.asList("describe", "context", "it", "feature", "specify", "scenario"));
    public final Set<String> BEFORE_AFTER_KEYWORDS
            = new HashSet<String>(Arrays.asList("before", "after"));

    private Editor editor;
    private List description;
    private Map<String, Let> lets;
    private List<BeforeOrAfter> beforeOrAfters;

    public RspecContextBuilder(Editor editor) {
        description = new ArrayList();
        lets = new TreeMap<String, Let>();
        beforeOrAfters = new ArrayList<BeforeOrAfter>();
        this.editor = editor;
    }

    PsiElement enclosingBlock(PsiElement selection) {
        RBlockCall enclosingBlockCall = PsiTreeUtil.getParentOfType(selection, RBlockCall.class);
        if (enclosingBlockCall != null) {
            return enclosingBlockCall.getBlock().getCompoundStatement();
        } else {
            return null;
        }
    }

    void searchScope(PsiElement selection) {
        selection = enclosingBlock(selection);
        searchScopeAndAscend(selection);
    }

    private void searchScopeAndAscend(PsiElement selection) {
        if (selection == null) {
            return;
        }

        RspecContextBuilder userData = selection.getUserData(USER_DATA_KEY);
        if (userData == null) {
            System.out.println(("haven't seen yet: " + selection.getText()).substring(0, 10));
            selection.putUserData(USER_DATA_KEY, this);
        } else {
            System.out.println(("Saw before: " + selection.getText()).substring(0, 10));
        }

        try {
            for (RBlockCall blockCall : PsiTreeUtil.getChildrenOfTypeAsList(selection, RBlockCall.class)) {
                RPossibleCall call = blockCall.getCall();
                String command = call.getPossibleCommand();
                if (LET_KEYWORDS.contains(command)) {
                    processLetOrSubject(blockCall);
                    continue;
                }
                if (BEFORE_AFTER_KEYWORDS.contains(command))
                    processBeforeOrAfter(blockCall);
            }

            if (selection instanceof RBlockCall) {
                RBlockCall blockCall = (RBlockCall) selection;
                RPossibleCall call = blockCall.getCall();
                String command = call.getPossibleCommand();
                if (CONTEXT_KEYWORDS.contains(command))
                    processContext(blockCall);
            }
            searchScopeAndAscend(selection.getParent());
        } catch (AssertionError e) {
            e.printStackTrace();
        }
    }

    void processLetOrSubject(RBlockCall el) {
        RPossibleCall call = el.getCall();
        String type = call.getPossibleCommand();
        PsiElement children[] = call.getChildren();
        String name = null;
        if (children.length >= 2)
            name = stringFrom(children[1]);
        if (name == null)
            name = "<unknown>";
        String block = codeFromBlock(el.getChildren()[1]);
        if (lets.get(name) == null)
            lets.put(name, new Let(type, name, block, el.getTextOffset()));
    }

    private String codeFromBlock(PsiElement psiElement) {
        if (psiElement instanceof RCodeBlock) {
            RCodeBlock codeBlock = (RCodeBlock) psiElement;
            return codeBlock.getCompoundStatement().getText();
        } else {
            return psiElement.getText().trim();
        }
    }

    void processBeforeOrAfter(RBlockCall el) {
        String type = el.getPossibleCommand();
        beforeOrAfters.add(new BeforeOrAfter(type, codeFromBlock(el.getChildren()[1]), el.getTextOffset()));
    }

    void processContext(RBlockCall el) {
        RPossibleCall call = el.getCall();
        String type = call.getPossibleCommand();
        PsiElement children[] = call.getChildren();
        String name = null;
        if (children.length >= 2)
            name = stringFrom(children[1]);
        if (name == null)
            name = "<unknown>";
        description.add(0, new Part(name, el.getTextOffset(), type));
    }

    private String stringFrom(PsiElement arg) {
        if (arg instanceof RListOfExpressions) {
            RListOfExpressions expressions = (RListOfExpressions) arg;
            RPsiElement element = expressions.getElement(0);
            if (element != null) {
                if (element instanceof RStringLiteral) {
                    RStringLiteral stringLiteral = (RStringLiteral) element;
                    return stringLiteral.getPsiContent().get(0).getText();
                }
                if (element instanceof RSymbol)
                    return ((RSymbol) element).getValue();
            }
        }
        return null;
    }

    public void showStuff(StatusConsole statusConsole) {
        statusConsole.clear();
        Let let;
        for (Iterator i$ = lets.values().iterator(); i$.hasNext(); statusConsole.addItem(new StatusConsole.LetListItem(let, editor)))
            let = (Let) i$.next();

        statusConsole.addItem(new StatusConsole.DividerItem());
        BeforeOrAfter beforeOrAfter;
        for (Iterator i$ = beforeOrAfters.iterator(); i$.hasNext(); statusConsole.addItem(new StatusConsole.BeforeItem(editor, beforeOrAfter)))
            beforeOrAfter = (BeforeOrAfter) i$.next();

        statusConsole.addItem(new StatusConsole.DividerItem());
        for (int i = 0; i < description.size(); i++) {
            Part part = (Part) description.get(i);
            boolean last = i == description.size() - 1;
            int offset = last ? editor.getCaretModel().getOffset() : part.getOffset();
            statusConsole.addItem(new StatusConsole.ContextItem(editor, part, offset, i, last));
        }
    }

    public Let getLet(String name) {
        return lets.get(name);
    }

}
