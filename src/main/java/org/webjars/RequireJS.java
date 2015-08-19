package org.webjars;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;

import org.apache.commons.lang3.StringUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public final class RequireJS {

    public static final String WEBJARS_MAVEN_PREFIX = "META-INF/maven/org.webjars";

    private static final Logger log = LoggerFactory.getLogger(RequireJS.class);

    private static String requireConfigJavaScript;
    private static String requireConfigJavaScriptCdn;

    private static Map<String, ObjectNode> requireConfigJson;
    private static Map<String, ObjectNode> requireConfigJsonCdn;

    /**
     * Returns the JavaScript that is used to setup the RequireJS config.
     * This value is cached in memory so that all of the processing to get the String only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag
     */
    public synchronized static String getSetupJavaScript(String urlPrefix) {
        if (requireConfigJavaScript == null) {
            List<String> prefixes = new ArrayList<String>();
            prefixes.add(urlPrefix);

            requireConfigJavaScript = generateSetupJavaScript(prefixes);
        }
        return requireConfigJavaScript;
    }

    /**
     * Returns the JavaScript that is used to setup the RequireJS config.
     * This value is cached in memory so that all of the processing to get the String only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @param cdnPrefix The optional CDN prefix where the WebJars can be downloaded from
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag
     */
    public synchronized static String getSetupJavaScript(String cdnPrefix, String urlPrefix) {
        if (requireConfigJavaScriptCdn == null) {
            List<String> prefixes = new ArrayList<String>();
            prefixes.add(cdnPrefix);
            prefixes.add(urlPrefix);

            requireConfigJavaScriptCdn = generateSetupJavaScript(prefixes);
        }
        return requireConfigJavaScriptCdn;
    }

    /**
     * Returns the JavaScript that is used to setup the RequireJS config.
     * This value is not cached.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag.
     */
    public static String generateSetupJavaScript(List<String> prefixes) {
        Map<String, String> webJars = new WebJarAssetLocator().getWebJars();

        return generateSetupJavaScript(prefixes, webJars);
    }

    /**
     * Generate the JavaScript that is used to setup the RequireJS config.
     * This value is not cached.
     * This uses nasty stuff that is really not maintainable or testable.  So this has been deprecated and the implementation will eventually be replaced with something better.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @param webJars  The WebJars (artifactId -&gt; version) to use
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag.
     */
    @Deprecated
    public static String generateSetupJavaScript(List<String> prefixes, Map<String, String> webJars) {

        List<Map.Entry<String, Boolean>> prefixesWithVersion = new ArrayList<Map.Entry<String, Boolean>>();
        for (String prefix : prefixes) {
            prefixesWithVersion.add(new AbstractMap.SimpleEntry<String, Boolean>(prefix, true));
        }

        ObjectMapper mapper = new ObjectMapper();

        ObjectNode webJarsVersions = mapper.createObjectNode();

        StringBuilder webJarConfigsString = new StringBuilder();

        if (webJars.isEmpty()) {
            log.warn("Can't find any WebJars in the classpath, RequireJS configuration will be empty.");
        } else {
            for (Map.Entry<String, String> webJar : webJars.entrySet()) {

                // assemble the WebJar versions string
                webJarsVersions.put(webJar.getKey(), webJar.getValue());

                // assemble the WebJar config string

                // default to the new pom.xml meta-data way
                ObjectNode webJarObjectNode = getWebJarSetupJson(webJar, prefixesWithVersion);
                if ((webJarObjectNode != null ? webJarObjectNode.size() : 0) != 0) {
                    webJarConfigsString.append("\n").append("requirejs.config(").append(webJarObjectNode.toString()).append(");");
                } else {
                    webJarConfigsString.append("\n").append(getWebJarConfig(webJar));
                }
            }
        }

        String webJarBasePath = "webJarId + '/' + webjars.versions[webJarId] + '/' + path";

        StringBuilder webJarPath = new StringBuilder("[");

        for (String prefix : prefixes) {
            webJarPath.append("'").append(prefix).append("' + ").append(webJarBasePath).append(",\n");
        }

        //webJarBasePath.
        webJarPath.delete(webJarPath.lastIndexOf(",\n"), webJarPath.lastIndexOf(",\n") + 2);

        webJarPath.append("]");

        return "var webjars = {\n" +
                "    versions: " + webJarsVersions.toString() + ",\n" +
                "    path: function(webJarId, path) {\n" +
                "        console.error('The webjars.path() method of getting a WebJar path has been deprecated.  The RequireJS config in the ' + webJarId + ' WebJar may need to be updated.  Please file an issue: http://github.com/webjars/' + webJarId + '/issues/new');\n" +
                "        return " + webJarPath.toString() + ";\n" +
                "    }\n" +
                "};\n" +
                "\n" +
                "var require = {\n" +
                "    callback: function() {\n" +
                "        // Deprecated WebJars RequireJS plugin loader\n" +
                "        define('webjars', function() {\n" +
                "            return {\n" +
                "                load: function(name, req, onload, config) {\n" +
                "                    if (name.indexOf('.js') >= 0) {\n" +
                "                        console.warn('Detected a legacy file name (' + name + ') as the thing to load.  Loading via file name is no longer supported so the .js will be dropped in an effort to resolve the module name instead.');\n" +
                "                        name = name.replace('.js', '');\n" +
                "                    }\n" +
                "                    console.error('The webjars plugin loader (e.g. webjars!' + name + ') has been deprecated.  The RequireJS config in the ' + name + ' WebJar may need to be updated.  Please file an issue: http://github.com/webjars/webjars/issues/new');\n" +
                "                    req([name], function() {\n" +
                "                        onload();\n" +
                "                    });\n" +
                "                }\n" +
                "            }\n" +
                "        });\n" +
                "\n" +
                "        // All of the WebJar configs\n\n" +
                webJarConfigsString +
                "    }\n" +
                "};";
    }

    /**
     * Returns the JSON that is used to setup the RequireJS config.
     * This value is cached in memory so that all of the processing to get the JSON only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JSON structured config
     */
    public synchronized static Map<String, ObjectNode> getSetupJson(String urlPrefix) {
        if (requireConfigJson == null) {

            List<Map.Entry<String, Boolean>> prefixes = new ArrayList<Map.Entry<String, Boolean>>();
            prefixes.add(new AbstractMap.SimpleEntry<String, Boolean>(urlPrefix, true));

            requireConfigJson = generateSetupJson(prefixes);
        }
        return requireConfigJson;
    }

    /**
     * Returns the JSON that is used to setup the RequireJS config.
     * This value is cached in memory so that all of the processing to get the JSON only has to happen once.
     *
     * @param cdnPrefix The CDN prefix where the WebJars can be downloaded from
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JSON structured config
     */
    public synchronized static Map<String, ObjectNode> getSetupJson(String cdnPrefix, String urlPrefix) {
        if (requireConfigJsonCdn == null) {

            List<Map.Entry<String, Boolean>> prefixes = new ArrayList<Map.Entry<String, Boolean>>();
            prefixes.add(new AbstractMap.SimpleEntry<String, Boolean>(cdnPrefix, true));
            prefixes.add(new AbstractMap.SimpleEntry<String, Boolean>(urlPrefix, true));

            requireConfigJsonCdn = generateSetupJson(prefixes);
        }
        return requireConfigJsonCdn;
    }

    /**
     * Returns the JSON used to setup the RequireJS config for each WebJar in the CLASSPATH.
     * This value is not cached.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config with a boolean flag
     *                 indicating whether or not to include the version.
     * @return The JSON structured config for each WebJar.
     */
    public static Map<String, ObjectNode> generateSetupJson(List<Map.Entry<String, Boolean>> prefixes) {
        Map<String, String> webJars = new WebJarAssetLocator().getWebJars();

        Map<String, ObjectNode> jsonConfigs = new HashMap<String, ObjectNode>();

        for (Map.Entry<String, String> webJar : webJars.entrySet()) {
            jsonConfigs.put(webJar.getKey(), getWebJarSetupJson(webJar, prefixes));
        }

        return jsonConfigs;
    }

    private static ObjectNode getWebJarSetupJson(Map.Entry<String, String> webJar, List<Map.Entry<String, Boolean>> prefixes) {

        if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars.npm/" + webJar.getKey() + "/pom.xml") != null) {
            // create the requirejs config from the package.json
            return getNpmWebJarRequireJsConfig(webJar, prefixes);
        }
        else if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars.bower/" + webJar.getKey() + "/pom.xml") != null) {
            // create the requirejs config from the bower.json
            return getBowerWebJarRequireJsConfig(webJar, prefixes);
        }
        else if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars/" + webJar.getKey() + "/pom.xml") != null) {
            // get the requirejs config from the pom
            return getWebJarRequireJsConfig(webJar, prefixes);
        }

        return null;
    }

    /**
     * Returns the JSON RequireJS config for a given WebJar
     *
     * @param webJar   A tuple (artifactId -&gt; version) representing the WebJar.
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JSON RequireJS config for the WebJar based on the meta-data in the WebJar's pom.xml file.
     */
    public static ObjectNode getWebJarRequireJsConfig(Map.Entry<String, String> webJar, List<Map.Entry<String, Boolean>> prefixes) {
        String rawRequireJsConfig = getRawWebJarRequireJsConfig(webJar);

        ObjectMapper mapper = new ObjectMapper()
            .configure(ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(ALLOW_SINGLE_QUOTES, true);

        // default to just an empty object
        ObjectNode webJarRequireJsNode = mapper.createObjectNode();

        try {
            JsonNode maybeRequireJsConfig = mapper.readTree(rawRequireJsConfig);
            if (maybeRequireJsConfig.isObject()) {
                // The provided config was parseable, now lets fix the paths

                webJarRequireJsNode = (ObjectNode) maybeRequireJsConfig;


                if (webJarRequireJsNode.isObject()) {

                    // update the paths

                    ObjectNode pathsNode = (ObjectNode) webJarRequireJsNode.get("paths");

                    ObjectNode newPaths = mapper.createObjectNode();

                    if (pathsNode != null) {
                        Iterator<Map.Entry<String, JsonNode>> paths = pathsNode.fields();
                        while (paths.hasNext()) {
                            Map.Entry<String, JsonNode> pathNode = paths.next();

                            String originalPath = null;

                            if (pathNode.getValue().isArray()) {
                                ArrayNode nodePaths = (ArrayNode) pathNode.getValue();
                                // lets just assume there is only 1 for now
                                originalPath = nodePaths.get(0).asText();
                            } else if (pathNode.getValue().isTextual()) {
                                TextNode nodePath = (TextNode) pathNode.getValue();
                                originalPath = nodePath.textValue();
                            }

                            if (originalPath != null) {
                                ArrayNode newPathsNode = newPaths.putArray(pathNode.getKey());
                                for (Map.Entry<String, Boolean> prefix : prefixes) {
                                    String newPath = prefix.getKey() + webJar.getKey();
                                    if (prefix.getValue()) {
                                        newPath += "/" + webJar.getValue();
                                    }
                                    newPath += "/" + originalPath;
                                    newPathsNode.add(newPath);
                                }
                                newPathsNode.add(originalPath);
                            } else {
                                log.error("Strange... The path could not be parsed.  Here is what was provided: " + pathNode.getValue().toString());
                            }
                        }
                    }

                    webJarRequireJsNode.replace("paths", newPaths);


                    // update the location in the packages node
                    ArrayNode packagesNode = webJarRequireJsNode.withArray("packages");

                    ArrayNode newPackages = mapper.createArrayNode();

                    if (packagesNode != null) {
                        for (JsonNode packageJson : packagesNode) {
                            String originalLocation = packageJson.get("location").textValue();
                            if (prefixes.size() > 0) {
                                // this picks the last prefix assuming that it is the right one
                                // not sure of a better way to do this since I don't think we want the CDN prefix
                                // maybe this can be an array like paths?
                                Map.Entry<String, Boolean> prefix = prefixes.get(prefixes.size() - 1);
                                String newLocation = prefix.getKey() + webJar.getKey();
                                if (prefix.getValue()) {
                                    newLocation += "/" + webJar.getValue();
                                }
                                newLocation += "/" + originalLocation;

                                ((ObjectNode) packageJson).put("location", newLocation);
                            }

                            newPackages.add(packageJson);
                        }
                    }

                    webJarRequireJsNode.replace("packages", newPackages);
                }

            } else {
                log.error(requireJsConfigErrorMessage(webJar));
            }
        } catch (IOException e) {
            log.warn(requireJsConfigErrorMessage(webJar));
            if (rawRequireJsConfig.length() > 0) {
                // only show the error if there was a config to parse
                log.error(e.getMessage());
            }
        }

        return webJarRequireJsNode;
    }


    /**
     * Returns the JSON RequireJS config for a given Bower WebJar
     *
     * @param webJar   A tuple (artifactId -&gt; version) representing the WebJar.
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JSON RequireJS config for the WebJar based on the meta-data in the WebJar's pom.xml file.
     */
    public static ObjectNode getBowerWebJarRequireJsConfig(Map.Entry<String, String> webJar, List<Map.Entry<String, Boolean>> prefixes) {

        String bowerJsonPath = WebJarAssetLocator.WEBJARS_PATH_PREFIX + "/" + webJar.getKey() + "/" + webJar.getValue() + "/" + "bower.json";

        return getWebJarRequireJsConfigFromMainConfig(webJar, prefixes, bowerJsonPath);
    }

    /**
     * Returns the JSON RequireJS config for a given Bower WebJar
     *
     * @param webJar   A tuple (artifactId -&gt; version) representing the WebJar.
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JSON RequireJS config for the WebJar based on the meta-data in the WebJar's pom.xml file.
     */
    public static ObjectNode getNpmWebJarRequireJsConfig(Map.Entry<String, String> webJar, List<Map.Entry<String, Boolean>> prefixes) {

        String packageJsonPath = WebJarAssetLocator.WEBJARS_PATH_PREFIX + "/" + webJar.getKey() + "/" + webJar.getValue() + "/" + "package.json";

        return getWebJarRequireJsConfigFromMainConfig(webJar, prefixes, packageJsonPath);
    }

    private static ObjectNode getWebJarRequireJsConfigFromMainConfig(Map.Entry<String, String> webJar, List<Map.Entry<String, Boolean>> prefixes, String path) {
        InputStream inputStream = RequireJS.class.getClassLoader().getResourceAsStream(path);

        if (inputStream != null) {
            try {
                ObjectMapper mapper = new ObjectMapper()
                        .configure(ALLOW_UNQUOTED_FIELD_NAMES, true)
                        .configure(ALLOW_SINGLE_QUOTES, true);

                ObjectNode requireConfig = mapper.createObjectNode();
                ObjectNode requireConfigPaths = requireConfig.putObject("paths");

                JsonNode jsonNode = mapper.readTree(inputStream);

                String name = jsonNode.get("name").asText();
                String requireFriendlyName = name.replaceAll("\\.", "-");

                JsonNode mainJs = jsonNode.get("main");
                if (mainJs == null)
                    throw new IllegalArgumentException("no 'main' attribute; cannot generate a config");

                if (mainJs.getNodeType() == JsonNodeType.STRING) {
                    String main = mainJs.asText();
                    requireConfigPaths.put(requireFriendlyName, mainJsToPathJson(webJar, main, prefixes));
                }
                else if (mainJs.getNodeType() == JsonNodeType.ARRAY) {
                    ArrayList<String> mainList = new ArrayList<>();
                    for (JsonNode mainJsonNode : mainJs) {
                        mainList.add(mainJsonNode.asText());
                    }
                    String main = getBowerBestMatchFromMainArray(mainList, name);
                    requireConfigPaths.put(requireFriendlyName, mainJsToPathJson(webJar, main, prefixes));
                }

                // todo add dependency shims

                return requireConfig;

            } catch (IOException e) {
                log.warn("Could not create the RequireJS config for the " + webJar.getKey() + " " + webJar.getValue() + " WebJar" + " from " + path + "\n" +
                        "Error: " +  e.getMessage() + "\n" +
                        "Please file a bug at: http://github.com/webjars/webjars-locator/issues/new");
            } catch (IllegalArgumentException e) {
                log.warn("Could not create the RequireJS config for the " + webJar.getKey() + " " + webJar.getValue() + " WebJar" + " from " + path + "\n" +
                        "There was not enough information in the package metadata to do so.\n" +
                        "Error: " +  e.getMessage() + "\n" +
                        "If you think you have received this message in error, " +
                        "please file a bug at: http://github.com/webjars/webjars-locator/issues/new");
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // what-evs
                }
            }

        }

        return null;
    }

    /*
     * Heuristic approach to find the 'best' candidate which most likely is the main script of a package.
     */

    private static String getBowerBestMatchFromMainArray(ArrayList<String> items, String name) {
        if(items.size() == 1) // not really much choice here
            return items.get(0);

        ArrayList<String> filteredList = new ArrayList<>();

        // first idea: only look at .js files

        for(String item : items) {
            if(item.toLowerCase().endsWith(".js")) {
                filteredList.add(item);
            }
        }

        // ... if there are any
        if(filteredList.size() == 0)
            filteredList = items;

        final HashMap<String, Integer> distanceMap = new HashMap<>();
        final String nameForComparisons = name.toLowerCase();

        // second idea: most scripts are named after the project's name
        // sort all script files by their Levenshtein-distance
        // and return the one which is most similar to the project's name

        Collections.sort(filteredList, new Comparator<String>() {

            public Integer getDistance(String value) {
                int distance;
                value = value.toLowerCase();
                if (distanceMap.containsKey(value)) {
                    distance = distanceMap.get(value);
                } else {
                    distance = StringUtils.getLevenshteinDistance(nameForComparisons, value);
                    distanceMap.put(value, distance);
                }
                return distance;
            }

            @Override
            public int compare(String o1, String o2) {
                return getDistance(o1).compareTo(getDistance(o2));
            }
        });

        return filteredList.get(0);
    }

    private static JsonNode mainJsToPathJson(Map.Entry<String, String> webJar, String main, List<Map.Entry<String, Boolean>> prefixes) {
        String requireJsStyleMain = main;
        if (main.endsWith(".js")) {
            requireJsStyleMain = main.substring(0, main.lastIndexOf(".js"));
        }

        if (requireJsStyleMain.startsWith("./")) {
            requireJsStyleMain = requireJsStyleMain.substring(2);
        }

        String unprefixedMain = webJar.getKey() + "/" + webJar.getValue() + "/" + requireJsStyleMain;

        ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);

        for (Map.Entry<String, Boolean> prefix : prefixes) {
            arrayNode.add(prefix.getKey() + unprefixedMain);
        }

        return arrayNode;
    }

    /**
     * A generic error message for when the RequireJS config could not be parsed out of the WebJar's pom.xml meta-data.
     *
     * @param webJar A tuple (artifactId -> version) representing the WebJar.
     * @return The error message.
     */
    private static String requireJsConfigErrorMessage(Map.Entry<String, String> webJar) {
        return "Could not read WebJar RequireJS config for: " + webJar.getKey() + " " + webJar.getValue() + "\n" +
                "Please file a bug at: http://github.com/webjars/" + webJar.getKey() + "/issues/new";
    }

    /**
     * @param webJar A tuple (artifactId -&gt; version) representing the WebJar.
     * @return The raw RequireJS config string from the WebJar's pom.xml meta-data.
     */
    public static String getRawWebJarRequireJsConfig(Map.Entry<String, String> webJar) {
        String filename = WEBJARS_MAVEN_PREFIX + "/" + webJar.getKey() + "/pom.xml";
        InputStream inputStream = RequireJS.class.getClassLoader().getResourceAsStream(filename);

        if (inputStream != null) {
            // try to parse: <root><properties><requirejs>{ /* some json */ }</requirejs></properties></root>
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputStream);
                doc.getDocumentElement().normalize();

                NodeList propertiesNodes = doc.getElementsByTagName("properties");
                for (int i = 0; i < propertiesNodes.getLength(); i++) {
                    NodeList propertyNodes = propertiesNodes.item(i).getChildNodes();
                    for (int j = 0; j < propertyNodes.getLength(); j++) {
                        Node node = propertyNodes.item(j);
                        if (node.getNodeName().equals("requirejs")) {
                            return node.getTextContent();
                        }
                    }
                }

            } catch (ParserConfigurationException | IOException | SAXException e) {
                log.warn(requireJsConfigErrorMessage(webJar));
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // what-evs
                }
            }

        } else {
            log.warn(requireJsConfigErrorMessage(webJar));
        }

        return "";
    }

    /**
     * The legacy webJars-requirejs.js based RequireJS config for a WebJar.
     *
     * @param webJar A tuple (artifactId -&gt; version) representing the WebJar.
     * @return The contents of the webJars-requirejs.js file.
     */
    @Deprecated
    public static String getWebJarConfig(Map.Entry<String, String> webJar) {
        String webJarConfig = "";

        // read the webJarConfigs
        String filename = WebJarAssetLocator.WEBJARS_PATH_PREFIX + "/" + webJar.getKey() + "/" + webJar.getValue() + "/" + "webjars-requirejs.js";
        InputStream inputStream = RequireJS.class.getClassLoader().getResourceAsStream(filename);
        if (inputStream != null) {
            log.warn("The " + webJar.getKey() + " " + webJar.getValue() + " WebJar is using the legacy RequireJS config.\n" +
                    "Please try a new version of the WebJar or file or file an issue at:\n" +
                    "http://github.com/webjars/" + webJar.getKey() + "/issues/new");

            StringBuilder webJarConfigBuilder = new StringBuilder("// WebJar config for " + webJar.getKey() + "\n");
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            try {
                String line;

                while ((line = br.readLine()) != null) {
                    webJarConfigBuilder.append(line).append("\n");
                }

                webJarConfig = webJarConfigBuilder.toString();
            } catch (IOException e) {
                log.warn(filename + " could not be read.");
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    // really?
                }
            }
        }

        return webJarConfig;
    }

}
