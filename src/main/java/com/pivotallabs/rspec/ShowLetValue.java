package com.pivotallabs.rspec;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;

import javax.swing.*;

import static com.pivotallabs.rspec.Util.htmlEscape;

public class ShowLetValue extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null)
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        RspecContextBuilder contextBuilder = new RspecContextBuilder(editor);
        contextBuilder.searchScope(element);
        showLetHintFor(editor, element, contextBuilder);
    }

    public void showLetHintFor(Editor editor, PsiElement element, RspecContextBuilder contextBuilder) {
        String selectionText = element.getText();
        if (selectionText != null) {
            RspecContextBuilder.Let let = contextBuilder.getLet(selectionText);
            if (let != null) {
                showLetHint(editor, let);
            }
        }
    }

    public void showLetHint(Editor editor, RspecContextBuilder.Let let) {
        System.out.println(let.getName() + ": " + let.getValue());
        JLabel label = new JLabel(String.format("<html>%s(:%s) { <b>%s</b> }</html>",
                htmlEscape(let.getType()), htmlEscape(let.getName()), htmlEscape(let.getValue())));
        label.setFont(editor.getColorsScheme().getFont(EditorFontType.CONSOLE_PLAIN));
        if (editor.getComponent().isValid()) {
            HintManager.getInstance().showInformationHint(editor, label);
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        if (psiFile == null || editor == null)
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        IElementType elementType = element.getNode().getElementType();
        System.out.println("*** update: " + element.getText() + " (" + elementType + ")");
        if (elementType.getLanguage().isKindOf("ruby") && elementType.toString().equals("identifier")) {
            e.getPresentation().setEnabled(true);
        } else {
            e.getPresentation().setEnabled(false);
        }

    }
}
