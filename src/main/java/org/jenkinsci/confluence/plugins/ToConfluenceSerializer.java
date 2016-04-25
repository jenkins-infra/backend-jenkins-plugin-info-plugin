/**
 * Created by acearl on 4/22/2016.
 */

package org.jenkinsci.confluence.plugins;

import org.pegdown.LinkRenderer;
import org.pegdown.Printer;
import org.pegdown.VerbatimSerializer;
import org.pegdown.ast.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.parboiled.common.Preconditions.checkArgNotNull;

/**
 * Class to serialize Markdown to Confluence wiki markup based on pegdown processing
 */
public class ToConfluenceSerializer implements Visitor {


    protected Printer printer = new Printer();
    protected final Map<String, ReferenceNode> references = new HashMap<String, ReferenceNode>();
    protected final Map<String, String> abbreviations = new HashMap<String, String>();
    protected final LinkRenderer linkRenderer;

    protected Stack<ListType> listStack = new Stack<ListType>();

    protected URI resourceRoot;

    protected Map<List<String>, VerbatimSerializer> verbatimSerializers;

    protected String[] codeTypes = new String[] { "java", "ruby", "python", "javascript" };

    public enum ListType {
        ORDERED_LIST,
        BULLETED_LIST
    }

    public class DefaultVerbatimConfluenceSerializer implements VerbatimSerializer {
        public void serialize(VerbatimNode node, Printer printer) {
            printText("{code}", node, "{code}");
        }
    }

    public class CodeVerbatimSerializer implements VerbatimSerializer {
        public void serialize(VerbatimNode node, Printer printer) {
            printText("{code:" + node.getType() + "}", node, "{code}");
        }
    }

    public ToConfluenceSerializer(LinkRenderer linkRenderer, String resourceRoot) throws URISyntaxException {
        this.linkRenderer = linkRenderer;
        if(!resourceRoot.endsWith("/")) {
            resourceRoot += "/";
        }
        this.resourceRoot = new URI(resourceRoot);
        this.verbatimSerializers = new HashMap<List<String>, VerbatimSerializer>();
        this.verbatimSerializers.put(Collections.singletonList(VerbatimSerializer.DEFAULT), new DefaultVerbatimConfluenceSerializer());
        List<String> codeTypes = new ArrayList<String>();
        Collections.addAll(codeTypes, this.codeTypes);
        this.verbatimSerializers.put(codeTypes, new CodeVerbatimSerializer());
    }

    /**
     * Main interface method to convert an AST to markup using the visit methods
     * @param astRoot root node of the AST from processing via pegdown
     * @return the Confluence wiki markup representing the Markdown
     */
    public String toConfluence(RootNode astRoot) {
        checkArgNotNull(astRoot, "astRoot");
        astRoot.accept(this);
        return printer.getString().trim();
    }

    public void visit(AbbreviationNode abbreviationNode) {
        // don't do anything with these...
    }

    public void visit(AnchorLinkNode node) {
        printLink("[#", linkRenderer.render(node), "]");
    }

    public void visit(AutoLinkNode node) {
        printLink("[", linkRenderer.render(node), "]");
    }

    public void visit(BlockQuoteNode node) {
        printText("bq.", node, "");
    }

    public void visit(BulletListNode node) {
        listStack.push(ListType.BULLETED_LIST);
        visitChildren(node);
        listStack.pop();
    }

    public void visit(CodeNode node) {
        printText("{code}", node, "{code}");
    }

    public void visit(DefinitionListNode definitionListNode) {

    }

    public void visit(DefinitionNode definitionNode) {

    }

    public void visit(DefinitionTermNode definitionTermNode) {

    }

    public void visit(ExpImageNode node) {
        String url = node.url;
        try {
            URI uri = new URI(node.url);
            if(!uri.isAbsolute()) {
                // add the resource root
                url = resourceRoot.resolve(uri).toASCIIString();
            }
        } catch (URISyntaxException e) {

        }

        printer.print("!" + url );
        if(node.title != null && node.title.length() > 0) {
            printer.print("|alt=");
            printer.print(node.title);
        }
        printer.print("!");
    }

    public void visit(ExpLinkNode node) {
        String text = printChildrenToString(node);
        printLink("[", linkRenderer.render(node, text), "]");
    }

    public void visit(HeaderNode node) {
        printer.println().println().print("h" + node.getLevel() + ". ");
        visitChildren(node);
        printer.println();
    }

    public void visit(HtmlBlockNode htmlBlockNode) {

    }

    public void visit(InlineHtmlNode inlineHtmlNode) {

    }

    public void visit(ListItemNode node) {
        ListType cur = listStack.peek();
        char[] chars = new char[getCurrentNestCount(cur)];
        if(cur == ListType.ORDERED_LIST) {
            Arrays.fill(chars, '#');
        } else {
            Arrays.fill(chars, '*');
        }
        String indentChars = new String(chars);
        printer.println();
        printText(indentChars + " ", node, "");
    }

    public void visit(MailLinkNode node) {
        printLink("[mailto:", linkRenderer.render(node), "]");
    }

    public void visit(OrderedListNode node) {
        listStack.push(ListType.ORDERED_LIST);
        visitChildren(node);
        listStack.pop();
    }

    public void visit(ParaNode node) {
        printer.println();
        printText("", node, "");
        printer.println();
    }

    public void visit(QuotedNode node) {

    }

    public void visit(ReferenceNode node) {

    }

    public void visit(RefImageNode node) {

    }

    public void visit(RefLinkNode node) {
        String text = printChildrenToString(node);
        String key = node.referenceKey != null ? printChildrenToString(node.referenceKey) : text;
        ReferenceNode refNode = references.get(normalize(key));
        if (refNode == null) { // "fake" reference link
            printer.print('[').print(text).print(']');
            if (node.separatorSpace != null) {
                printer.print(node.separatorSpace).print('[');
                if (node.referenceKey != null) printer.print(key);
                printer.print(']');
            }
        } else printLink("[", linkRenderer.render(node, refNode.getUrl(), refNode.getTitle(), text), "]");

    }

    public void visit(RootNode node) {
        for (ReferenceNode refNode : node.getReferences()) {
            visitChildren(refNode);
            references.put(normalize(printer.getString()), refNode);
            printer.clear();
        }
        for (AbbreviationNode abbrNode : node.getAbbreviations()) {
            visitChildren(abbrNode);
            String abbr = printer.getString();
            printer.clear();
            abbrNode.getExpansion().accept(this);
            String expansion = printer.getString();
            abbreviations.put(abbr, expansion);
            printer.clear();
        }
        visitChildren(node);
    }

    public void visit(SimpleNode node) {
        switch (node.getType()) {
            case HRule:
                printer.println().print("----").println();
                break;
            case Linebreak:
                printer.println();
                break;
            case Nbsp:
                printer.print(" ");
                break;
            default:
                throw new IllegalStateException();
        }
    }

    public void visit(SpecialTextNode node) {
        printer.printEncoded(node.getText());
    }

    public void visit(StrikeNode node) {
        printText("-", node, "-");
    }

    public void visit(StrongEmphSuperNode node) {
        if (node.isClosed()) {
            if(node.isStrong()) {
                printText("*", node, "*");
            } else {
                printText("_", node, "_");
            }
        } else {
            //sequence was not closed, treat open chars as ordinary chars
            printer.print(node.getChars());
            visitChildren(node);
        }
    }

    public void visit(TableBodyNode node) {

    }

    public void visit(TableCaptionNode node) {

    }

    public void visit(TableCellNode node) {

    }

    public void visit(TableColumnNode node) {

    }

    public void visit(TableHeaderNode node) {

    }

    public void visit(TableNode node) {

    }

    public void visit(TableRowNode node) {

    }

    public void visit(VerbatimNode node) {
        VerbatimSerializer serializer = lookupSerializer(node.getType());
        serializer.serialize(node, printer);
    }

    protected VerbatimSerializer lookupSerializer(final String type) {
        for(List<String> types : verbatimSerializers.keySet()) {
            if(types.contains(type)) {
                return verbatimSerializers.get(types);
            }
        }
        return lookupSerializer(VerbatimSerializer.DEFAULT);
    }

    public void visit(WikiLinkNode node) {
        printLink("[", linkRenderer.render(node), "]");
    }

    public void visit(TextNode node) {
        printer.print(node.getText());
    }

    public void visit(SuperNode node) {
        visitChildren(node);
    }

    public void visit(Node node) {

    }

    /* helper methods */

    protected void visitChildren(SuperNode node) {
        for (Node child : node.getChildren()) {
            child.accept(this);
        }
    }

    protected String printChildrenToString(SuperNode node) {
        Printer priorPrinter = printer;
        printer = new Printer();
        visitChildren(node);
        String result = printer.getString();
        printer = priorPrinter;
        return result;
    }

    private void printLink(String prefix, LinkRenderer.Rendering rendering, String postfix) {
        printer.print(prefix);
        printer.print(rendering.text);
        printer.print("|");

        String href = rendering.href;
        try {
            URI uri = new URI(rendering.href);
            if(!uri.isAbsolute()) {
                // add the resource root
                href = resourceRoot.resolve(uri).toASCIIString();
            }
        } catch (URISyntaxException e) {

        }

        printer.print(href);
        printer.print(postfix);
    }

    private void printText(String prefix, TextNode node, String postfix) {
        printer.print(prefix);
        printer.print(node.getText());
        printer.print(postfix);
    }

    private void printText(String prefix, SuperNode node, String postfix) {
        printer.print(prefix);
        visitChildren(node);
        printer.print(postfix);
    }

    private int getCurrentNestCount(ListType t) {
        int count = 0;
        for(ListType c : listStack) {
            if(c != t) {
                break;
            }
            count++;
        }

        return count;
    }

    protected String normalize(String string) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case ' ':
                case '\n':
                case '\t':
                    continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
