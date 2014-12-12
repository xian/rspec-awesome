package com.github.xian.rspec_awesome;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType;
import org.jetbrains.plugins.ruby.ruby.lang.RubyLanguage;
import org.jetbrains.plugins.ruby.ruby.lang.parser.RubyParserDefinition;
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil;
import org.jetbrains.plugins.ruby.templates.TemplateIntegrationUtils;

public class RspecContextBuilderTest extends LightPlatformCodeInsightFixtureTestCase {
    @Override
    public void setUp() throws Exception {
        PlatformTestCase.initPlatformLangPrefix();
        PluginManager.enablePlugin("ruby");
        TemplateIntegrationUtils.getInstance();
        RubyPsiUtil.getInstance();
        super.setUp();
        LanguageParserDefinitions.INSTANCE.addExplicitExtension(RubyLanguage.INSTANCE, new RubyParserDefinition());
        FileTypeManager.getInstance().registerFileType(RubyFileType.RUBY, "rb");
    }

    public void testContextBuilding() throws Exception {
        myFixture.configureByText("a_spec.rb",
                "require 'spec_helper'\n" +
                        "describe MyClass do\n" +
                        "  let(:var1) { \"var1 top\" }\n" +
                        "  let(:var2) { \"var2 top\" }\n" +
                        "  context \"middle\" do\n" +
                        "    let(:var2) { \"var2 middle\" }\n" +
                        "    let(:var2) { \"var3 middle\" }\n" +
                        "    it \"spec\" do\n" +
                        "      foo\n" +
                        "    end\n" +
                        "  end\n" +
                        "end");
        PsiElement fooElement = myFixture.findElementByText("foo", PsiElement.class);
        RspecContextBuilder contextBuilder = new RspecContextBuilder();
        contextBuilder.searchScope(fooElement);

//        assertEquals("var1 top", contextBuilder.getLet("var1").getValue());
//        assertEquals("var2 middle", contextBuilder.getLet("var2").getValue());
//        assertEquals("var3 middle", contextBuilder.getLet("var3").getValue());
//        assertNull(contextBuilder.getLet("var4"));
    }
}