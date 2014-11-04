// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   ProjectComponent.java

package com.pivotallabs.rspec;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RListOfExpressions;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.stubs.types.RBlockCallStubElementType;

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
        PsiFile psiFile = psiDocumentManager.getCachedPsiFile(editor.getDocument());
        if (psiFile != null && psiFile.getName().contains("spec")) {
            CaretModel caretModel = e.getCaret().getCaretModel();
            int offset = caretModel.getOffset();
            PsiElement selection = psiFile.findElementAt(offset);
            if (ALL_WHITESPACE.matcher(selection.getText()).matches())
                selection = psiFile.findElementAt(caretModel.getVisualLineEnd());
            ContextBuilder contextBuilder = new ContextBuilder(editor);
            contextBuilder.searchScope(selection);
            if (statusConsole != null)
                contextBuilder.showStuff(statusConsole);
        }
    }

    class ContextBuilder {
        public final Set<String> LET_KEYWORDS
                = new HashSet<String>(Arrays.asList("let", "let!", "subject", "subject!"));
        public final Set<String> CONTEXT_KEYWORDS
                = new HashSet<String>(Arrays.asList("describe", "context", "it", "feature", "specify", "scenario"));
        public final Set<String> BEFORE_AFTER_KEYWORDS
                = new HashSet<String>(Arrays.asList("before", "after"));

        private Editor editor;
        private List description;
        private Map lets;
        private List beforeOrAfters;

        public ContextBuilder(Editor editor) {
            description = new ArrayList();
            lets = new TreeMap();
            beforeOrAfters = new ArrayList();
            this.editor = editor;
        }

        boolean isBlock(PsiElement psiElement) {
            ASTNode astNode = psiElement.getNode();
            if (astNode == null)
                return false;
            IElementType elementType = astNode.getElementType();
            return elementType instanceof RBlockCallStubElementType;
        }

        PsiElement blockOf(PsiElement psiElement) {
            PsiElement children[] = psiElement.getChildren();
            psiElement = children[children.length - 1];
            PsiElement otherChildren[] = psiElement.getChildren();
            return otherChildren[otherChildren.length - 1];
        }

        PsiElement enclosingBlock(PsiElement selection) {
            PsiElement original = selection;
            for (; selection != null && !isBlock(selection); selection = selection.getParent()) ;
            if (selection != null)
                selection = blockOf(selection);
            return selection != null ? selection : original;
        }

        void searchScope(PsiElement selection) {
            selection = enclosingBlock(selection);
            searchScopeAndAscend(selection);
        }

        void searchScopeAndAscend(PsiElement selection) {
            if (selection == null)
                return;
            PsiElement arr$[] = selection.getChildren();
            int len$ = arr$.length;
            for (int i$ = 0; i$ < len$; i$++) {
                PsiElement psiElement = arr$[i$];
                if (!(psiElement instanceof RBlockCall))
                    continue;
                RBlockCall blockCall = (RBlockCall) psiElement;
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
                        return ((PsiElement) stringLiteral.getPsiContent().get(0)).getText();
                    }
                    if (element instanceof RSymbol)
                        return ((RSymbol) element).getValue();
                }
            }
            return null;
        }

        public void showStuff() {
            System.out.println();
            System.out.println("==========================================");
            System.out.println();
            System.out.print("Description:");
            Part part;
            for (Iterator i$ = description.iterator(); i$.hasNext(); System.out.print(part.text)) {
                part = (Part) i$.next();
                System.out.print(" ");
            }

            System.out.println();
            Let let;
            for (Iterator i$ = lets.values().iterator(); i$.hasNext(); System.out.println((new StringBuilder()).append(let.name).append(": ").append(let.value).toString()))
                let = (Let) i$.next();

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
                int offset = last ? editor.getCaretModel().getOffset() : part.offset;
                statusConsole.addItem(new StatusConsole.ContextItem(editor, part, offset, i, last));
            }

        }

        class BeforeOrAfter {

            public String getType() {
                return type;
            }

            public String getText() {
                return text;
            }

            public int getOffset() {
                return offset;
            }

            private String type;
            private final String text;
            private final int offset;

            public BeforeOrAfter(String type, String text, int offset) {
                this.type = type;
                this.text = text;
                this.offset = offset;
            }
        }

        class Part {

            public String getText() {
                return text;
            }

            public int getOffset() {
                return offset;
            }

            public String getType() {
                return type;
            }

            private final String text;
            private final int offset;
            private final String type;


            public Part(String text, int offset, String type) {
                this.text = text;
                this.offset = offset;
                this.type = type;
            }
        }

        class Let {

            public String getType() {
                return type;
            }

            public String getName() {
                return name;
            }

            public String getValue() {
                return value;
            }

            public int getOffset() {
                return offset;
            }

            private String type;
            private final String name;
            private final String value;
            private final int offset;

            public Let(String type, String name, String value, int offset) {
                this.type = type;
                this.name = name;
                this.value = value;
                this.offset = offset;
            }
        }
    }
}
