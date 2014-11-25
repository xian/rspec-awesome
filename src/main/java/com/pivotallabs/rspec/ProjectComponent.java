// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ProjectComponent.java

package com.pivotallabs.rspec;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;

import java.util.regex.Pattern;

public class ProjectComponent extends AbstractProjectComponent {
    public static final Pattern ALL_WHITESPACE = Pattern.compile("^[\\s]+$");
    private PsiDocumentManager psiDocumentManager;
    private StatusConsole statusConsole;

    protected ProjectComponent(Project project) {
        super(project);
    }

    public void initComponent() {
        psiDocumentManager = PsiDocumentManager.getInstance(myProject);
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        multicaster.addCaretListener(
                new CaretAdapter() {
                    public void caretPositionChanged(CaretEvent e) {
                        caretMoved(e);
                    }
                }
        );
        multicaster.addDocumentListener(
                new DocumentAdapter() {
                    public void documentChanged(DocumentEvent e) {
                        System.out.println((new StringBuilder()).append("Document changed: ").append(e).toString());
                    }
                }
        );
    }

    public void projectOpened() {
        ToolWindowManager twm = ToolWindowManagerEx.getInstance(myProject);
        ToolWindow toolWindow = twm.registerToolWindow("RSpec Context", false, ToolWindowAnchor.BOTTOM, myProject);
        statusConsole = new StatusConsole();
        com.intellij.ui.content.Content content = com.intellij.ui.content.ContentFactory.SERVICE.getInstance().createContent(statusConsole.getComponent(), "", true);
        toolWindow.getContentManager().addContent(content);
    }

    private void caretMoved(CaretEvent e) {
        System.out.println((new StringBuilder()).append("Caret changed: ").append(e).toString());
        Editor editor = e.getEditor();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        PsiFile psiFile = psiDocumentManager.getCachedPsiFile(editor.getDocument());
        if (psiFile != null && isRspec(psiFile)) {
            CaretModel caretModel = e.getCaret().getCaretModel();
            int offset = caretModel.getOffset();
            PsiElement selection = psiFile.findElementAt(offset);
            if (ALL_WHITESPACE.matcher(selection.getText()).matches())
                selection = psiFile.findElementAt(caretModel.getVisualLineEnd());
            RspecContextBuilder contextBuilder = new RspecContextBuilder(editor);
            contextBuilder.searchScope(selection);
            if (statusConsole != null)
                contextBuilder.showStuff(statusConsole);

            new ShowLetValue().showLetHintFor(editor, selection, contextBuilder);
        }
    }

    private boolean isRspec(PsiFile psiFile) {
        return psiFile.getName().contains("spec"); // or "shared_example"
    }
}
