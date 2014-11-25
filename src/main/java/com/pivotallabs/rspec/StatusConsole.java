// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3) 
// Source File Name:   StatusConsole.java

package com.pivotallabs.rspec;

import com.intellij.openapi.editor.*;
import com.intellij.ui.JBColor;

import java.awt.Component;
import java.awt.Font;
import java.text.MessageFormat;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

// Referenced classes of package com.pivotallabs.rspec:
//            ProjectComponent

public class StatusConsole {
    private DefaultListModel listModel;
    private JList list;
    private final JScrollPane scrollView;

    StatusConsole() {
        listModel = new DefaultListModel();
        list = new JList(listModel);
        list.setSelectionMode(0);
        list.addListSelectionListener(
                new ListSelectionListener() {
                    public void valueChanged(ListSelectionEvent listSelectionEvent) {
                        int minSelectionIndex = list.getSelectionModel().getMinSelectionIndex();
                        if (minSelectionIndex != -1) {
                            ListItem selectedListItem = (ListItem) listModel.get(minSelectionIndex);
                            selectedListItem.goTo();
                        }
                    }
                }
        );
        list.setCellRenderer(new MyListCellRenderer());
        scrollView = new JScrollPane(list);
    }

    public JComponent getComponent() {
        return scrollView;
    }

    public void clear() {
        listModel.clear();
    }

    public void addItem(ListItem listItem) {
        listModel.addElement(listItem);
    }

    private static String joinNewlines(String s) {
        return s.replaceAll("\\n+", " <b style=\"color: blue;\">\266</b> ");
    }

    private static class MyListCellRenderer extends JEditorPane implements ListCellRenderer {
        public static final Font FONT = Font.getFont("Monospaced");

        private MyListCellRenderer() {
            super("text/html", "");
        }

        public Component getListCellRendererComponent(JList jList, Object o, int i, boolean isSelected, boolean hasFocus) {
            ListItem listItem = (ListItem) o;
            setText((new StringBuilder()).append("<span style=\"font-family: Menlo,Monaco,monospace;\">").append(listItem.getText()).append("</span>").toString());
            if (isSelected) {
                setBackground(JBColor.GREEN);
            } else {
                setBackground(jList.getBackground());
            }
            setFont(FONT);
            return this;
        }
    }

    public static class ContextItem extends ListItem {
        private final Editor editor;
        private final RspecContextBuilder.Part part;
        private final int offset;
        private final int index;
        private final boolean isLast;

        public ContextItem(Editor editor, RspecContextBuilder.Part part, int offset, int index, boolean isLast) {
            super(editor, offset);
            this.editor = editor;
            this.part = part;
            this.offset = offset;
            this.index = index;
            this.isLast = isLast;
        }

        public String getText() {
            return Util.htmlEscape(part.getText());
        }
    }

    public static class BeforeItem extends ListItem {
        private final RspecContextBuilder.BeforeOrAfter beforeOrAfter;

        public BeforeItem(Editor editor, RspecContextBuilder.BeforeOrAfter beforeOrAfter) {
            super(editor, beforeOrAfter.getOffset());
            this.beforeOrAfter = beforeOrAfter;
        }

        public String getText() {
            return (new StringBuilder()).append(beforeOrAfter.getType()).append(" { ").append(StatusConsole.joinNewlines(Util.htmlEscape(beforeOrAfter.getText()))).append(" }").toString();
        }
    }

    public static class DividerItem extends ListItem {
        DividerItem() {
            super(null, -1);
        }

        public String getText() {
            return "----------------------------";
        }
    }

    public static class LetListItem extends ListItem {
        private RspecContextBuilder.Let let;

        public LetListItem(RspecContextBuilder.Let let, Editor editor) {
            super(editor, let.getOffset());
            this.let = let;
        }

        public String getText() {
            String label = let.getName();
            if (let.getType().startsWith("subject")) {
                label = "subject:" + label;
            }
            return two_columns(label, let.getValue(), let.getType().contains("!"));
        }
    }

    public static class ListItem {
        private final Editor editor;
        private final int offset;

        ListItem(Editor editor, int offset) {
            this.offset = offset;
            this.editor = editor;
        }

        String two_columns(String left, String right, boolean leftIsRed) {
            int leftLength = left.length();
            String nbsps = (new String(new char[Math.max(28 - leftLength, 0)])).replace("\0", "&nbsp;");
            return MessageFormat.format("<b{0}>{1}</b>{2} {3}", new Object[]{
                    leftIsRed ? " style=\"color: red;\"" : "", Util.htmlEscape(left).replaceAll(" ", "&nbsp;"), nbsps, StatusConsole.joinNewlines(Util.htmlEscape(right))
            });
        }

        public String getText() {
            return "";
        }

        public void goTo() {
            editor.getCaretModel().moveToOffset(offset);
            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
    }
}
