/*
  This file is licensed to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.xmlunit.diff;

import static org.xmlunit.util.Linqy.all;
import static org.xmlunit.util.Linqy.any;
import static org.xmlunit.util.Linqy.map;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.transform.dom.DOMSource;
import org.xmlunit.util.BiPredicate;
import org.xmlunit.util.IsNullPredicate;
import org.xmlunit.util.IterableNodeList;
import org.xmlunit.util.Linqy;
import org.xmlunit.util.Mapper;
import org.xmlunit.util.Nodes;
import org.xmlunit.util.Predicate;
import org.xmlunit.xpath.JAXPXPathEngine;
import org.xmlunit.xpath.XPathEngine;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Common ElementSelector implementations.
 */
public final class ElementSelectors {
    private ElementSelectors() { }

    /**
     * Always returns true, i.e. each element can be compared to each
     * other element.
     *
     * <p>Generally this means elements will be compared in document
     * order.</p>
     */
    public static final ElementSelector Default = new ElementSelector() {
            @Override
            public boolean canBeCompared(Element _controlElement,
                                         XPathContext _controlXPath,
                                         Element _testElement,
                                         XPathContext _textXPath) {
                return true;
            }
        };

    /**
     * Elements with the same local name (and namespace URI - if any)
     * can be compared.
     */
    public static final ElementSelector byName = new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext _controlXPath,
                                         Element testElement,
                                         XPathContext _textXPath) {
                return controlElement != null
                    && testElement != null
                    && bothNullOrEqual(Nodes.getQName(controlElement),
                                       Nodes.getQName(testElement));
            }
        };

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and nested text (if any) can be compared.
     */
    public static final ElementSelector byNameAndText = new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return byName.canBeCompared(controlElement, controlXPath,
                                            testElement, testXPath)
                    && bothNullOrEqual(Nodes.getMergedNestedText(controlElement),
                                       Nodes.getMergedNestedText(testElement));
            }
        };

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and attribute values for the given attribute names can be
     * compared.
     *
     * <p>Attributes are only searched for in the null namespace.</p>
     */
    public static ElementSelector byNameAndAttributes(String... attribs) {
        if (attribs == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        if (any(Arrays.asList(attribs), new IsNullPredicate())) {
            throw new IllegalArgumentException("attributes must not contain null values");
        }
        QName[] qs = new QName[attribs.length];
        for (int i = 0; i < attribs.length; i++) {
            qs[i] = new QName(attribs[i]);
        }
        return byNameAndAttributes(qs);
    }

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and attribute values for the given attribute names can be
     * compared.
     *
     * <p>Namespace URIs of attributes are those of the attributes on
     * the control element or the null namespace if they don't
     * exist.</p>
     */
    public static ElementSelector
        byNameAndAttributesControlNS(final String... attribs) {

        if (attribs == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        final Collection<String> qs = Arrays.asList(attribs);
        if (any(qs, new IsNullPredicate())) {
            throw new IllegalArgumentException("attributes must not contain null values");
        }
        final HashSet<String> as = new HashSet(qs);
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext textXPath) {
                if (!byName.canBeCompared(controlElement, controlXPath,
                                          testElement, textXPath)) {
                    return false;
                }
                Map<QName, String> cAttrs = Nodes.getAttributes(controlElement);
                Map<String, QName> qNameByLocalName =
                    new HashMap<String, QName>();
                for (QName q : cAttrs.keySet()) {
                    String local = q.getLocalPart();
                    if (as.contains(local)) {
                        qNameByLocalName.put(local, q);
                    }
                }
                for (String a : as) {
                    QName q = qNameByLocalName.get(a);
                    if (q == null) {
                        qNameByLocalName.put(a, new QName(a));
                    }
                }
                return mapsEqualForKeys(cAttrs,
                                        Nodes.getAttributes(testElement),
                                        qNameByLocalName.values());
            }
        };
    }

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and attribute values for the given attribute names can be
     * compared.
     */
    public static ElementSelector byNameAndAttributes(final QName... attribs) {
        if (attribs == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        final Collection<QName> qs = Arrays.asList(attribs);
        if (any(qs, new IsNullPredicate())) {
            throw new IllegalArgumentException("attributes must not contain null values");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                if (!byName.canBeCompared(controlElement, controlXPath,
                                          testElement, testXPath)) {
                    return false;
                }
                return mapsEqualForKeys(Nodes.getAttributes(controlElement),
                                        Nodes.getAttributes(testElement),
                                        qs);
            }
        };
    }

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and attribute values for all attributes can be compared.
     */
    public static final ElementSelector byNameAndAllAttributes =
        new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                if (!byName.canBeCompared(controlElement, controlXPath,
                                          testElement, testXPath)) {
                    return false;
                }
                Map<QName, String> cAttrs = Nodes.getAttributes(controlElement);
                Map<QName, String> tAttrs = Nodes.getAttributes(testElement);
                if (cAttrs.size() != tAttrs.size()) {
                    return false;
                }
                return mapsEqualForKeys(cAttrs, tAttrs, cAttrs.keySet());
            }
        };

    /**
     * Elements with the same local name (and namespace URI - if any)
     * and child elements and nested text at each level (if any) can
     * be compared.
     */
    public static final ElementSelector byNameAndTextRec = new ByNameAndTextRecSelector();

    /**
     * Negates another ElementSelector.
     */
    public static ElementSelector not(final ElementSelector es) {
        if (es == null) {
            throw new IllegalArgumentException("es must not be null");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return !es.canBeCompared(controlElement, controlXPath,
                                         testElement, testXPath);
            }
        };
    }

    /**
     * Accepts two elements if at least one of the given ElementSelectors does.
     */
    public static ElementSelector or(final ElementSelector... selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("selectors must not be null");
        }
        final Collection<ElementSelector> s = Arrays.asList(selectors);
        if (any(s, new IsNullPredicate())) {
            throw new IllegalArgumentException("selectors must not contain null values");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return any(s, new CanBeComparedPredicate(controlElement, controlXPath,
                                                         testElement, testXPath));
            }
        };
    }

    /**
     * Accepts two elements if all of the given ElementSelectors do.
     */
    public static ElementSelector and(final ElementSelector... selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("selectors must not be null");
        }
        final Collection<ElementSelector> s = Arrays.asList(selectors);
        if (any(s, new IsNullPredicate())) {
            throw new IllegalArgumentException("selectors must not contain null values");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return all(s,
                           new CanBeComparedPredicate(controlElement,
                                                      controlXPath,
                                                      testElement,
                                                      testXPath));
            }
        };
    }

    /**
     * Accepts two elements if exactly on of the given ElementSelectors does.
     */
    public static ElementSelector xor(final ElementSelector es1,
                                      final ElementSelector es2) {
        if (es1 == null || es2 == null) {
            throw new IllegalArgumentException("selectors must not be null");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return es1.canBeCompared(controlElement, controlXPath,
                                         testElement, testXPath)
                    ^ es2.canBeCompared(controlElement, controlXPath,
                                        testElement, testXPath);
            }
        };
    }

    /**
     * Applies the wrapped ElementSelector's logic if and only if the
     * control element matches the given predicate.
     */
    public static ElementSelector conditionalSelector(final BiPredicate<? super Element, XPathContext> predicate,
                                                      final ElementSelector es) {

        if (predicate == null) {
            throw new IllegalArgumentException("predicate must not be null");
        }
        if (es == null) {
            throw new IllegalArgumentException("es must not be null");
        }
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                return predicate.test(controlElement, controlXPath)
                    && es.canBeCompared(controlElement, controlXPath,
                                        testElement, testXPath);
            }
        };
    }

    /**
     * Applies the wrapped ElementSelector's logic if and only if the
     * control element has the given (local) name.
     */
    public static ElementSelector selectorForElementNamed(final String expectedName,
                                                          final ElementSelector es) {
        if (expectedName == null) {
            throw new IllegalArgumentException("expectedName must not be null");
        }

        return conditionalSelector(elementNameBiPredicate(expectedName), es);
    }

    /**
     * Applies the wrapped ElementSelector's logic if and only if the
     * control element has the given name.
     */
    public static ElementSelector selectorForElementNamed(final QName expectedName,
                                                          final ElementSelector es) {
        if (expectedName == null) {
            throw new IllegalArgumentException("expectedName must not be null");
        }

        return conditionalSelector(elementNameBiPredicate(expectedName), es);
    }

    /**
     * Selects two elements as matching if the child elements selected
     * via XPath match using the given childSelector.
     *
     * <p>The xpath expression should yield elements.  Two elements
     * match if a DefaultNodeMatcher applied to the selected children
     * finds matching pairs for all children.</p>
     *
     * @param xpath XPath expression applied in the context of the
     * elements to chose from that selects the children to compare.
     * @param childSelector ElementSelector to apply to the selected children.
     */
    public static ElementSelector byXPath(String xpath, ElementSelector childSelector) {
        return byXPath(xpath, null, childSelector);
    }

    /**
     * Selects two elements as matching if the child elements selected
     * via XPath match using the given childSelector.
     *
     * <p>The xpath expression should yield elements.  Two elements
     * match if a DefaultNodeMatcher applied to the selected children
     * finds matching pairs for all children.</p>
     *
     * @param xpath XPath expression applied in the context of the
     * elements to chose from that selects the children to compare.
     * @param namespaceContext provides prefix mapping for namespace
     * prefixes used inside the xpath expression
     * @param childSelector ElementSelector to apply to the selected children.
     */
    public static ElementSelector byXPath(final String xpath,
                                          Map<String, String> namespaceContext,
                                          ElementSelector childSelector) {
        final XPathEngine engine = new JAXPXPathEngine();
        if (namespaceContext != null) {
            engine.setNamespaceContext(namespaceContext);
        }
        final NodeMatcher nm = new DefaultNodeMatcher(childSelector);
        return new ElementSelector() {
            @Override
            public boolean canBeCompared(Element controlElement,
                                         XPathContext controlXPath,
                                         Element testElement,
                                         XPathContext testXPath) {
                Iterable<Node> controlChildren =
                    engine.selectNodes(xpath, new DOMSource(controlElement));
                // TODO
                controlXPath.setChildren(map(controlChildren, TO_NODE_INFO));
                int expected = Linqy.count(controlChildren);
                Iterable<Node> testChildren =
                    engine.selectNodes(xpath, new DOMSource(testElement));
                // TODO
                testXPath.setChildren(map(testChildren, TO_NODE_INFO));
                int matched =
                    Linqy.count(nm.match(controlChildren,
                                         /* TODO */ new ChildNodeXPathContextProvider(controlXPath, controlChildren),
                                         testChildren,
                                         /* TODO */ new ChildNodeXPathContextProvider(testXPath, testChildren)));
                return expected == matched;
            }
        };
    }

    /**
     * {@code then}-part of conditional {@link ElementSelectors} built
     * via {@link ConditionalSelectorBuilder}.
     */
    public interface ConditionalSelectorBuilderThen {
        /**
         * Specifies the ElementSelector to use when the condition holds true.
         */
        ConditionalSelectorBuilder thenUse(ElementSelector es);
    }

    /**
     * Allows to build complex {@link ElementSelector}s by combining simpler blocks.
     *
     * <p>All pairs created by the {@code when*}/{@code thenUse} pairs
     * are evaluated in order until one returns true, finally the
     * {@code default}, if any, is consulted.</p>
     */
    public interface ConditionalSelectorBuilder {
        /**
         * Sets up a conditional ElementSelector.
         */
        ConditionalSelectorBuilderThen when(BiPredicate<? super Element, XPathContext> biPredicate);
        /**
         * Sets up a conditional ElementSelector.
         */
        ConditionalSelectorBuilderThen whenElementIsNamed(String expectedName);
        /**
         * Sets up a conditional ElementSelector.
         */
        ConditionalSelectorBuilderThen whenElementIsNamed(QName expectedName);
        /**
         * Assigns a default ElementSelector.
         */
        ConditionalSelectorBuilder defaultTo(ElementSelector es);
        /**
         * Builds a conditional ElementSelector.
         */
        ElementSelector build();
    }

    /**
     * Allows to build complex {@link ElementSelector}s by combining simpler blocks.
     *
     * <p>All pairs created by the {@code when*}/{@code thenUse} pairs
     * are evaluated in order until one returns true, finally the
     * {@code default}, if any, is consulted.</p>
     */
    public static ConditionalSelectorBuilder conditionalBuilder() {
        return new DefaultConditionalSelectorBuilder();
    }

    private static class DefaultConditionalSelectorBuilder
        implements ConditionalSelectorBuilder, ConditionalSelectorBuilderThen {
        private ElementSelector defaultSelector;
        private final List<ElementSelector> conditionalSelectors = new LinkedList<ElementSelector>();
        private BiPredicate<? super Element, XPathContext> pendingCondition;

        @Override
        public ConditionalSelectorBuilder thenUse(ElementSelector es) {
            if (pendingCondition == null) {
                throw new IllegalStateException("missing condition");
            }
            conditionalSelectors.add(conditionalSelector(pendingCondition, es));
            pendingCondition = null;
            return this;
        }
        @Override
        public ConditionalSelectorBuilderThen when(BiPredicate<? super Element, XPathContext> biPredicate) {
            if (pendingCondition != null) {
                throw new IllegalStateException("unbalanced conditions");
            }
            pendingCondition = biPredicate;
            return this;
        }
        @Override
        public ConditionalSelectorBuilder defaultTo(ElementSelector es) {
            if (defaultSelector != null) {
                throw new IllegalStateException("can't have more than one default selector");
            }
            defaultSelector = es;
            return this;
        }
        @Override
        public ConditionalSelectorBuilderThen whenElementIsNamed(String expectedName) {
            return when(elementNameBiPredicate(expectedName));
        }
        @Override
        public ConditionalSelectorBuilderThen whenElementIsNamed(QName expectedName) {
            return when(elementNameBiPredicate(expectedName));
        }
        @Override
        public ElementSelector build() {
            if (pendingCondition != null) {
                throw new IllegalStateException("unbalanced conditions");
            }
            List<ElementSelector> es = new ArrayList<ElementSelector>();
            es.addAll(conditionalSelectors);
            if (defaultSelector != null) {
                es.add(defaultSelector);
            }
            return or(es.toArray(new ElementSelector[es.size()]));
        }
    }

    private static boolean bothNullOrEqual(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    private static boolean mapsEqualForKeys(Map<QName, String> control,
                                            Map<QName, String> test,
                                            Iterable<QName> keys) {
        for (QName q : keys) {
            if (!bothNullOrEqual(control.get(q), test.get(q))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isText(Node n) {
        return n instanceof Text || n instanceof CDATASection;
    }

    private static BiPredicate<Element, XPathContext> elementNameBiPredicate(final String expectedName) {
        return new BiPredicate<Element, XPathContext>() {
            @Override
            public boolean test(Element e, XPathContext _) {
                if (e == null) {
                    return false;
                }
                String name = e.getLocalName();
                if (name == null) {
                    name = e.getNodeName();
                }
                return expectedName.equals(name);
            }
        };
    }

    private static BiPredicate<Element, XPathContext> elementNameBiPredicate(final QName expectedName) {
        return new BiPredicate<Element, XPathContext>() {
            @Override
            public boolean test(Element e, XPathContext _) {
                return e == null ? false : expectedName.equals(Nodes.getQName(e));
            }
        };
    }

    private static class CanBeComparedPredicate implements Predicate<ElementSelector> {
        private final Element e1, e2;
        private final XPathContext c1, c2;

        private CanBeComparedPredicate(Element e1, XPathContext c1,
                                       Element e2, XPathContext c2) {
            this.e1 = e1;
            this.c1 = c1;
            this.e2 = e2;
            this.c2 = c2;
        }

        @Override
        public boolean test(ElementSelector es) {
            return es.canBeCompared(e1, c1, e2, c2);
        }
    }

    private static class ByNameAndTextRecSelector implements ElementSelector {
        @Override
        public boolean canBeCompared(Element controlElement,
                                     XPathContext controlXPath,
                                     Element testElement,
                                     XPathContext testXPath) {
            if (!byNameAndText.canBeCompared(controlElement,
                                             controlXPath,
                                             testElement,
                                             testXPath)) {
                return false;
            }
            NodeList controlChildren = getAndRegisterChildren(controlElement,
                                                              controlXPath);
            NodeList testChildren = getAndRegisterChildren(testElement, testXPath);
            final int controlLen = controlChildren.getLength();
            final int testLen = testChildren.getLength();
            int controlIndex, testIndex;
            for (controlIndex = testIndex = 0;
                 controlIndex < controlLen && testIndex < testLen;
                 ) {
                // find next non-text child nodes
                Map.Entry<Integer, Node> control = findNonText(controlChildren,
                                                               controlIndex,
                                                               controlLen);
                controlIndex = control.getKey();
                Node c = control.getValue();
                if (isText(c)) {
                    break;
                }
                Map.Entry<Integer, Node> test = findNonText(testChildren,
                                                            testIndex,
                                                            testLen);
                testIndex = test.getKey();
                Node t = test.getValue();
                if (isText(t)) {
                    break;
                }

                // different types of children make elements
                // non-comparable
                if (c.getNodeType() != t.getNodeType()) {
                    return false;
                }
                // recurse for child elements
                if (c instanceof Element) {
                    try {
                        controlXPath.navigateToChild(controlIndex);
                        testXPath.navigateToChild(testIndex);
                        if (!byNameAndTextRec.canBeCompared((Element) c,
                                                            controlXPath,
                                                            (Element) t,
                                                            testXPath)) {
                            return false;
                        }
                    } finally {
                        controlXPath.navigateToParent();
                        testXPath.navigateToParent();
                    }
                }
                controlIndex++;
                testIndex++;
            }

            // child lists exhausted?
            if (controlIndex < controlLen) {
                Map.Entry<Integer, Node> p = findNonText(controlChildren,
                                                         controlIndex,
                                                         controlLen);
                controlIndex = p.getKey();
                // some non-Text children remained
                if (controlIndex < controlLen) {
                    return false;
                }
            }
            if (testIndex < testLen) {
                Map.Entry<Integer, Node> p = findNonText(testChildren,
                                                         testIndex,
                                                         testLen);
                testIndex = p.getKey();
                // some non-Text children remained
                if (testIndex < testLen) {
                    return false;
                }
            }
            return true;
        }

        private NodeList getAndRegisterChildren(Element parent, XPathContext ctx) {
            NodeList nl = parent.getChildNodes();
            ctx.setChildren(map(new IterableNodeList(nl), TO_NODE_INFO));
            return nl;
        }

        private Map.Entry<Integer, Node> findNonText(NodeList nl, int current, int len) {
            Node n = nl.item(current);
            while (isText(n) && ++current < len) {
                n = nl.item(current);
            }
            return new AbstractMap.SimpleImmutableEntry<Integer, Node>(current, n);
        }
    }

    /**
     * Maps Nodes to their NodeInfo equivalent.
     */
    static final Mapper<Node, XPathContext.NodeInfo> TO_NODE_INFO =
        new Mapper<Node, XPathContext.NodeInfo>() {
            @Override
            public XPathContext.NodeInfo apply(Node n) {
                return new XPathContext.DOMNodeInfo(n);
            }
        };

}
