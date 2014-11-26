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
    public final Set<String> LET_KEYWORDS
            = new HashSet<String>(Arrays.asList("let", "let!", "subject", "subject!"));
    public final Set<String> SCOPE_KEYWORDS
            = new HashSet<String>(Arrays.asList("describe", "context", "it", "feature", "specify", "scenario"));
    public final Set<String> BEFORE_AFTER_KEYWORDS
            = new HashSet<String>(Arrays.asList("before", "after"));

    RspecContext searchScope(PsiElement selection) {
        RBlockCall enclosingBlockCall = findContainingScope(selection);

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
            System.out.println(String.format("Created new RspecContextFrame for %.30s", contextBlockCall.getText().replaceAll("\n", " ")));

            fillContextFrame(contextBlockCall, rspecContextFrames, block, rspecContextFrame);
        }

        rspecContextFrames.add(rspecContextFrame);

        RBlockCall parentBlockColl = findContainingScope(contextBlockCall);
        searchScopeAndAscend(parentBlockColl, rspecContextFrames);
    }

    private void fillContextFrame(RBlockCall contextBlockCall, List<RspecContextFrame> rspecContextFrames, RCompoundStatement block, RspecContextFrame rspecContextFrame) {
        try {
            for (RBlockCall blockCall : PsiTreeUtil.getChildrenOfTypeAsList(block, RBlockCall.class)) {
                RPossibleCall call = blockCall.getCall();
                String command = call.getPossibleCommand();
                if (LET_KEYWORDS.contains(command)) {
                    processLetOrSubject(blockCall, rspecContextFrame);
                    continue;
                }
                if (BEFORE_AFTER_KEYWORDS.contains(command)) {
                    processBeforeOrAfter(blockCall, rspecContextFrame);
                }
            }

            if (isScopeBlockCall(contextBlockCall)) {
                processContext(contextBlockCall, rspecContextFrame);
            }
        } catch (AssertionError e) {
            e.printStackTrace();
        }
    }

    private boolean isScopeBlockCall(RBlockCall contextBlockCall) {
        RPossibleCall call = contextBlockCall.getCall();
        String command = call.getPossibleCommand();
        return SCOPE_KEYWORDS.contains(command);
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

    private RBlockCall findContainingScope(PsiElement selection) {
        RBlockCall blockCall = PsiTreeUtil.getParentOfType(selection, RBlockCall.class);
        while (blockCall != null && !isScopeBlockCall(blockCall)) {
            System.out.println("Skipping non-context: " + blockCall.getText());
            blockCall = PsiTreeUtil.getParentOfType(blockCall, RBlockCall.class);
        }
        return blockCall;
    }

    public void invalidateContainingScope(PsiElement psiElement) {
        if (psiElement == null || !Util.isRspecFile(psiElement.getContainingFile())) {
            return;
        }

        RBlockCall enclosingBlockCall = findContainingScope(psiElement);
        if (enclosingBlockCall != null) {
            RspecContextFrame rspecContextFrame = enclosingBlockCall.getUserData(USER_DATA_KEY);
            if (rspecContextFrame != null) {
                Part description = rspecContextFrame.getDescription();
                System.out.println("Dropping cached context for " + (description == null ? "????" : description.getText()));
            }

            enclosingBlockCall.putUserData(USER_DATA_KEY, null);
        }
    }
}
