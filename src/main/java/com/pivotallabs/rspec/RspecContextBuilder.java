package com.pivotallabs.rspec;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RListOfExpressions;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;

import java.util.*;

class RspecContextBuilder {
    public static final Key<RspecContextFrame> USER_DATA_KEY = Key.create(RspecContextFrame.class.getName());

    RspecContext searchScope(PsiElement selection) {
        RBlockCall enclosingBlockCall = Util.findContainingContext(selection);

        List<RspecContextFrame> rspecContextFrames = new ArrayList<RspecContextFrame>();
        searchScopeAndAscend(enclosingBlockCall, rspecContextFrames);
        return new RspecContext(rspecContextFrames);
    }

    private void searchScopeAndAscend(RBlockCall contextBlockCall, List<RspecContextFrame> rspecContextFrames) {
        if (contextBlockCall == null) {
            return;
        }

        RCompoundStatement block = contextBlockCall.getBlock().getCompoundStatement();

        RspecContextFrame rspecContextFrame = contextBlockCall.getUserData(USER_DATA_KEY);
        if (rspecContextFrame == null) {
            rspecContextFrame = new RspecContextFrame();
            contextBlockCall.putUserData(USER_DATA_KEY, rspecContextFrame);

            fillContextFrame(contextBlockCall, rspecContextFrames, block, rspecContextFrame);
        }

        rspecContextFrames.add(rspecContextFrame);

        RBlockCall parentBlockColl = Util.findContainingSpecOrContext(contextBlockCall);
        searchScopeAndAscend(parentBlockColl, rspecContextFrames);
    }

    private void fillContextFrame(RBlockCall contextBlockCall, List<RspecContextFrame> rspecContextFrames, RCompoundStatement block, RspecContextFrame rspecContextFrame) {
        try {
            for (RBlockCall blockCall : PsiTreeUtil.getChildrenOfTypeAsList(block, RBlockCall.class)) {
                if (Util.isLetBlockCall(blockCall)) {
                    processLetOrSubject(blockCall, rspecContextFrame);
                } else if (Util.isBeforeOrAfterBlockCall(blockCall)) {
                    processBeforeOrAfter(blockCall, rspecContextFrame);
                }
            }

            if (Util.isContextBlockCall(contextBlockCall)) {
                processContext(contextBlockCall, rspecContextFrame);
            }
        } catch (AssertionError e) {
            e.printStackTrace();
        }
    }

    void processLetOrSubject(RBlockCall el, RspecContextFrame rspecContextFrame) {
        RPossibleCall call = el.getCall();
        String type = call.getPossibleCommand();
        PsiElement children[] = call.getChildren();
        String name = null;
        if (children.length >= 2)
            name = stringFrom(children[1]);
        if (name == null)
            name = "<unknown>";
        String block = codeFromBlock(el.getChildren()[1]);
        rspecContextFrame.putLet(new Let(type, name, block, el.getTextOffset()));
    }

    private String codeFromBlock(PsiElement psiElement) {
        if (psiElement instanceof RCodeBlock) {
            RCodeBlock codeBlock = (RCodeBlock) psiElement;
            return codeBlock.getCompoundStatement().getText();
        } else {
            return psiElement.getText().trim();
        }
    }

    void processBeforeOrAfter(RBlockCall el, RspecContextFrame rspecContextFrame) {
        String type = el.getPossibleCommand();
        rspecContextFrame.addBeforeOrAfter(new BeforeOrAfter(type, codeFromBlock(el.getChildren()[1]), el.getTextOffset()));
    }

    void processContext(RBlockCall el, RspecContextFrame rspecContextFrame) {
        RPossibleCall possibleCall = el.getCall();

        if (possibleCall instanceof RCall) {
            RCall call = (RCall) possibleCall;
            String type = call.getCommand();

            List<RPsiElement> arguments = call.getArguments();
            String name = "<unknown>";
            if (arguments.size() >= 1) {
                RPsiElement arg = arguments.get(0);
                if (arg instanceof RStringLiteral) {
                    name = ((RStringLiteral) arg).getContent();
                } else {
                    name = arg.getText();
                }
            }
            rspecContextFrame.setDescription(new Part(name, el.getTextOffset(), type));
        }
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

    public void invalidateContainingScope(PsiElement psiElement) {
        if (psiElement == null || !Util.isRspecFile(psiElement.getContainingFile())) {
            return;
        }

        RBlockCall enclosingBlockCall = Util.findContainingContext(psiElement);
        if (enclosingBlockCall != null) {
            enclosingBlockCall.putUserData(USER_DATA_KEY, null);
        }
    }
}
