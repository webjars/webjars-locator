package org.webjars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.script.ScriptException;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class RequireJSTest {

    private static final String WEBJAR_URL_PREFIX = "/webjars/";
    private static final String WEBJAR_CDN_PREFIX = "http://cdn.jsdelivr.net/webjars/";

    @Test
    public void should_generate_correct_javascript() throws ScriptException {
        String javaScript = RequireJS.getSetupJavaScript(WEBJAR_URL_PREFIX);

        assertTrue(javaScript.indexOf("\"bootstrap\":\"3.1.1\",") > 0);

        assertSyntax(javaScript);

    }

    @Test
    public void should_generate_correct_json() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);
        Map<String, ObjectNode> jsonWithCdn = RequireJS.getSetupJson(WEBJAR_CDN_PREFIX, WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "jquery/2.1.0/jquery", jsonNoCdn.get("jquery").get("paths").withArray("jquery").get(0).asText());
        assertEquals(WEBJAR_CDN_PREFIX + "jquery/2.1.0/jquery", jsonWithCdn.get("jquery").get("paths").withArray("jquery").get(0).asText());
        assertEquals(WEBJAR_URL_PREFIX + "jquery/2.1.0/jquery", jsonWithCdn.get("jquery").get("paths").withArray("jquery").get(1).asText());

        assertEquals("$", jsonNoCdn.get("jquery").get("shim").get("jquery").get("exports").asText());
    }

    @Test
    public void should_get_nonversioned_json() {
        List<Map.Entry<String, Boolean>> prefixes = new ArrayList<Map.Entry<String, Boolean>>();
        prefixes.add(new AbstractMap.SimpleEntry<String, Boolean>(WEBJAR_URL_PREFIX, false));

        Map<String, ObjectNode> json = RequireJS.generateSetupJson(prefixes);

        assertEquals(WEBJAR_URL_PREFIX + "jquery/jquery", json.get("jquery").get("paths").withArray("jquery").get(0).asText());
    }

    @Test
    public void should_replace_location() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "when-node/3.5.2/when", jsonNoCdn.get("when-node").withArray("packages").get(0).get("location").asText());

        Map<String, ObjectNode> jsonWithCdn = RequireJS.getSetupJson(WEBJAR_CDN_PREFIX, WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "when-node/3.5.2/when", jsonWithCdn.get("when-node").withArray("packages").get(0).get("location").asText());
    }

    @Test
    public void should_work_with_bower_webjars() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);

        // todo: the angular version changes due to a range transitive dependency

        assertEquals(WEBJAR_URL_PREFIX + "angular-bootstrap/0.13.0/ui-bootstrap-tpls", jsonNoCdn.get("angular-bootstrap").get("paths").get("angular-bootstrap").get(0).asText());
        assertEquals(WEBJAR_URL_PREFIX + "angular/1.8.2/angular", jsonNoCdn.get("angular").get("paths").get("angular").get(0).asText());

        Map<String, ObjectNode> jsonWithCdn = RequireJS.getSetupJson(WEBJAR_CDN_PREFIX, WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_CDN_PREFIX + "angular-bootstrap/0.13.0/ui-bootstrap-tpls", jsonWithCdn.get("angular-bootstrap").get("paths").get("angular-bootstrap").get(0).asText());
        assertEquals(WEBJAR_URL_PREFIX + "angular-bootstrap/0.13.0/ui-bootstrap-tpls", jsonWithCdn.get("angular-bootstrap").get("paths").get("angular-bootstrap").get(1).asText());
    }

    @Test
    public void should_pick_right_script_in_bower_webjars() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "angular-schema-form/0.8.2/dist/schema-form", jsonNoCdn.get("angular-schema-form").get("paths").get("angular-schema-form").get(0).asText());

        Map<String, ObjectNode> jsonWithCdn = RequireJS.getSetupJson(WEBJAR_CDN_PREFIX, WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_CDN_PREFIX + "angular-schema-form/0.8.2/dist/schema-form", jsonWithCdn.get("angular-schema-form").get("paths").get("angular-schema-form").get(0).asText());
        assertEquals(WEBJAR_URL_PREFIX + "angular-schema-form/0.8.2/dist/schema-form", jsonWithCdn.get("angular-schema-form").get("paths").get("angular-schema-form").get(1).asText());
    }

    @Test
    public void should_work_with_npm_webjars() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "angular-pouchdb/2.0.8/dist/angular-pouchdb", jsonNoCdn.get("angular-pouchdb").get("paths").get("angular-pouchdb").get(0).asText());

        Map<String, ObjectNode> jsonWithCdn = RequireJS.getSetupJson(WEBJAR_CDN_PREFIX, WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_CDN_PREFIX + "angular-pouchdb/2.0.8/dist/angular-pouchdb", jsonWithCdn.get("angular-pouchdb").get("paths").get("angular-pouchdb").get(0).asText());
        assertEquals(WEBJAR_URL_PREFIX + "angular-pouchdb/2.0.8/dist/angular-pouchdb", jsonWithCdn.get("angular-pouchdb").get("paths").get("angular-pouchdb").get(1).asText());
    }

    @Test
    public void should_fix_npm_module_names() {
        Map<String, ObjectNode> jsonNoCdn = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);

        assertEquals(WEBJAR_URL_PREFIX + "validate.js/0.8.0/validate", jsonNoCdn.get("validate.js").get("paths").get("validate-js").get(0).asText());
    }

    @Test
    public void should_be_empty_if_no_main_nor_index_file() {
        Map<String, ObjectNode> json = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);
        assertNull(json.get("babel-runtime"));
    }

    @Test
    public void should_be_ok_with_index_file_but_no_main() {
        Map<String, ObjectNode> json = RequireJS.getSetupJson(WEBJAR_URL_PREFIX);
        assertEquals(WEBJAR_URL_PREFIX + "object-assign/4.1.0/index", json.get("object-assign").get("paths").get("object-assign").get(0).asText());
    }

    @Test
    public void should_load_webjar_without_requirejs_properties() {
        ObjectNode objectNode = RequireJS.getWebJarRequireJsConfig(new AbstractMap.SimpleEntry<>("bootswatch", "2.3.1"), Collections.<Map.Entry<String, Boolean>>emptyList());
        assertEquals(0, objectNode.size());
    }

    private static void assertSyntax(String javaScript) {
        try (Context context = Context.enter()){
            Scriptable scope = context.initStandardObjects();
            context.evaluateString(scope, javaScript, "setup-template.mustache", 1, null);
        }
    }

}
