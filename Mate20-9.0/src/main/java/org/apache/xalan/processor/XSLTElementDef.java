package org.apache.xalan.processor;

import java.util.Enumeration;
import java.util.Hashtable;
import org.apache.xml.utils.Constants;
import org.apache.xml.utils.QName;

public class XSLTElementDef {
    static final int T_ANY = 3;
    static final int T_ELEMENT = 1;
    static final int T_PCDATA = 2;
    private XSLTAttributeDef[] m_attributes;
    private Class m_classObject;
    private XSLTElementProcessor m_elementProcessor;
    private XSLTElementDef[] m_elements;
    private boolean m_has_required;
    boolean m_isOrdered;
    private int m_lastOrder;
    private boolean m_multiAllowed;
    private String m_name;
    private String m_nameAlias;
    private String m_namespace;
    private int m_order;
    private boolean m_required;
    Hashtable m_requiredFound;
    private int m_type;

    XSLTElementDef() {
        this.m_type = 1;
        this.m_has_required = false;
        this.m_required = false;
        this.m_isOrdered = false;
        this.m_order = -1;
        this.m_lastOrder = -1;
        this.m_multiAllowed = true;
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject) {
        XSLTSchema xSLTSchema = schema;
        String str = namespace;
        String str2 = nameAlias;
        this.m_type = 1;
        this.m_has_required = false;
        this.m_required = false;
        this.m_isOrdered = false;
        this.m_order = -1;
        this.m_lastOrder = -1;
        this.m_multiAllowed = true;
        build(str, name, str2, elements, attributes, contentHandler, classObject);
        if (str == null || (!str.equals(Constants.S_XSLNAMESPACEURL) && !str.equals("http://xml.apache.org/xalan") && !str.equals(Constants.S_BUILTIN_OLD_EXTENSIONS_URL))) {
            String str3 = name;
            return;
        }
        xSLTSchema.addAvailableElement(new QName(str, name));
        if (str2 != null) {
            xSLTSchema.addAvailableElement(new QName(str, str2));
        }
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, boolean has_required) {
        XSLTSchema xSLTSchema = schema;
        String str = namespace;
        String str2 = nameAlias;
        this.m_type = 1;
        this.m_has_required = false;
        this.m_required = false;
        this.m_isOrdered = false;
        this.m_order = -1;
        this.m_lastOrder = -1;
        this.m_multiAllowed = true;
        this.m_has_required = has_required;
        build(str, name, str2, elements, attributes, contentHandler, classObject);
        if (str == null || (!str.equals(Constants.S_XSLNAMESPACEURL) && !str.equals("http://xml.apache.org/xalan") && !str.equals(Constants.S_BUILTIN_OLD_EXTENSIONS_URL))) {
            String str3 = name;
            return;
        }
        xSLTSchema.addAvailableElement(new QName(str, name));
        if (str2 != null) {
            xSLTSchema.addAvailableElement(new QName(str, str2));
        }
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, boolean has_required, boolean required) {
        this(schema, namespace, name, nameAlias, elements, attributes, contentHandler, classObject, has_required);
        this.m_required = required;
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, boolean has_required, boolean required, int order, boolean multiAllowed) {
        this(schema, namespace, name, nameAlias, elements, attributes, contentHandler, classObject, has_required, required);
        this.m_order = order;
        this.m_multiAllowed = multiAllowed;
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, boolean has_required, boolean required, boolean has_order, int order, boolean multiAllowed) {
        this(schema, namespace, name, nameAlias, elements, attributes, contentHandler, classObject, has_required, required);
        this.m_order = order;
        this.m_multiAllowed = multiAllowed;
        this.m_isOrdered = has_order;
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, boolean has_order, int order, boolean multiAllowed) {
        this(schema, namespace, name, nameAlias, elements, attributes, contentHandler, classObject, order, multiAllowed);
        this.m_isOrdered = has_order;
    }

    XSLTElementDef(XSLTSchema schema, String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject, int order, boolean multiAllowed) {
        this(schema, namespace, name, nameAlias, elements, attributes, contentHandler, classObject);
        this.m_order = order;
        this.m_multiAllowed = multiAllowed;
    }

    XSLTElementDef(Class classObject, XSLTElementProcessor contentHandler, int type) {
        this.m_type = 1;
        this.m_has_required = false;
        this.m_required = false;
        this.m_isOrdered = false;
        this.m_order = -1;
        this.m_lastOrder = -1;
        this.m_multiAllowed = true;
        this.m_classObject = classObject;
        this.m_type = type;
        setElementProcessor(contentHandler);
    }

    /* access modifiers changed from: package-private */
    public void build(String namespace, String name, String nameAlias, XSLTElementDef[] elements, XSLTAttributeDef[] attributes, XSLTElementProcessor contentHandler, Class classObject) {
        this.m_namespace = namespace;
        this.m_name = name;
        this.m_nameAlias = nameAlias;
        this.m_elements = elements;
        this.m_attributes = attributes;
        setElementProcessor(contentHandler);
        this.m_classObject = classObject;
        if (hasRequired() && this.m_elements != null) {
            for (XSLTElementDef def : this.m_elements) {
                if (def != null && def.getRequired()) {
                    if (this.m_requiredFound == null) {
                        this.m_requiredFound = new Hashtable();
                    }
                    this.m_requiredFound.put(def.getName(), "xsl:" + def.getName());
                }
            }
        }
    }

    private static boolean equalsMayBeNull(Object obj1, Object obj2) {
        return obj2 == obj1 || !(obj1 == null || obj2 == null || !obj2.equals(obj1));
    }

    private static boolean equalsMayBeNullOrZeroLen(String s1, String s2) {
        int len1 = s1 == null ? 0 : s1.length();
        if (len1 != (s2 == null ? 0 : s2.length())) {
            return false;
        }
        if (len1 == 0) {
            return true;
        }
        return s1.equals(s2);
    }

    /* access modifiers changed from: package-private */
    public int getType() {
        return this.m_type;
    }

    /* access modifiers changed from: package-private */
    public void setType(int t) {
        this.m_type = t;
    }

    /* access modifiers changed from: package-private */
    public String getNamespace() {
        return this.m_namespace;
    }

    /* access modifiers changed from: package-private */
    public String getName() {
        return this.m_name;
    }

    /* access modifiers changed from: package-private */
    public String getNameAlias() {
        return this.m_nameAlias;
    }

    public XSLTElementDef[] getElements() {
        return this.m_elements;
    }

    /* access modifiers changed from: package-private */
    public void setElements(XSLTElementDef[] defs) {
        this.m_elements = defs;
    }

    private boolean QNameEquals(String uri, String localName) {
        return equalsMayBeNullOrZeroLen(this.m_namespace, uri) && (equalsMayBeNullOrZeroLen(this.m_name, localName) || equalsMayBeNullOrZeroLen(this.m_nameAlias, localName));
    }

    /* access modifiers changed from: package-private */
    public XSLTElementProcessor getProcessorFor(String uri, String localName) {
        XSLTElementProcessor elemDef = null;
        if (this.m_elements == null) {
            return null;
        }
        int n = this.m_elements.length;
        int order = -1;
        boolean multiAllowed = true;
        int i = 0;
        while (true) {
            if (i >= n) {
                break;
            }
            XSLTElementDef def = this.m_elements[i];
            if (def.m_name.equals("*")) {
                if (!equalsMayBeNullOrZeroLen(uri, Constants.S_XSLNAMESPACEURL)) {
                    elemDef = def.m_elementProcessor;
                    order = def.getOrder();
                    multiAllowed = def.getMultiAllowed();
                }
            } else if (def.QNameEquals(uri, localName)) {
                if (def.getRequired()) {
                    setRequiredFound(def.getName(), true);
                }
                order = def.getOrder();
                multiAllowed = def.getMultiAllowed();
                elemDef = def.m_elementProcessor;
            }
            i++;
        }
        if (elemDef != null && isOrdered()) {
            int lastOrder = getLastOrder();
            if (order > lastOrder) {
                setLastOrder(order);
            } else if (order == lastOrder && !multiAllowed) {
                return null;
            } else {
                if (order >= lastOrder || order <= 0) {
                    return elemDef;
                }
                return null;
            }
        }
        return elemDef;
    }

    /* access modifiers changed from: package-private */
    public XSLTElementProcessor getProcessorForUnknown(String uri, String localName) {
        if (this.m_elements == null) {
            return null;
        }
        for (XSLTElementDef def : this.m_elements) {
            if (def.m_name.equals("unknown") && uri.length() > 0) {
                return def.m_elementProcessor;
            }
        }
        return null;
    }

    /* access modifiers changed from: package-private */
    public XSLTAttributeDef[] getAttributes() {
        return this.m_attributes;
    }

    /* access modifiers changed from: package-private */
    public XSLTAttributeDef getAttributeDef(String uri, String localName) {
        XSLTAttributeDef defaultDef = null;
        for (XSLTAttributeDef attrDef : getAttributes()) {
            String uriDef = attrDef.getNamespace();
            String nameDef = attrDef.getName();
            if (nameDef.equals("*") && (equalsMayBeNullOrZeroLen(uri, uriDef) || (uriDef != null && uriDef.equals("*") && uri != null && uri.length() > 0))) {
                return attrDef;
            }
            if (nameDef.equals("*") && uriDef == null) {
                defaultDef = attrDef;
            } else if (equalsMayBeNullOrZeroLen(uri, uriDef) && localName.equals(nameDef)) {
                return attrDef;
            }
        }
        if (defaultDef != null || uri.length() <= 0 || equalsMayBeNullOrZeroLen(uri, Constants.S_XSLNAMESPACEURL)) {
            return defaultDef;
        }
        return XSLTAttributeDef.m_foreignAttr;
    }

    public XSLTElementProcessor getElementProcessor() {
        return this.m_elementProcessor;
    }

    public void setElementProcessor(XSLTElementProcessor handler) {
        if (handler != null) {
            this.m_elementProcessor = handler;
            this.m_elementProcessor.setElemDef(this);
        }
    }

    /* access modifiers changed from: package-private */
    public Class getClassObject() {
        return this.m_classObject;
    }

    /* access modifiers changed from: package-private */
    public boolean hasRequired() {
        return this.m_has_required;
    }

    /* access modifiers changed from: package-private */
    public boolean getRequired() {
        return this.m_required;
    }

    /* access modifiers changed from: package-private */
    public void setRequiredFound(String elem, boolean found) {
        if (this.m_requiredFound.get(elem) != null) {
            this.m_requiredFound.remove(elem);
        }
    }

    /* access modifiers changed from: package-private */
    public boolean getRequiredFound() {
        if (this.m_requiredFound == null) {
            return true;
        }
        return this.m_requiredFound.isEmpty();
    }

    /* access modifiers changed from: package-private */
    public String getRequiredElem() {
        if (this.m_requiredFound == null) {
            return null;
        }
        Enumeration elems = this.m_requiredFound.elements();
        String s = "";
        boolean first = true;
        while (elems.hasMoreElements()) {
            if (first) {
                first = false;
            } else {
                s = s + ", ";
            }
            s = s + ((String) elems.nextElement());
        }
        return s;
    }

    /* access modifiers changed from: package-private */
    public boolean isOrdered() {
        return this.m_isOrdered;
    }

    /* access modifiers changed from: package-private */
    public int getOrder() {
        return this.m_order;
    }

    /* access modifiers changed from: package-private */
    public int getLastOrder() {
        return this.m_lastOrder;
    }

    /* access modifiers changed from: package-private */
    public void setLastOrder(int order) {
        this.m_lastOrder = order;
    }

    /* access modifiers changed from: package-private */
    public boolean getMultiAllowed() {
        return this.m_multiAllowed;
    }
}
