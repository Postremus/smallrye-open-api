package io.smallrye.openapi.mavenplugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.microprofile.openapi.OASConfig;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.Result;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.constants.OpenApiConstants;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.io.OpenApiSerializer;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;

@Mojo(name = "generate-schema", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateSchemaMojo extends AbstractMojo {

    @Parameter(property = "schemas", required = true)
    List<SchemaConfig> schemas;

    @Parameter(defaultValue = "${project}", required = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "false", property = "skip")
    private boolean skip;

    @Parameter(property = "project.compileClasspathElements", required = true, readonly = true)
    private List<String> classpath;

    /**
     * Directory where to output the schemas.
     * If no path is specified, the schema will be printed to the log.
     */
    @Parameter(defaultValue = "${project.build.directory}/generated/", property = "outputDirectory")
    private File outputDirectory;

    /**
     * Compiled classes of the project.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", property = "classesDir")
    private File classesDir;

    private IndexView cached = null;

    @Override
    public void execute() throws MojoExecutionException {
        if (!skip) {
            for (SchemaConfig schemaConfig : schemas) {
                try {

                    if (cached == null) {
                        cached = createIndex(schemaConfig);
                    }
                    OpenApiDocument schema = generateSchema(cached, schemaConfig);
                    write(schema, schemaConfig);
                } catch (IOException ex) {
                    getLog().error(ex);
                    throw new MojoExecutionException("Could not generate OpenAPI Schema", ex); // TODO allow failOnError = false ?
                }
            }
        }
    }

    private IndexView createIndex(SchemaConfig schemaConfig) throws MojoExecutionException {
        IndexView moduleIndex;
        try {
            moduleIndex = indexModuleClasses();
        } catch (IOException e) {
            throw new MojoExecutionException("Can't compute index", e);
        }
        if (!scanDependenciesDisable(schemaConfig)) {
            List<IndexView> indexes = new ArrayList<>();
            indexes.add(moduleIndex);
            for (Object a : mavenProject.getArtifacts()) {
                Artifact artifact = (Artifact) a;
                if (schemaConfig.getIncludeDependenciesScopes().contains(artifact.getScope())
                        && schemaConfig.getIncludeDependenciesTypes().contains(artifact.getType())) {
                    try {
                        Result result = JarIndexer.createJarIndex(artifact.getFile(), new Indexer(),
                                false, false, false);
                        indexes.add(result.getIndex());
                    } catch (Exception e) {
                        getLog().error("Can't compute index of " + artifact.getFile().getAbsolutePath() + ", skipping", e);
                    }
                }
            }
            return CompositeIndex.create(indexes);
        } else {
            return moduleIndex;
        }
    }

    private boolean scanDependenciesDisable(SchemaConfig schemaConfig) {
        if (schemaConfig.getScanDependenciesDisable() == null) {
            return false;
        }
        return schemaConfig.getScanDependenciesDisable();
    }

    // index the classes of this Maven module
    private Index indexModuleClasses() throws IOException {
        Indexer indexer = new Indexer();

        try (Stream<Path> stream = Files.walk(classesDir.toPath())) {

            List<Path> classFiles = stream
                    .filter(path -> path.toString().endsWith(".class"))
                    .collect(Collectors.toList());
            for (Path path : classFiles) {
                indexer.index(Files.newInputStream(path));
            }
        }
        return indexer.complete();
    }

    private OpenApiDocument generateSchema(IndexView index, SchemaConfig schemaConfig) throws IOException {
        OpenApiConfig openApiConfig = new MavenConfig(getProperties(schemaConfig));

        OpenAPI staticModel = generateStaticModel();
        OpenAPI annotationModel = generateAnnotationModel(index, openApiConfig);

        ClassLoader classLoader = getClassLoader(schemaConfig);

        OpenAPI readerModel = OpenApiProcessor.modelFromReader(openApiConfig, classLoader);

        OpenApiDocument document = OpenApiDocument.INSTANCE;

        document.reset();
        document.config(openApiConfig);

        if (annotationModel != null) {
            document.modelFromAnnotations(annotationModel);
        }
        if (readerModel != null) {
            document.modelFromReader(readerModel);
        }
        if (staticModel != null) {
            document.modelFromStaticFile(staticModel);
        }
        document.filter(OpenApiProcessor.getFilter(openApiConfig, classLoader));
        document.initialize();

        return document;
    }

    private ClassLoader getClassLoader(SchemaConfig schemaConfig) throws MalformedURLException {
        Set<URL> urls = new HashSet<>();

        for (String element : classpath) {
            urls.add(new File(element).toURI().toURL());
        }

        return URLClassLoader.newInstance(
                urls.toArray(new URL[0]),
                Thread.currentThread().getContextClassLoader());

    }

    private OpenAPI generateAnnotationModel(IndexView indexView, OpenApiConfig openApiConfig) {
        OpenApiAnnotationScanner openApiAnnotationScanner = new OpenApiAnnotationScanner(openApiConfig, indexView);
        return openApiAnnotationScanner.scan();
    }

    private OpenAPI generateStaticModel() throws IOException {
        Path staticFile = getStaticFile();
        if (staticFile != null) {
            try (InputStream is = Files.newInputStream(staticFile);
                    OpenApiStaticFile openApiStaticFile = new OpenApiStaticFile(is, getFormat(staticFile))) {
                return OpenApiProcessor.modelFromStaticFile(openApiStaticFile);
            }
        }
        return null;
    }

    private Path getStaticFile() {
        Path classesPath = classesDir.toPath();

        if (Files.exists(classesPath)) {
            Path resourcePath = Paths.get(classesPath.toString(), META_INF_OPENAPI_YAML);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
            resourcePath = Paths.get(classesPath.toString(), WEB_INF_CLASSES_META_INF_OPENAPI_YAML);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
            resourcePath = Paths.get(classesPath.toString(), META_INF_OPENAPI_YML);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
            resourcePath = Paths.get(classesPath.toString(), WEB_INF_CLASSES_META_INF_OPENAPI_YML);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
            resourcePath = Paths.get(classesPath.toString(), META_INF_OPENAPI_JSON);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
            resourcePath = Paths.get(classesPath.toString(), WEB_INF_CLASSES_META_INF_OPENAPI_JSON);
            if (Files.exists(resourcePath)) {
                return resourcePath;
            }
        }
        return null;
    }

    private Format getFormat(Path path) {
        if (path.endsWith(".json")) {
            return Format.JSON;
        }
        return Format.YAML;
    }

    private Map<String, String> getProperties(SchemaConfig schemaConfig) throws IOException {
        // First check if the configProperties is set, if so, load that.
        Map<String, String> cp = new HashMap<>();
        if (schemaConfig.getConfigProperties() != null && schemaConfig.getConfigProperties().exists()) {
            Properties p = new Properties();
            try (InputStream is = Files.newInputStream(schemaConfig.getConfigProperties().toPath())) {
                p.load(is);
                cp.putAll((Map) p);
            }
        }

        // Now add properties set in the maven plugin.

        addToPropertyMap(cp, OASConfig.MODEL_READER, schemaConfig.getModelReader());
        addToPropertyMap(cp, OASConfig.FILTER, schemaConfig.getFilter());
        addToPropertyMap(cp, OASConfig.SCAN_DISABLE, schemaConfig.getScanDisabled());
        addToPropertyMap(cp, OASConfig.SCAN_PACKAGES, schemaConfig.getScanPackages());
        addToPropertyMap(cp, OASConfig.SCAN_CLASSES, schemaConfig.getScanClasses());
        addToPropertyMap(cp, OASConfig.SCAN_EXCLUDE_PACKAGES, schemaConfig.getScanExcludePackages());
        addToPropertyMap(cp, OASConfig.SCAN_EXCLUDE_CLASSES, schemaConfig.getScanExcludeClasses());
        addToPropertyMap(cp, OASConfig.SERVERS, schemaConfig.getServers());
        addToPropertyMap(cp, OASConfig.SERVERS_PATH_PREFIX, schemaConfig.getPathServers());
        addToPropertyMap(cp, OASConfig.SERVERS_OPERATION_PREFIX, schemaConfig.getOperationServers());
        addToPropertyMap(cp, OpenApiConstants.SMALLRYE_SCAN_DEPENDENCIES_DISABLE, schemaConfig.getScanDependenciesDisable());
        addToPropertyMap(cp, OpenApiConstants.SMALLRYE_SCAN_DEPENDENCIES_JARS, schemaConfig.getScanDependenciesJars());
        addToPropertyMap(cp, OpenApiConstants.SMALLRYE_CUSTOM_SCHEMA_REGISTRY_CLASS,
                schemaConfig.getCustomSchemaRegistryClass());
        addToPropertyMap(cp, OpenApiConstants.SMALLRYE_APP_PATH_DISABLE, schemaConfig.getApplicationPathDisable());
        addToPropertyMap(cp, OpenApiConstants.VERSION, schemaConfig.getOpenApiVersion());
        addToPropertyMap(cp, OpenApiConstants.INFO_TITLE, schemaConfig.getInfoTitle());
        addToPropertyMap(cp, OpenApiConstants.INFO_VERSION, schemaConfig.getInfoVersion());
        addToPropertyMap(cp, OpenApiConstants.INFO_DESCRIPTION, schemaConfig.getInfoDescription());
        addToPropertyMap(cp, OpenApiConstants.INFO_TERMS, schemaConfig.getInfoTermsOfService());
        addToPropertyMap(cp, OpenApiConstants.INFO_CONTACT_EMAIL, schemaConfig.getInfoContactEmail());
        addToPropertyMap(cp, OpenApiConstants.INFO_CONTACT_NAME, schemaConfig.getInfoContactName());
        addToPropertyMap(cp, OpenApiConstants.INFO_CONTACT_URL, schemaConfig.getInfoContactUrl());
        addToPropertyMap(cp, OpenApiConstants.INFO_LICENSE_NAME, schemaConfig.getInfoLicenseName());
        addToPropertyMap(cp, OpenApiConstants.INFO_LICENSE_URL, schemaConfig.getInfoLicenseUrl());
        addToPropertyMap(cp, OpenApiConstants.OPERATION_ID_STRAGEGY, schemaConfig.getOperationIdStrategy());

        return cp;
    }

    private void addToPropertyMap(Map<String, String> map, String key, Boolean value) {
        if (value != null) {
            map.put(key, value.toString());
        }
    }

    private void addToPropertyMap(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private void addToPropertyMap(Map<String, String> map, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            map.put(key, values.stream().collect(Collectors.joining(",")));
        }
    }

    private void write(OpenApiDocument schema, SchemaConfig schemaConfig) throws MojoExecutionException {
        try {
            String yaml = OpenApiSerializer.serialize(schema.get(), Format.YAML);
            String json = OpenApiSerializer.serialize(schema.get(), Format.JSON);
            if (outputDirectory == null) {
                // no destination file specified => print to stdout
                getLog().info(yaml);
            } else {
                Path directory = outputDirectory.toPath();
                if (!Files.exists(directory)) {
                    Files.createDirectories(directory);
                }

                writeSchemaFile(directory, schemaConfig.getSchemaFilename() + ".yaml", yaml.getBytes());
                writeSchemaFile(directory, schemaConfig.getSchemaFilename() + ".json", json.getBytes());

                getLog().info("Wrote the schema files to " + outputDirectory.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Can't write the result", e);
        }
    }

    private void writeSchemaFile(Path directory, String filename, byte[] contents) throws IOException {
        Path file = Paths.get(directory.toString(), filename);
        if (!Files.exists(file)) {
            Files.createFile(file);
        }

        Files.write(file, contents,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static final String META_INF_OPENAPI_YAML = "META-INF/openapi.yaml";
    private static final String WEB_INF_CLASSES_META_INF_OPENAPI_YAML = "WEB-INF/classes/META-INF/openapi.yaml";
    private static final String META_INF_OPENAPI_YML = "META-INF/openapi.yml";
    private static final String WEB_INF_CLASSES_META_INF_OPENAPI_YML = "WEB-INF/classes/META-INF/openapi.yml";
    private static final String META_INF_OPENAPI_JSON = "META-INF/openapi.json";
    private static final String WEB_INF_CLASSES_META_INF_OPENAPI_JSON = "WEB-INF/classes/META-INF/openapi.json";
}
