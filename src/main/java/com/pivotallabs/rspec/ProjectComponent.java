// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ProjectComponent.java

package com.pivotallabs.rspec;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType;

import java.util.regex.Pattern;

public class ProjectComponent extends AbstractProjectComponent {
    public static final Pattern ALL_WHITESPACE = Pattern.compile("^[\\s]+$");
    private PsiDocumentManager psiDocumentManager;
    private StatusConsole statusConsole;
    private RspecContextBuilder contextBuilder;

    protected ProjectComponent(Project project) {
        super(project);
    }

    public void initComponent() {
        contextBuilder = new RspecContextBuilder();
        psiDocumentManager = PsiDocumentManager.getInstance(myProject);
        final Application application = ApplicationManager.getApplication();
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        multicaster.addCaretListener(
                new CaretAdapter() {
                    public void caretPositionChanged(final CaretEvent e) {
                        application.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                caretMoved(e);
                            }
                        });
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
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        com.intellij.ui.content.Content content = contentFactory.createContent(statusConsole.getComponent(), "", true);
        toolWindow.getContentManager().addContent(content);

        PsiManagerEx.getInstance(myProject).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void beforeChildAddition(PsiTreeChangeEvent event) {
                contextBuilder.invalidateContainingScope(event.getParent());
            }

            @Override
            public void beforeChildRemoval(PsiTreeChangeEvent event) {
                contextBuilder.invalidateContainingScope(event.getParent());
            }

            @Override
            public void beforeChildReplacement(PsiTreeChangeEvent event) {
                contextBuilder.invalidateContainingScope(event.getParent());
            }

            @Override
            public void beforeChildMovement(PsiTreeChangeEvent event) {
                contextBuilder.invalidateContainingScope(event.getOldParent());
                contextBuilder.invalidateContainingScope(event.getNewParent());
            }

            @Override
            public void beforeChildrenChange(PsiTreeChangeEvent event) {
                contextBuilder.invalidateContainingScope(event.getParent());
            }
        });
    }

    private void caretMoved(CaretEvent e) {
        System.out.println((new StringBuilder()).append("Caret changed: ").append(e).toString());
        Editor editor = e.getEditor();
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        PsiFile psiFile = psiDocumentManager.getCachedPsiFile(editor.getDocument());
        if (psiFile != null && Util.isRspecFile(psiFile)) {
            CaretModel caretModel = e.getCaret().getCaretModel();
            int offset = caretModel.getOffset();
            PsiElement selection = psiFile.findElementAt(offset);
            if (selection == null) {
                return;
            }
            if (ALL_WHITESPACE.matcher(selection.getText()).matches()) {
                selection = psiFile.findElementAt(caretModel.getVisualLineEnd());
            }
            RspecContext rspecContext = contextBuilder.searchScope(selection);
            if (statusConsole != null) {
                statusConsole.showStuff(editor, rspecContext);
            }

            new ShowLetValue().showLetHintFor(editor, selection, rspecContext);
        }
    }
}
