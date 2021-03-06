package org.apache.xml.serializer.utils;

import java.util.ListResourceBundle;
import org.apache.xpath.res.XPATHErrorResources;

public class SerializerMessages extends ListResourceBundle {
    public Object[][] getContents() {
        contents = new Object[60][];
        contents[0] = new Object[]{MsgKey.BAD_MSGKEY, "The message key ''{0}'' is not in the message class ''{1}''"};
        contents[1] = new Object[]{MsgKey.BAD_MSGFORMAT, "The format of message ''{0}'' in message class ''{1}'' failed."};
        contents[2] = new Object[]{MsgKey.ER_SERIALIZER_NOT_CONTENTHANDLER, "The serializer class ''{0}'' does not implement org.xml.sax.ContentHandler."};
        contents[3] = new Object[]{MsgKey.ER_RESOURCE_COULD_NOT_FIND, "The resource [ {0} ] could not be found.\n {1}"};
        contents[4] = new Object[]{MsgKey.ER_RESOURCE_COULD_NOT_LOAD, "The resource [ {0} ] could not load: {1} \n {2} \t {3}"};
        contents[5] = new Object[]{MsgKey.ER_BUFFER_SIZE_LESSTHAN_ZERO, "Buffer size <=0"};
        contents[6] = new Object[]{XPATHErrorResources.ER_INVALID_UTF16_SURROGATE, "Invalid UTF-16 surrogate detected: {0} ?"};
        contents[7] = new Object[]{XPATHErrorResources.ER_OIERROR, "IO error"};
        contents[8] = new Object[]{MsgKey.ER_ILLEGAL_ATTRIBUTE_POSITION, "Cannot add attribute {0} after child nodes or before an element is produced.  Attribute will be ignored."};
        contents[9] = new Object[]{MsgKey.ER_NAMESPACE_PREFIX, "Namespace for prefix ''{0}'' has not been declared."};
        contents[10] = new Object[]{MsgKey.ER_STRAY_ATTRIBUTE, "Attribute ''{0}'' outside of element."};
        contents[11] = new Object[]{MsgKey.ER_STRAY_NAMESPACE, "Namespace declaration ''{0}''=''{1}'' outside of element."};
        contents[12] = new Object[]{MsgKey.ER_COULD_NOT_LOAD_RESOURCE, "Could not load ''{0}'' (check CLASSPATH), now using just the defaults"};
        contents[13] = new Object[]{MsgKey.ER_ILLEGAL_CHARACTER, "Attempt to output character of integral value {0} that is not represented in specified output encoding of {1}."};
        contents[14] = new Object[]{MsgKey.ER_COULD_NOT_LOAD_METHOD_PROPERTY, "Could not load the propery file ''{0}'' for output method ''{1}'' (check CLASSPATH)"};
        contents[15] = new Object[]{MsgKey.ER_INVALID_PORT, "Invalid port number"};
        contents[16] = new Object[]{MsgKey.ER_PORT_WHEN_HOST_NULL, "Port cannot be set when host is null"};
        contents[17] = new Object[]{MsgKey.ER_HOST_ADDRESS_NOT_WELLFORMED, "Host is not a well formed address"};
        contents[18] = new Object[]{MsgKey.ER_SCHEME_NOT_CONFORMANT, "The scheme is not conformant."};
        contents[19] = new Object[]{MsgKey.ER_SCHEME_FROM_NULL_STRING, "Cannot set scheme from null string"};
        contents[20] = new Object[]{MsgKey.ER_PATH_CONTAINS_INVALID_ESCAPE_SEQUENCE, "Path contains invalid escape sequence"};
        contents[21] = new Object[]{MsgKey.ER_PATH_INVALID_CHAR, "Path contains invalid character: {0}"};
        contents[22] = new Object[]{MsgKey.ER_FRAG_INVALID_CHAR, "Fragment contains invalid character"};
        contents[23] = new Object[]{MsgKey.ER_FRAG_WHEN_PATH_NULL, "Fragment cannot be set when path is null"};
        contents[24] = new Object[]{MsgKey.ER_FRAG_FOR_GENERIC_URI, "Fragment can only be set for a generic URI"};
        contents[25] = new Object[]{MsgKey.ER_NO_SCHEME_IN_URI, "No scheme found in URI"};
        contents[26] = new Object[]{MsgKey.ER_CANNOT_INIT_URI_EMPTY_PARMS, "Cannot initialize URI with empty parameters"};
        contents[27] = new Object[]{MsgKey.ER_NO_FRAGMENT_STRING_IN_PATH, "Fragment cannot be specified in both the path and fragment"};
        contents[28] = new Object[]{MsgKey.ER_NO_QUERY_STRING_IN_PATH, "Query string cannot be specified in path and query string"};
        contents[29] = new Object[]{MsgKey.ER_NO_PORT_IF_NO_HOST, "Port may not be specified if host is not specified"};
        contents[30] = new Object[]{MsgKey.ER_NO_USERINFO_IF_NO_HOST, "Userinfo may not be specified if host is not specified"};
        contents[31] = new Object[]{MsgKey.ER_XML_VERSION_NOT_SUPPORTED, "Warning:  The version of the output document is requested to be ''{0}''.  This version of XML is not supported.  The version of the output document will be ''1.0''."};
        contents[32] = new Object[]{MsgKey.ER_SCHEME_REQUIRED, "Scheme is required!"};
        contents[33] = new Object[]{MsgKey.ER_FACTORY_PROPERTY_MISSING, "The Properties object passed to the SerializerFactory does not have a ''{0}'' property."};
        contents[34] = new Object[]{MsgKey.ER_ENCODING_NOT_SUPPORTED, "Warning:  The encoding ''{0}'' is not supported by the Java runtime."};
        contents[35] = new Object[]{MsgKey.ER_FEATURE_NOT_FOUND, "The parameter ''{0}'' is not recognized."};
        contents[36] = new Object[]{MsgKey.ER_FEATURE_NOT_SUPPORTED, "The parameter ''{0}'' is recognized but the requested value cannot be set."};
        contents[37] = new Object[]{MsgKey.ER_STRING_TOO_LONG, "The resulting string is too long to fit in a DOMString: ''{0}''."};
        contents[38] = new Object[]{MsgKey.ER_TYPE_MISMATCH_ERR, "The value type for this parameter name is incompatible with the expected value type."};
        contents[39] = new Object[]{MsgKey.ER_NO_OUTPUT_SPECIFIED, "The output destination for data to be written to was null."};
        contents[40] = new Object[]{MsgKey.ER_UNSUPPORTED_ENCODING, "An unsupported encoding is encountered."};
        contents[41] = new Object[]{MsgKey.ER_UNABLE_TO_SERIALIZE_NODE, "The node could not be serialized."};
        contents[42] = new Object[]{MsgKey.ER_CDATA_SECTIONS_SPLIT, "The CDATA Section contains one or more termination markers ']]>'."};
        contents[43] = new Object[]{MsgKey.ER_WARNING_WF_NOT_CHECKED, "An instance of the Well-Formedness checker could not be created.  The well-formed parameter was set to true but well-formedness checking can not be performed."};
        contents[44] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER, "The node ''{0}'' contains invalid XML characters."};
        contents[45] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER_IN_COMMENT, "An invalid XML character (Unicode: 0x{0}) was found in the comment."};
        contents[46] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER_IN_PI, "An invalid XML character (Unicode: 0x{0}) was found in the processing instructiondata."};
        contents[47] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER_IN_CDATA, "An invalid XML character (Unicode: 0x{0}) was found in the contents of the CDATASection."};
        contents[48] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER_IN_TEXT, "An invalid XML character (Unicode: 0x{0}) was found in the node''s character data content."};
        contents[49] = new Object[]{MsgKey.ER_WF_INVALID_CHARACTER_IN_NODE_NAME, "An invalid XML character(s) was found in the {0} node named ''{1}''."};
        contents[50] = new Object[]{MsgKey.ER_WF_DASH_IN_COMMENT, "The string \"--\" is not permitted within comments."};
        contents[51] = new Object[]{MsgKey.ER_WF_LT_IN_ATTVAL, "The value of attribute \"{1}\" associated with an element type \"{0}\" must not contain the ''<'' character."};
        contents[52] = new Object[]{MsgKey.ER_WF_REF_TO_UNPARSED_ENT, "The unparsed entity reference \"&{0};\" is not permitted."};
        contents[53] = new Object[]{MsgKey.ER_WF_REF_TO_EXTERNAL_ENT, "The external entity reference \"&{0};\" is not permitted in an attribute value."};
        contents[54] = new Object[]{MsgKey.ER_NS_PREFIX_CANNOT_BE_BOUND, "The prefix \"{0}\" can not be bound to namespace \"{1}\"."};
        contents[55] = new Object[]{MsgKey.ER_NULL_LOCAL_ELEMENT_NAME, "The local name of element \"{0}\" is null."};
        contents[56] = new Object[]{MsgKey.ER_NULL_LOCAL_ATTR_NAME, "The local name of attr \"{0}\" is null."};
        contents[57] = new Object[]{MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF, "The replacement text of the entity node \"{0}\" contains an element node \"{1}\" with an unbound prefix \"{2}\"."};
        contents[58] = new Object[]{MsgKey.ER_ELEM_UNBOUND_PREFIX_IN_ENTREF, "The replacement text of the entity node \"{0}\" contains an attribute node \"{1}\" with an unbound prefix \"{2}\"."};
        contents[59] = new Object[]{MsgKey.ER_WRITING_INTERNAL_SUBSET, "An error occured while writing the internal subset."};
        return contents;
    }
}
