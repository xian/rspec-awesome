package com.pivotallabs.rspec;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.ruby.codeInsight.codeFragment.RubyExtractMethodUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.ScopeHolder;
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.ScopeUtil;
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType;
import org.jetbrains.plugins.ruby.ruby.lang.RubyLanguage;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock;
import org.jetbrains.plugins.ruby.ruby.refactoring.common.RefactoringPsiHelper;
import org.jetbrains.plugins.ruby.ruby.refactoring.extractMethod.RubyExtractMethodHelper;

public class ExtractLetAction extends BaseRefactoringAction {
    @Override
    protected boolean isAvailableInEditorOnly() {
        return true;
    }

    @Override
    protected boolean isEnabledOnElements(PsiElement[] elements) {
        return true;
    }

    @Override
    protected boolean isEnabledOnDataContext(DataContext dataContext) {
        return true;
    }

    @Override
    protected boolean isAvailableForLanguage(Language language) {
        return language.isKindOf(RubyLanguage.INSTANCE);
    }

    @Nullable
    @Override
    protected RefactoringActionHandler getHandler(DataContext dataContext) {
        return new RefactoringActionHandler() {
            @Override
            public void invoke(final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
                CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                    @Override
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                doExtractLet(project, editor, file, dataContext);
                            }
                        });
                    }
                }, "Extract Let", null);
            }

            @Override
            public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
            }
        };
    }

    private void doExtractLet(Project project, Editor editor, PsiFile psiFile, DataContext dataContext) {
        if (psiFile == null || editor == null)
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        RspecContextBuilder contextBuilder = new RspecContextBuilder();
        RBlockCall containingScope = contextBuilder.findContainingScope(element);
        RCodeBlock block = containingScope.getBlock();
        RBlockCall lastLetBlockCall = null;
        RCompoundStatement blockContents = block.getCompoundStatement();
        for (PsiElement child : blockContents.getChildren()) {
            if (child instanceof RBlockCall && contextBuilder.isLetBlockCall((RBlockCall) child)) {
                lastLetBlockCall = (RBlockCall) child;
            }
        }

        extractMethod(project, editor, psiFile, null);

        ExtractMethodHandler.selectAndPass(project, editor, psiFile, new Pass<PsiElement[]>() {
            @Override
            public void pass(PsiElement[] psiElements) {
                System.out.println(psiElements);
            }
        });
        SelectionModel selectionModel = editor.getSelectionModel();
        String letText = "\nlet(:foo) { " + selectionModel.getSelectedText() + " }";
        PsiFile newPsiFile = PsiFileFactory.getInstance(project).createFileFromText("temp.rb", RubyFileType.RUBY, letText);
        PsiElement newPsiElement = newPsiFile.getOriginalElement().copy();
        if (lastLetBlockCall == null) {
            if (blockContents.getChildren().length == 0) {
                blockContents.add(newPsiElement);
            } else {
                blockContents.addBefore(newPsiElement, blockContents.getFirstChild());
            }
        } else {
            blockContents.addAfter(newPsiElement, lastLetBlockCall);
        }
    }


    static final String DIALOG_TITLE = "Extract Let";

    public static void extractMethod(@NotNull Project project, Editor editor, PsiFile file, @Nullable String methodName) {
        CommonRefactoringUtil.checkReadOnlyStatus(project, file);
        Pair selected = RefactoringPsiHelper.getSelectedElements(editor, file);
        PsiElement element1 = (PsiElement)selected.first;
        PsiElement element2 = (PsiElement)selected.second;
        if(element1 != null && element2 != null && RubyPsiUtil.getInstance().isBefore(element1, element2)) {
            Pair var11 = getStatements(element1, element2);
            if(var11 != null) {
                ScopeHolder var12 = ScopeUtil.findScopeHolder((PsiElement) var11.first);

                assert var12 != null;

                Object var14 = RubyExtractMethodUtil.createCodeFragment(var12, element1, element2);
                if(var14 instanceof String) {
                    showError(project, editor, var14);
                } else {
//                    RubyExtractMethodHelper.handleExtractFromStatements(project, editor, methodName, (CodeFragment) var14, (RPsiElement) var11.first, (RPsiElement) var11.second);
                }
            } else {
                Object expression = RefactoringPsiHelper.getSelectedExpression(project, file, element1, element2, false, "refactoring.extractMethod");
                if(expression != null && (!(expression instanceof String) || !StringUtil.isEmpty((String) expression))) {
                    if(expression instanceof String) {
                        showError(project, editor, expression);
                    } else {
                        ScopeHolder var13 = ScopeUtil.findScopeHolder(element1);

                        assert var13 != null;

                        Object fragment = RubyExtractMethodUtil.createCodeFragment(var13, element1, element2);
                        if(fragment instanceof String) {
                            showError(project, editor, fragment);
                        } else {
//                            RubyExtractMethodHelper.handleExtractFromExpression(project, editor, methodName, (CodeFragment)fragment, (RPsiElement)expression);
                        }
                    }
                } else {
                    String owner = RBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.using.selected.elements");
                    CommonRefactoringUtil.showErrorHint(project, editor, owner, DIALOG_TITLE, "refactoring.extractMethod");
                }
            }
        } else {
            String statements = RBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.using.selected.elements");
            CommonRefactoringUtil.showErrorHint(project, editor, statements, DIALOG_TITLE, "refactoring.extractMethod");
        }
    }

    @Nullable
    private static Pair<RPsiElement, RPsiElement> getStatements(PsiElement element1, PsiElement element2) {
        PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
        RPsiElement commonParent = parent != null?RubyPsiUtil.getInstance().getCoveringRPsiElement(parent):null;
        if(commonParent == null) {
            return null;
        } else {
            RCompoundStatement compoundStatement;
            if(commonParent instanceof RCompoundStatement) {
                compoundStatement = (RCompoundStatement)commonParent;
            } else if(commonParent.getParent() instanceof RCompoundStatement) {
                compoundStatement = (RCompoundStatement)commonParent.getParent();
            } else {
                compoundStatement = null;
            }

            if(compoundStatement == null) {
                return null;
            } else {
                RPsiElement statement1 = RubyPsiUtil.getInstance().getStatement(compoundStatement, element1);
                RPsiElement statement2 = RubyPsiUtil.getInstance().getStatement(compoundStatement, element2);
                return statement1 != null && statement2 != null?(element1 == PsiTreeUtil.getDeepestFirst(statement1) && element2 == PsiTreeUtil.getDeepestLast(statement2)?Pair.create(statement1, statement2):null):null;
            }
        }
    }

    private static void showError(Project project, Editor editor, Object fragment) {
        CommonRefactoringUtil.showErrorHint(project, editor, (String)fragment, DIALOG_TITLE, "refactoring.extractMethod");
    }
}
