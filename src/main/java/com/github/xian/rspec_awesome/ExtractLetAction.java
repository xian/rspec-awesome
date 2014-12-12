package com.github.xian.rspec_awesome;

import com.intellij.lang.Language;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.IntroduceVariableAction;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
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
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyElementFactory;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RExpressionSubstitution;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.RModifierStatement;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RListOfExpressions;
import org.jetbrains.plugins.ruby.ruby.lang.psi.impl.RubyLanguageLevelPusher;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RBlockCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock;
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier;
import org.jetbrains.plugins.ruby.ruby.refactoring.common.RefactoringPsiHelper;
import org.jetbrains.plugins.ruby.ruby.refactoring.introduce.IntroduceValidator;
import org.jetbrains.plugins.ruby.ruby.refactoring.introduce.variable.RubyIntroduceVariableHandler;
import org.jetbrains.plugins.ruby.ruby.sdk.LanguageLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class ExtractLetAction extends IntroduceVariableAction {
    private static final Logger LOG = Logger.getInstance(ExtractLetAction.class);

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

    @Override
    protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider) {
        return new RefactoringHandler();
    }

    @Nullable
    @Override
    protected RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider, PsiElement element) {
        return new RefactoringHandler();
    }

    private class RefactoringHandler extends RubyIntroduceVariableHandler {
        private RBlockCall containingScope;
        private String startingName;

        @Override
        protected PsiElement insertDeclaration(String name, PsiElement declaration, PsiElement expression, List<PsiElement> occurrences, boolean replaceAll) {
            PsiElement placeForVarDeclaration = getPlaceForDeclaration(expression, occurrences, replaceAll);
            containingScope = Util.findContainingContext(placeForVarDeclaration);
            RBlockCall lastLetBlockCall = findLastLet(containingScope);
            if (lastLetBlockCall != null) {
                return containingScope.getBlock().getCompoundStatement().addAfter(declaration, lastLetBlockCall);
            } else {
                return containingScope.getBlock().getCompoundStatement().addBefore(declaration, containingScope);
            }
        }

        // cribbed from RubyIntroduceHandlerBase
        protected void performActionOnElement(final Project project, final Editor editor, PsiElement element1, final RPsiElement expression, String name, boolean replaceAll) {
            final List<PsiElement> occurrences = findOccurrences(expression);
            if (supportsInplace() && editor.getSettings().isVariableInplaceRenameEnabled() && !ApplicationManager.getApplication().isUnitTestMode()) {
                IntroduceValidator nameAndReplaceChoice1 = createValidator(element1, occurrences);
                final List names = getSuggestedNames(expression, nameAndReplaceChoice1);
                OccurrencesChooser.simpleChooser(editor).showChooser(element1, occurrences, new Pass<OccurrencesChooser.ReplaceChoice>() {
                    public void pass(OccurrencesChooser.ReplaceChoice choice) {
                        boolean replaceAll = choice == OccurrencesChooser.ReplaceChoice.ALL;
                        startingName = names.isEmpty() ? "var" : (String) names.get(0);
                        final PsiElement element = performIntroduce(project, expression, startingName, occurrences, replaceAll);
                        final RPsiElement scope = Util.findContainingContext(element);
                        final PsiElement[] arr = (PsiElement[]) occurrences.toArray(new PsiElement[occurrences.size()]);

                        final PsiNamedElement target = (RSymbol) ((RCall) ((RBlockCall) element).getCall()).getArguments().get(0);
//                        final PsiNamedElement target = (PsiNamedElement)((RAssignmentExpression)element).getObject();
                        editor.getCaretModel().moveToOffset(target.getTextOffset() + 1);
                        InplaceVariableIntroducer introducer = new InplaceVariableIntroducer<PsiElement>(target, editor, project, getTitle(), arr, expression) {
                            protected PsiElement checkLocalScope() {
                                return scope;
                            }

                            @Override
                            protected SearchScope getReferencesSearchScope(VirtualFile file) {
                                PsiFile currentFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
                                return currentFile != null ? new LocalSearchScope(scope)
                                        : ProjectScope.getProjectScope(myProject);
                            }

                            @Override
                            protected Collection<PsiReference> collectRefs(SearchScope referencesSearchScope) {
                                final ArrayList<PsiReference> references = new ArrayList<PsiReference>();
                                PsiTreeUtil.processElements(containingScope, new PsiElementProcessor() {
                                    @Override
                                    public boolean execute(@NotNull PsiElement element) {
//                                        System.out.println("* " + element.getClass().getName() + ": " + element.getText().replaceAll("\n", "\\\\n"));
                                        if (element instanceof RIdentifier) {
                                            RIdentifier rIdentifier = (RIdentifier) element;
                                            System.out.println("**** " + rIdentifier.getText());
                                            if (rIdentifier.getText().equals(startingName)) {
                                                System.out.println("ding ding ding!");
                                                references.add(rIdentifier.getReference());
//                                                stringUsages.add(new Pair<PsiElement, TextRange>(rIdentifier, rIdentifier.getTextRange()));
                                            }
                                        }

                                        return true;
                                    }
                                });
                                return references;
                            }

                        };
                        introducer.performInplaceRefactoring(new LinkedHashSet<String>(names));
                    }
                });
            } else {
                Pair nameAndReplaceChoice = getParametersForRefactoring(project, expression, occurrences, name, replaceAll);
                if (nameAndReplaceChoice.first == null && nameAndReplaceChoice.second == null) {
                    return;
                }

                performIntroduce(project, expression, (String) nameAndReplaceChoice.first, occurrences, ((Boolean) nameAndReplaceChoice.second).booleanValue());
            }
        }

        // cribbed from RubyIntroduceHandlerBase
        private PsiElement performIntroduce(final Project project, final RPsiElement expression, final String name, final List<PsiElement> occurrences, final boolean replaceAll) {
            final Ref result = new Ref();
            final RExpression declaration = createLetDeclaration(project, expression, name);
            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                public void run() {
                    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
                        public void run() {
                            AccessToken token = WriteAction.start();

                            try {
                                result.set(performReplace(project, declaration, expression, name, occurrences, replaceAll));
                            } finally {
                                token.finish();
                            }

                        }
                    });
                }
            }, getTitle(), null);
            return (PsiElement) result.get();
        }

        @Override
        protected PsiElement performReplace(Project project, PsiElement declaration, RPsiElement expression, String name, List<PsiElement> occurrences, boolean replaceAll) {
            return super.performReplace(project, declaration, expression, name, occurrences, replaceAll);
        }

        protected RExpression createLetDeclaration(Project project, RPsiElement expression, String name) {
            String assignmentText;
            if (expression instanceof RExpressionSubstitution) {
                RCompoundStatement languageLevel = ((RExpressionSubstitution) expression).getCompoundStatement();
                assignmentText = languageLevel != null ? languageLevel.getText() : "";
            } else if (!(expression instanceof RAssoc) && (!(expression instanceof RListOfExpressions) || !(((RListOfExpressions) expression).getElement(0) instanceof RAssoc))) {
                if (expression instanceof RListOfExpressions && expression.getParent() instanceof RCall) {
                    assignmentText = "[" + expression.getText() + "]";
                } else if (expression instanceof RModifierStatement) {
                    assignmentText = "(" + expression.getText() + ")";
                } else {
                    assignmentText = expression.getText();
                }
            } else {
                assignmentText = "{" + expression.getText() + "}";
            }

            LanguageLevel languageLevel1 = RubyLanguageLevelPusher.getInstance().getLanguageLevelByElement(expression);
            return RubyElementFactory.createExpressionFromText(project, "let(:" + name + ") { " + assignmentText + " }", languageLevel1);
//            return RubyElementFactory.createExpressionFromText(project, name + " = " + assignmentText, languageLevel1);
        }
    }

    private void doExtractLet(Project project, Editor editor, PsiFile psiFile, DataContext dataContext) {
        if (psiFile == null || editor == null)
            return;

        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null || element.getNode() == null)
            return;

        RBlockCall containingScope = Util.findContainingContext(element);
        RBlockCall lastLetBlockCall = findLastLet(containingScope);

        RCodeBlock block = containingScope.getBlock();
        RCompoundStatement blockContents = block.getCompoundStatement();

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

    private RBlockCall findLastLet(RBlockCall containingScope) {
        RCodeBlock block = containingScope.getBlock();
        RBlockCall lastLetBlockCall = null;
        RCompoundStatement blockContents = block.getCompoundStatement();
        for (PsiElement child : blockContents.getChildren()) {
            if (child instanceof RBlockCall && Util.isLetBlockCall((RBlockCall) child)) {
                lastLetBlockCall = (RBlockCall) child;
            }
        }
        return lastLetBlockCall;
    }

    static final String DIALOG_TITLE = "Extract Let";

    public static void extractMethod(@NotNull Project project, Editor editor, PsiFile file, @Nullable String methodName) {
        CommonRefactoringUtil.checkReadOnlyStatus(project, file);
        Pair selected = RefactoringPsiHelper.getSelectedElements(editor, file);
        PsiElement element1 = (PsiElement) selected.first;
        PsiElement element2 = (PsiElement) selected.second;
        if (element1 != null && element2 != null && RubyPsiUtil.getInstance().isBefore(element1, element2)) {
            Pair var11 = getStatements(element1, element2);
            if (var11 != null) {
                ScopeHolder var12 = ScopeUtil.findScopeHolder((PsiElement) var11.first);

                assert var12 != null;

                Object var14 = RubyExtractMethodUtil.createCodeFragment(var12, element1, element2);
                if (var14 instanceof String) {
                    showError(project, editor, var14);
                } else {
//                    RubyExtractMethodHelper.handleExtractFromStatements(project, editor, methodName, (CodeFragment) var14, (RPsiElement) var11.first, (RPsiElement) var11.second);
                }
            } else {
                Object expression = RefactoringPsiHelper.getSelectedExpression(project, file, element1, element2, false, "refactoring.extractMethod");
                if (expression != null && (!(expression instanceof String) || !StringUtil.isEmpty((String) expression))) {
                    if (expression instanceof String) {
                        showError(project, editor, expression);
                    } else {
                        ScopeHolder var13 = ScopeUtil.findScopeHolder(element1);

                        assert var13 != null;

                        Object fragment = RubyExtractMethodUtil.createCodeFragment(var13, element1, element2);
                        if (fragment instanceof String) {
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
        RPsiElement commonParent = parent != null ? RubyPsiUtil.getInstance().getCoveringRPsiElement(parent) : null;
        if (commonParent == null) {
            return null;
        } else {
            RCompoundStatement compoundStatement;
            if (commonParent instanceof RCompoundStatement) {
                compoundStatement = (RCompoundStatement) commonParent;
            } else if (commonParent.getParent() instanceof RCompoundStatement) {
                compoundStatement = (RCompoundStatement) commonParent.getParent();
            } else {
                compoundStatement = null;
            }

            if (compoundStatement == null) {
                return null;
            } else {
                RPsiElement statement1 = RubyPsiUtil.getInstance().getStatement(compoundStatement, element1);
                RPsiElement statement2 = RubyPsiUtil.getInstance().getStatement(compoundStatement, element2);
                return statement1 != null && statement2 != null ? (element1 == PsiTreeUtil.getDeepestFirst(statement1) && element2 == PsiTreeUtil.getDeepestLast(statement2) ? Pair.create(statement1, statement2) : null) : null;
            }
        }
    }

    private static void showError(Project project, Editor editor, Object fragment) {
        CommonRefactoringUtil.showErrorHint(project, editor, (String) fragment, DIALOG_TITLE, "refactoring.extractMethod");
    }
}
