package org.webjars;

import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES;
import static com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class RequireJS {

    public static final String WEBJARS_MAVEN_PREFIX = "META-INF/maven/org.webjars";

    private static final Logger log = LoggerFactory.getLogger(RequireJS.class);
    private static final Pattern DOT = Pattern.compile("\\.");

    private static String requireConfigJavaScript;
    private static String requireConfigJavaScriptCdn;

    private static Map<String, ObjectNode> requireConfigJson;
    private static Map<String, ObjectNode> requireConfigJsonCdn;

    private RequireJS() {
        // utility class
    }

    /**
     * Returns the JavaScript that is used to setup the RequireJS config. This value is cached in memory so that all of the processing to get the String only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag
     */
    @Nonnull
    public static synchronized String getSetupJavaScript(@Nullable String urlPrefix) {
        if (requireConfigJavaScript == null) {
            requireConfigJavaScript = generateSetupJavaScript(Collections.singletonList(urlPrefix));
        }
        return requireConfigJavaScript;
    }

    /**
     * Returns the JavaScript that is used to setup the RequireJS config. This value is cached in memory so that all of the processing to get the String only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @param cdnPrefix The optional CDN prefix where the WebJars can be downloaded from
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag
     */
    @Nonnull
    public static synchronized String getSetupJavaScript(@Nullable String cdnPrefix, @Nullable String urlPrefix) {
        if (requireConfigJavaScriptCdn == null) {
            Collection<String> prefixes = new ArrayList<>(2);
            prefixes.add(cdnPrefix);
            prefixes.add(urlPrefix);
            requireConfigJavaScriptCdn = generateSetupJavaScript(prefixes);
        }
        return requireConfigJavaScriptCdn;
    }

    /**
     * Returns the JavaScript that is used to setup the RequireJS config. This value is not cached.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag.
     */
    @Nonnull
    public static String generateSetupJavaScript(@Nonnull Collection<String> prefixes) {
        Map<String, String> webJars = new WebJarAssetLocator().getWebJars();
        return generateSetupJavaScript(prefixes, webJars);
    }

    /**
     * Generate the JavaScript that is used to setup the RequireJS config. This value is not cached.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @param webJars  The WebJars (artifactId -&gt; version) to use
     * @return The JavaScript block that can be embedded or loaded in a &lt;script&gt; tag.
     */
    @Nonnull
    public static String generateSetupJavaScript(@Nonnull Collection<String> prefixes, @Nonnull Map<String, String> webJars) {

        List<Entry<String, Boolean>> prefixesWithVersion =
            prefixes.stream()
                .map(prefix -> new SimpleEntry<>(prefix, true))
                .collect(Collectors.toList());

        Collection<WebJarVersion> versions = new ArrayList<>(webJars.size());
        StringBuilder webJarConfigsString = new StringBuilder();
        Collection<String> requireJsConfigs = new ArrayList<>(webJars.size());

        if (webJars.isEmpty()) {
            log.warn("Can't find any WebJars in the classpath, RequireJS configuration will be empty.");
        } else {
            for (Entry<String, String> webJar : webJars.entrySet()) {
                versions.add(new WebJarVersion(webJar.getKey(), webJar.getValue()));

                // assemble the WebJar config string

                // default to the new pom.xml meta-data way
                ObjectNode webJarObjectNode = getWebJarSetupJson(webJar, prefixesWithVersion);
                if ((webJarObjectNode != null ? webJarObjectNode.size() : 0) == 0) {
                    String legacyWebJarConfig = getWebJarConfig(webJar);
                    if (legacyWebJarConfig != null) {
                        webJarConfigsString.append('\n').append(legacyWebJarConfig);
                    }
                } else {
                    requireJsConfigs.add(webJarObjectNode.toString());
                }
            }
        }

        Collection<WebJarPath> webJarPaths = new ArrayList<>(prefixes.size());
        for (Iterator<String> iterator = prefixes.iterator(); iterator.hasNext(); ) {
            String prefix = iterator.next();
            webJarPaths.add(new WebJarPath(prefix, iterator.hasNext()));
        }

        Map<String, Object> context = new HashMap<>(5);
        context.put("versions", versions);
        context.put("webJarPaths", webJarPaths);
        context.put("requireJsConfigs", requireJsConfigs);
        context.put("webJarConfigsString", webJarConfigsString);
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile("setup-template.mustache");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context);
        return writer.toString();
    }

    /**
     * Returns the JSON that is used to setup the RequireJS config. This value is cached in memory so that all of the processing to get the JSON only has to happen once.
     *
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JSON structured config
     */
    @Nonnull
    public static synchronized Map<String, ObjectNode> getSetupJson(@Nullable String urlPrefix) {
        if (requireConfigJson == null) {
            requireConfigJson = generateSetupJson(Collections.singletonList(new SimpleEntry<>(urlPrefix, true)));
        }
        return requireConfigJson;
    }

    /**
     * Returns the JSON that is used to setup the RequireJS config. This value is cached in memory so that all of the processing to get the JSON only has to happen once.
     *
     * @param cdnPrefix The CDN prefix where the WebJars can be downloaded from
     * @param urlPrefix The URL prefix where the WebJars can be downloaded from with a trailing slash, e.g. /webJars/
     * @return The JSON structured config
     */
    @Nonnull
    public static synchronized Map<String, ObjectNode> getSetupJson(@Nullable String cdnPrefix, @Nullable String urlPrefix) {
        if (requireConfigJsonCdn == null) {
            List<Entry<String, Boolean>> prefixes = new ArrayList<>(2);
            prefixes.add(new SimpleEntry<>(cdnPrefix, true));
            prefixes.add(new SimpleEntry<>(urlPrefix, true));
            requireConfigJsonCdn = generateSetupJson(prefixes);
        }
        return requireConfigJsonCdn;
    }

    /**
     * Returns the JSON used to setup the RequireJS config for each WebJar in the CLASSPATH. This value is not cached.
     *
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config with a boolean flag indicating whether or not to include the version.
     * @return The JSON structured config for each WebJar.
     */
    @Nonnull
    public static Map<String, ObjectNode> generateSetupJson(@Nonnull List<Entry<String, Boolean>> prefixes) {
        Map<String, String> webJars = new WebJarAssetLocator().getWebJars();
        Map<String, ObjectNode> jsonConfigs = new HashMap<>(webJars.size());
        for (Entry<String, String> webJar : webJars.entrySet()) {
            jsonConfigs.put(webJar.getKey(), getWebJarSetupJson(webJar, prefixes));
        }
        return jsonConfigs;
    }

    @Nullable
    private static ObjectNode getWebJarSetupJson(@Nonnull Entry<String, String> webJar, @Nonnull List<Entry<String, Boolean>> prefixes) {

        if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars.npm/" + webJar.getKey() + "/pom.xml") != null) {
            // create the requirejs config from the package.json
            return getNpmWebJarRequireJsConfig(webJar, prefixes);
        }
        if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars.bower/" + webJar.getKey() + "/pom.xml") != null) {
            // create the requirejs config from the bower.json
            return getBowerWebJarRequireJsConfig(webJar, prefixes);
        }
        if (RequireJS.class.getClassLoader().getResource("META-INF/maven/org.webjars/" + webJar.getKey() + "/pom.xml") != null) {
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
    public static ObjectNode getWebJarRequireJsConfig(Entry<String, String> webJar, List<Entry<String, Boolean>> prefixes) {
        String rawRequireJsConfig = getRawWebJarRequireJsConfig(webJar);

        ObjectMapper mapper = new ObjectMapper()
            .configure(ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(ALLOW_SINGLE_QUOTES, true);

        // default to just an empty object
        ObjectNode webJarRequireJsNode = mapper.createObjectNode();

        try {
            JsonNode maybeRequireJsConfig = mapper.readTree(rawRequireJsConfig);
            if (maybeRequireJsConfig != null && maybeRequireJsConfig.isObject()) {
                // The provided config was parseable, now lets fix the paths

                webJarRequireJsNode = (ObjectNode) maybeRequireJsConfig;

                if (webJarRequireJsNode.isObject()) {

                    // update the paths

                    ObjectNode pathsNode = (ObjectNode) webJarRequireJsNode.get("paths");

                    ObjectNode newPaths = mapper.createObjectNode();

                    if (pathsNode != null) {
                        Iterator<Entry<String, JsonNode>> paths = pathsNode.fields();
                        while (paths.hasNext()) {
                            Entry<String, JsonNode> pathNode = paths.next();

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
                                for (Entry<String, Boolean> prefix : prefixes) {
                                    StringBuilder newPath = new StringBuilder(prefix.getKey()).append(webJar.getKey());
                                    if (prefix.getValue()) {
                                        newPath.append('/').append(webJar.getValue());
                                    }
                                    newPathsNode.add(newPath.append('/').append(originalPath).toString());
                                }
                                newPathsNode.add(originalPath);
                            } else {
                                log.error("Strange... The path could not be parsed.  Here is what was provided: {}", pathNode.getValue().toString());
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
                            if (!prefixes.isEmpty()) {
                                // this picks the last prefix assuming that it is the right one
                                // not sure of a better way to do this since I don't think we want the CDN prefix
                                // maybe this can be an array like paths?
                                Entry<String, Boolean> prefix = prefixes.get(prefixes.size() - 1);
                                StringBuilder newLocation = new StringBuilder(prefix.getKey()).append(webJar.getKey());
                                if (prefix.getValue()) {
                                    newLocation.append('/').append(webJar.getValue());
                                }
                                ((ObjectNode) packageJson).put("location", newLocation.append('/').append(originalLocation).toString());
                            }

                            newPackages.add(packageJson);
                        }
                    }

                    webJarRequireJsNode.replace("packages", newPackages);
                }

            } else {
                if (rawRequireJsConfig.isEmpty()) {
                    log.warn(requireJsConfigErrorMessage(webJar));
                } else {
                    log.error(requireJsConfigErrorMessage(webJar));
                }
            }
        } catch (IOException e) {
            log.warn(requireJsConfigErrorMessage(webJar));
            if (!rawRequireJsConfig.isEmpty()) {
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
    public static ObjectNode getBowerWebJarRequireJsConfig(Entry<String, String> webJar, List<Entry<String, Boolean>> prefixes) {

        String bowerJsonPath = String.format("%s/%s/%s/bower.json", WebJarAssetLocator.WEBJARS_PATH_PREFIX, webJar.getKey(), webJar.getValue());

        return getWebJarRequireJsConfigFromMainConfig(webJar, prefixes, bowerJsonPath);
    }

    /**
     * Returns the JSON RequireJS config for a given Bower WebJar
     *
     * @param webJar   A tuple (artifactId -&gt; version) representing the WebJar.
     * @param prefixes A list of the prefixes to use in the `paths` part of the RequireJS config.
     * @return The JSON RequireJS config for the WebJar based on the meta-data in the WebJar's pom.xml file.
     */
    public static ObjectNode getNpmWebJarRequireJsConfig(Entry<String, String> webJar, List<Entry<String, Boolean>> prefixes) {

        String packageJsonPath = String.format("%s/%s/%s/package.json", WebJarAssetLocator.WEBJARS_PATH_PREFIX, webJar.getKey(), webJar.getValue());

        return getWebJarRequireJsConfigFromMainConfig(webJar, prefixes, packageJsonPath);
    }

    @Nullable
    private static ObjectNode getWebJarRequireJsConfigFromMainConfig(Entry<String, String> webJar, List<Entry<String, Boolean>> prefixes, String path) {
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
                String requireFriendlyName = DOT.matcher(name).replaceAll("-");

                JsonNode mainJs = jsonNode.get("main");
                if (mainJs != null) {
                    if (mainJs.getNodeType() == JsonNodeType.STRING) {
                        String main = mainJs.asText();
                        requireConfigPaths.set(requireFriendlyName, mainJsToPathJson(webJar, main, prefixes));
                    } else if (mainJs.getNodeType() == JsonNodeType.ARRAY) {
                        ArrayList<String> mainList = new ArrayList<>(mainJs.size());
                        for (JsonNode mainJsonNode : mainJs) {
                            mainList.add(mainJsonNode.asText());
                        }
                        String main = getBowerBestMatchFromMainArray(mainList, name);
                        requireConfigPaths.set(requireFriendlyName, mainJsToPathJson(webJar, main, prefixes));
                    }
                } else {
                    if (hasIndexFile(String.format("%s/%s/%s/index.js", WebJarAssetLocator.WEBJARS_PATH_PREFIX, webJar.getKey(), webJar.getValue()))) {
                        requireConfigPaths.set(requireFriendlyName, mainJsToPathJson(webJar, "index.js", prefixes));
                    } else {
                        throw new IllegalArgumentException("no 'main' nor 'index.js' file; cannot generate a config");
                    }
                }

                // todo add dependency shims

                return requireConfig;

            } catch (IOException e) {
                log.warn(
                    "Could not create the RequireJS config for the {} {} WebJar from {}\nError: {}\nPlease file a bug at: http://github.com/webjars/webjars-locator/issues/new",
                    webJar.getKey(), webJar.getValue(), path, e.getMessage());
            } catch (IllegalArgumentException e) {
                log.warn(
                    "Could not create the RequireJS config for the {} {} WebJar from {}\nThere was not enough information in the package metadata to do so.\nError: {}\nIf you think you have received this message in error, please file a bug at: http://github.com/webjars/webjars-locator/issues/new",
                    webJar.getKey(), webJar.getValue(), path, e.getMessage());
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

    private static boolean hasIndexFile(String path) {
        return RequireJS.class.getClassLoader().getResource(path) != null;
    }

    /*
     * Heuristic approach to find the 'best' candidate which most likely is the main script of a package.
     */

    private static String getBowerBestMatchFromMainArray(ArrayList<String> items, String name) {
        if (items.size() == 1) // not really much choice here
        {
            return items.get(0);
        }

        List<String> filteredList = new ArrayList<>(items.size());

        // first idea: only look at .js files

        for (String item : items) {
            if (item.toLowerCase(Locale.ENGLISH).endsWith(".js")) {
                filteredList.add(item);
            }
        }

        // ... if there are any
        if (filteredList.isEmpty()) {
            filteredList = items;
        }

        // second idea: most scripts are named after the project's name
        // sort all script files by their Levenshtein-distance
        // and return the one which is most similar to the project's name
        filteredList.sort(new LevenshteinDistanceComparator(name.toLowerCase(Locale.ENGLISH)));
        return filteredList.get(0);
    }

    private static JsonNode mainJsToPathJson(Entry<String, String> webJar, String main, Iterable<Entry<String, Boolean>> prefixes) {
        String requireJsStyleMain = main;
        if (main.endsWith(".js")) {
            requireJsStyleMain = main.substring(0, main.lastIndexOf(".js"));
        }

        if (requireJsStyleMain.startsWith("./")) {
            requireJsStyleMain = requireJsStyleMain.substring(2);
        }

        String unprefixedMain = String.format("%s/%s/%s", webJar.getKey(), webJar.getValue(), requireJsStyleMain);

        ArrayNode arrayNode = new ArrayNode(JsonNodeFactory.instance);

        for (Entry<String, Boolean> prefix : prefixes) {
            arrayNode.add(String.format("%s%s", prefix.getKey(), unprefixedMain));
        }

        return arrayNode;
    }

    /**
     * A generic error message for when the RequireJS config could not be parsed out of the WebJar's pom.xml meta-data.
     *
     * @param webJar A tuple (artifactId -> version) representing the WebJar.
     * @return The error message.
     */
    private static String requireJsConfigErrorMessage(Entry<String, String> webJar) {
        return String.format("Could not read WebJar RequireJS config for: %s %s\nPlease file a bug at: http://github.com/webjars/%s/issues/new", webJar.getKey(), webJar.getValue(),
            webJar.getKey());
    }

    /**
     * @param webJar A tuple (artifactId -&gt; version) representing the WebJar.
     * @return The raw RequireJS config string from the WebJar's pom.xml meta-data.
     */
    @Nonnull
    public static String getRawWebJarRequireJsConfig(@Nonnull Entry<String, String> webJar) {
        String filename = String.format("%s/%s/pom.xml", WEBJARS_MAVEN_PREFIX, webJar.getKey());
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
                        if ("requirejs".equals(node.getNodeName())) {
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
    @Nullable
    public static String getWebJarConfig(@Nonnull Entry<String, String> webJar) {
        String filename = String.format("%s/%s/%s/webjars-requirejs.js", WebJarAssetLocator.WEBJARS_PATH_PREFIX, webJar.getKey(), webJar.getValue());
        InputStream inputStream = RequireJS.class.getClassLoader().getResourceAsStream(filename);
        if (inputStream != null) {
            log.warn(
                "The {} {} WebJar is using the legacy RequireJS config.\nPlease try a new version of the WebJar or file or file an issue at:\nhttp://github.com/webjars/{}/issues/new",
                webJar.getKey(), webJar.getValue(), webJar.getKey());
            String fileContent = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
            if (StringUtils.isBlank(fileContent)) {
                return null;
            }
            return new StringBuilder()
                .append("// WebJar config for ")
                .append(webJar.getKey())
                .append('\n')
                .append(fileContent.trim())
                .toString();
        }
        return null;
    }

}
