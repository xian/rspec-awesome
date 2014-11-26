package com.pivotallabs.rspec;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.LightweightHint;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;

import javax.swing.*;

import java.awt.*;

import static com.pivotallabs.rspec.Util.htmlEscape;

public class ShowLetValueAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null)
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        RspecContextBuilder contextBuilder = new RspecContextBuilder();
        RspecContext rspecContext = contextBuilder.searchScope(element);
        showLetHintFor(editor, element, rspecContext);
    }

    public void showLetHintFor(Editor editor, PsiElement element, RspecContext rspecContext) {
        String selectionText = element.getText();
        if (selectionText != null) {
            Let let = rspecContext.getLet(selectionText);
            if (let != null) {
                try {
                    PsiElement symbol = element.getParent().getParent();
                    if (symbol instanceof RSymbol) {
                        PsiElement letBlockCall = symbol.getParent().getParent().getParent();
                        if (letBlockCall instanceof RBlockCall && Util.isLetBlockCall((RBlockCall) letBlockCall)) {
                            return;
                        }
                    }
                } catch (NullPointerException e) {
                    // ignore
                }
                showLetHint(editor, let);
            }
        }
    }

    public void showLetHint(Editor editor, Let let) {
        JLabel label = new JLabel(String.format("<html>%s(:%s) { <b>%s</b> }</html>",
                htmlEscape(let.getType()), htmlEscape(let.getName()), htmlEscape(let.getValue())));
        label.setFont(editor.getColorsScheme().getFont(EditorFontType.CONSOLE_PLAIN));
        if (editor.getComponent().isValid()) {
            HintManagerImpl hintManager = (HintManagerImpl) HintManager.getInstance();
            hintManager.showEditorHint(new LightweightHint(label), editor, HintManager.UNDER, 0, 0, true);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null || !Util.isRspecFile(psiFile))
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        IElementType elementType = element.getNode().getElementType();
        if (elementType.getLanguage().isKindOf("ruby") && elementType.toString().equals("identifier")) {
            e.getPresentation().setEnabled(true);
        } else {
            e.getPresentation().setEnabled(false);
        }

    }
}
